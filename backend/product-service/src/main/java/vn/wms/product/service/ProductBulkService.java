package vn.wms.product.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import vn.wms.common.bulk.BulkResult;
import vn.wms.common.bulk.BulkRowResult;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;
import vn.wms.product.dto.BulkProductItem;
import vn.wms.product.dto.CreateProductRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Nhập hàng hóa hàng loạt {@code POST /api/products/bulk} (WMS-22.4b, docs/SAAS_SPEC.md).
 *
 * <p>Thứ tự xử lý mỗi dòng:
 * <ol>
 *   <li>SKU đã xuất hiện ở một dòng trước trong cùng file: {@code FAILED} (dòng sau luôn lỗi, kể cả khi dòng trước
 *       lỗi hay bị bỏ qua).</li>
 *   <li>Kiểm tra dữ liệu bằng đúng ràng buộc của {@link CreateProductRequest} cộng ràng buộc của
 *       {@code uomName}, {@code categoryName}: sai thì {@code FAILED} kèm các thông báo lỗi.</li>
 *   <li>Trong transaction riêng ({@link ProductBulkRowImporter}): SKU đã có trong doanh nghiệp thì {@code SKIPPED},
 *       không ghi đè; còn lại tự tạo danh mục, đơn vị tính chưa có rồi tạo sản phẩm qua
 *       {@link ProductService#create}, nên trùng mã vạch và vượt giới hạn SKU của gói cũng thành {@code FAILED}
 *       kèm lý do.</li>
 * </ol>
 *
 * <p>Lớp này cố ý không đặt {@code @Transactional}: mỗi dòng là một transaction độc lập, dòng lỗi không làm mất các
 * dòng đã lưu.
 */
@Service
public class ProductBulkService {

    private static final Logger log = LoggerFactory.getLogger(ProductBulkService.class);

    /** Giá trị giữ chỗ cho uomId khi kiểm tra dữ liệu: ID thật chỉ có sau khi tìm hoặc tạo đơn vị tính. */
    private static final Long UOM_ID_PLACEHOLDER = 0L;

    /** Thứ tự trường để thông báo lỗi của một dòng luôn ra cùng một thứ tự. */
    private static final List<String> FIELD_ORDER = List.of("sku", "name", "barcode", "description",
            "categoryName", "uomName", "purchasePrice", "sellingPrice", "vatRate", "safetyStock", "reorderPoint",
            "maxStock");

    private final ProductBulkRowImporter rowImporter;
    private final Validator validator;

    public ProductBulkService(ProductBulkRowImporter rowImporter, Validator validator) {
        this.rowImporter = rowImporter;
        this.validator = validator;
    }

    /**
     * Nhập danh sách dòng (đã kiểm tra có từ 1 đến 500 dòng) cho doanh nghiệp hiện tại.
     *
     * @throws ApiException {@code COMPANY_REQUIRED} khi chưa xác định được doanh nghiệp
     */
    public BulkResult importProducts(List<BulkProductItem> items) {
        TenantContext.requireCompanyId();
        Map<String, Integer> firstRowBySku = new HashMap<>();
        List<BulkRowResult> results = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            results.add(importRow(index + 1, items.get(index), firstRowBySku));
        }
        BulkResult result = BulkResult.of(results);
        log.info("Nhập hàng hóa hàng loạt cho doanh nghiệp {}: {} dòng, tạo {}, bỏ qua {}, lỗi {}",
                TenantContext.getCompanyId(), result.total(), result.created(), result.skipped(), result.failed());
        return result;
    }

    private BulkRowResult importRow(int row, BulkProductItem item, Map<String, Integer> firstRowBySku) {
        if (item == null) {
            return BulkRowResult.failed(row, null, "Dòng trống, không có dữ liệu");
        }
        String sku = normalizeSku(item.sku());
        if (sku != null) {
            Integer firstRow = firstRowBySku.putIfAbsent(sku, row);
            if (firstRow != null) {
                return BulkRowResult.failed(row, sku,
                        "Mã SKU '" + sku + "' bị lặp lại trong file (đã có ở dòng " + firstRow + ")");
            }
        }

        String violations = validate(item);
        if (violations != null) {
            return BulkRowResult.failed(row, sku, violations);
        }

        try {
            return rowImporter.importRow(row, item, sku);
        } catch (ApiException ex) {
            return BulkRowResult.failed(row, sku, ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            // Hai lần nhập chạy cùng lúc tạo trùng SKU, mã vạch, mã danh mục hoặc đơn vị tính
            log.warn("Dòng {} vi phạm ràng buộc duy nhất khi nhập hàng loạt: {}", row, ex.getMostSpecificCause().getMessage());
            return BulkRowResult.failed(row, sku,
                    "Dữ liệu trùng với bản ghi vừa được tạo cùng lúc, vui lòng nhập lại dòng này");
        } catch (RuntimeException ex) {
            log.error("Lỗi không lường trước khi nhập dòng {} (SKU {})", row, sku, ex);
            return BulkRowResult.failed(row, sku, "Lỗi hệ thống khi lưu dòng này, vui lòng thử lại");
        }
    }

    /** Thông báo lỗi dữ liệu của dòng nối bằng "; ", {@code null} nếu dòng hợp lệ. */
    private String validate(BulkProductItem item) {
        CreateProductRequest asCreateRequest = item.toCreateRequest(null, UOM_ID_PLACEHOLDER);
        Set<ConstraintViolation<CreateProductRequest>> requestViolations = validator.validate(asCreateRequest);
        Set<ConstraintViolation<BulkProductItem>> itemViolations = validator.validate(item);
        if (requestViolations.isEmpty() && itemViolations.isEmpty()) {
            return null;
        }
        return Stream.concat(requestViolations.stream(), itemViolations.stream())
                .map(violation -> new FieldMessage(violation.getPropertyPath().toString(), violation.getMessage()))
                .distinct()
                .sorted(Comparator.comparingInt((FieldMessage fm) -> fieldRank(fm.field()))
                        .thenComparing(FieldMessage::message))
                .map(FieldMessage::message)
                .collect(Collectors.joining("; "));
    }

    private static int fieldRank(String field) {
        int rank = FIELD_ORDER.indexOf(field);
        return rank >= 0 ? rank : FIELD_ORDER.size();
    }

    /** Chuẩn hóa SKU giống {@link ProductService#create}: cắt khoảng trắng, viết hoa; rỗng thành {@code null}. */
    private static String normalizeSku(String sku) {
        if (sku == null || sku.isBlank()) {
            return null;
        }
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private record FieldMessage(String field, String message) {
    }
}
