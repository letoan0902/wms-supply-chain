package vn.wms.product.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.common.plan.PlanLimit;
import vn.wms.common.plan.PlanLimitChecker;
import vn.wms.product.dto.CreateProductRequest;
import vn.wms.product.dto.ProductResponse;
import vn.wms.product.dto.UpdateProductRequest;
import vn.wms.product.entity.Category;
import vn.wms.product.entity.Product;
import vn.wms.product.entity.Uom;
import vn.wms.product.repository.CategoryRepository;
import vn.wms.product.repository.ProductRepository;
import vn.wms.product.repository.UomRepository;

import java.math.BigDecimal;

/**
 * Nghiệp vụ danh mục SKU / sản phẩm.
 *
 * <p>Dữ liệu tách theo doanh nghiệp (WMS-22.4b): {@link Product}, {@link Category}, {@link Uom} kế thừa
 * {@code TenantEntity}, nên mọi truy vấn của repository (kể cả kiểm tra trùng SKU, mã vạch) chỉ xét doanh nghiệp
 * trong {@link vn.wms.common.tenant.TenantContext}; tìm theo ID một bản ghi của doanh nghiệp khác ném
 * {@code NOT_FOUND}.
 *
 * <p>Giới hạn số SKU theo gói (WMS-23.2): tạo SKU mới hoặc bật lại SKU đã ngưng phải qua {@link PlanLimitChecker},
 * đếm số SKU đang hoạt động của chính doanh nghiệp. Checker là tùy chọn (không có Redis thì bỏ qua).
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final UomRepository uomRepository;
    private final ObjectProvider<PlanLimitChecker> planLimitChecker;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          UomRepository uomRepository,
                          ObjectProvider<PlanLimitChecker> planLimitChecker) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.uomRepository = uomRepository;
        this.planLimitChecker = planLimitChecker;
    }

    /** Danh sách sản phẩm có phân trang, tìm kiếm và lọc. */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(String keyword, Long categoryId, Boolean active, Pageable pageable) {
        String trimmedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return PageResponse.from(productRepository.search(trimmedKeyword, categoryId, active, pageable)
                .map(ProductResponse::from));
    }

    /** Danh sách sản phẩm có phân trang (tương thích ngược). */
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(Pageable pageable) {
        return list(null, null, null, pageable);
    }

    /** Chi tiết sản phẩm theo ID. */
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm với id: " + id));
        return ProductResponse.from(product);
    }

    /** Chi tiết sản phẩm theo mã SKU. */
    @Transactional(readOnly = true)
    public ProductResponse getBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm với SKU: " + sku));
        return ProductResponse.from(product);
    }

    /** Tạo mới SKU / sản phẩm. */
    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        String cleanSku = request.sku().trim().toUpperCase();
        if (productRepository.existsBySku(cleanSku)) {
            throw ApiException.conflict("Mã SKU '" + cleanSku + "' đã tồn tại");
        }

        String cleanBarcode = normalizeBarcode(request.barcode());
        if (cleanBarcode != null && productRepository.existsByBarcode(cleanBarcode)) {
            throw ApiException.conflict("Mã vạch '" + cleanBarcode + "' đã tồn tại");
        }

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> ApiException.badRequest("Danh mục không tồn tại với id: " + request.categoryId()));
        }

        Uom uom = uomRepository.findById(request.uomId())
                .orElseThrow(() -> ApiException.badRequest("Đơn vị tính không tồn tại với id: " + request.uomId()));

        Product product = new Product();
        product.setSku(cleanSku);
        product.setBarcode(cleanBarcode);
        product.setName(request.name().trim());
        product.setDescription(request.description() != null ? request.description().trim() : null);
        product.setCategory(category);
        product.setUom(uom);
        product.setPurchasePrice(request.purchasePrice() != null ? request.purchasePrice() : BigDecimal.ZERO);
        product.setSellingPrice(request.sellingPrice() != null ? request.sellingPrice() : BigDecimal.ZERO);
        product.setVatRate(request.vatRate() != null ? request.vatRate() : BigDecimal.ZERO);
        product.setImageUrl(request.imageUrl() != null ? request.imageUrl().trim() : null);
        product.setManageByLot(Boolean.TRUE.equals(request.manageByLot()));
        product.setManageByExpiration(Boolean.TRUE.equals(request.manageByExpiration()));
        product.setSafetyStock(request.safetyStock() != null ? request.safetyStock() : BigDecimal.ZERO);
        product.setReorderPoint(request.reorderPoint() != null ? request.reorderPoint() : BigDecimal.ZERO);
        product.setMaxStock(request.maxStock() != null ? request.maxStock() : BigDecimal.ZERO);
        product.setActive(true);

        assertCanActivateSku();
        Product saved = productRepository.save(product);
        reportSkuUsage();
        return ProductResponse.from(saved);
    }

    /** Cập nhật thông tin SKU / sản phẩm. */
    @Transactional
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm với id: " + id));

        String cleanSku = request.sku().trim().toUpperCase();
        if (!product.getSku().equalsIgnoreCase(cleanSku) && productRepository.existsBySkuAndIdNot(cleanSku, id)) {
            throw ApiException.conflict("Mã SKU '" + cleanSku + "' đã tồn tại");
        }

        String cleanBarcode = normalizeBarcode(request.barcode());
        if (cleanBarcode != null && !cleanBarcode.equals(product.getBarcode()) && productRepository.existsByBarcodeAndIdNot(cleanBarcode, id)) {
            throw ApiException.conflict("Mã vạch '" + cleanBarcode + "' đã tồn tại");
        }

        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> ApiException.badRequest("Danh mục không tồn tại với id: " + request.categoryId()));
        }

        Uom uom = uomRepository.findById(request.uomId())
                .orElseThrow(() -> ApiException.badRequest("Đơn vị tính không tồn tại với id: " + request.uomId()));

        product.setSku(cleanSku);
        product.setBarcode(cleanBarcode);
        product.setName(request.name().trim());
        product.setDescription(request.description() != null ? request.description().trim() : null);
        product.setCategory(category);
        product.setUom(uom);
        product.setPurchasePrice(request.purchasePrice() != null ? request.purchasePrice() : BigDecimal.ZERO);
        product.setSellingPrice(request.sellingPrice() != null ? request.sellingPrice() : BigDecimal.ZERO);
        product.setVatRate(request.vatRate() != null ? request.vatRate() : BigDecimal.ZERO);
        product.setImageUrl(request.imageUrl() != null ? request.imageUrl().trim() : null);
        product.setManageByLot(Boolean.TRUE.equals(request.manageByLot()));
        product.setManageByExpiration(Boolean.TRUE.equals(request.manageByExpiration()));
        product.setSafetyStock(request.safetyStock() != null ? request.safetyStock() : BigDecimal.ZERO);
        product.setReorderPoint(request.reorderPoint() != null ? request.reorderPoint() : BigDecimal.ZERO);
        product.setMaxStock(request.maxStock() != null ? request.maxStock() : BigDecimal.ZERO);

        boolean wasActive = product.isActive();
        if (request.active() != null) {
            if (request.active() && !wasActive) {
                assertCanActivateSku();
            }
            product.setActive(request.active());
        }

        Product saved = productRepository.save(product);
        if (wasActive != saved.isActive()) {
            reportSkuUsage();
        }
        return ProductResponse.from(saved);
    }

    /** Xóa mềm sản phẩm theo ID (chuyển active = false để không mất tham chiếu SKU ở inventory-service). */
    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm với id: " + id));
        boolean wasActive = product.isActive();
        product.setActive(false);
        productRepository.save(product);
        if (wasActive) {
            reportSkuUsage();
        }
    }

    /**
     * Kiểm tra giới hạn số SKU của gói trước khi doanh nghiệp có thêm một SKU đang hoạt động (tạo mới, nhập hàng
     * loạt hoặc bật lại). Số đếm chỉ gồm SKU của doanh nghiệp hiện tại; thiếu doanh nghiệp thì ném
     * {@code COMPANY_REQUIRED}.
     */
    private void assertCanActivateSku() {
        PlanLimitChecker checker = planLimitChecker != null ? planLimitChecker.getIfAvailable() : null;
        if (checker != null) {
            checker.assertCanCreate(PlanLimit.SKUS, productRepository.countByActiveTrue());
        }
    }

    /** Ghi số SKU đang hoạt động của doanh nghiệp hiện tại vào bộ nhớ đệm mức sử dụng. */
    private void reportSkuUsage() {
        PlanLimitChecker checker = planLimitChecker != null ? planLimitChecker.getIfAvailable() : null;
        if (checker != null) {
            checker.reportUsage(PlanLimit.SKUS, productRepository.countByActiveTrue());
        }
    }

    private String normalizeBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return null;
        }
        return barcode.trim();
    }
}
