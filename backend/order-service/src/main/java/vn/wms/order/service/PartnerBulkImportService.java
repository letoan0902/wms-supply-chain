package vn.wms.order.service;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import vn.wms.common.exception.ApiException;
import vn.wms.common.tenant.TenantContext;
import vn.wms.order.dto.CreateCustomerRequest;
import vn.wms.order.dto.CreateSupplierRequest;
import vn.wms.order.dto.bulk.BulkImportResponse;
import vn.wms.order.dto.bulk.BulkRowResult;
import vn.wms.order.entity.Customer;
import vn.wms.order.entity.Supplier;
import vn.wms.order.repository.CustomerRepository;
import vn.wms.order.repository.SupplierRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Nhập hàng loạt nhà cung cấp và khách hàng (WMS-22.4b, phần 2).
 *
 * <p>Quy tắc cho từng dòng, theo đúng thứ tự:
 * <ol>
 *   <li>kiểm tra ràng buộc của {@link CreateSupplierRequest} / {@link CreateCustomerRequest} như API tạo từng bản ghi;</li>
 *   <li>mã đã xuất hiện ở một dòng hợp lệ phía trên trong cùng danh sách: {@code FAILED};</li>
 *   <li>mã đã tồn tại trong doanh nghiệp: {@code SKIPPED}, không ghi đè bản ghi đang có;</li>
 *   <li>còn lại gọi đúng phương thức tạo của {@link SupplierService} / {@link CustomerService} (kiểm tra trùng mã số
 *       thuế, ghi công nợ ban đầu...): thành công {@code CREATED}, lỗi nghiệp vụ {@code FAILED} kèm lý do.</li>
 * </ol>
 *
 * <p>Mỗi dòng chạy trong một transaction riêng ({@code REQUIRES_NEW} qua {@link TransactionTemplate}, không dựa vào
 * {@code @Transactional} của chính lớp này nên không bị ảnh hưởng bởi việc tự gọi phương thức trong cùng bean):
 * dòng lỗi chỉ rollback dòng đó. Phương thức nhập không mở transaction bao ngoài.
 */
@Service
public class PartnerBulkImportService {

    private static final Logger log = LoggerFactory.getLogger(PartnerBulkImportService.class);

    private final SupplierService supplierService;
    private final CustomerService customerService;
    private final SupplierRepository supplierRepository;
    private final CustomerRepository customerRepository;
    private final Validator validator;
    private final TransactionTemplate rowTransaction;

    public PartnerBulkImportService(SupplierService supplierService,
                                    CustomerService customerService,
                                    SupplierRepository supplierRepository,
                                    CustomerRepository customerRepository,
                                    Validator validator,
                                    PlatformTransactionManager transactionManager) {
        this.supplierService = supplierService;
        this.customerService = customerService;
        this.supplierRepository = supplierRepository;
        this.customerRepository = customerRepository;
        this.validator = validator;
        this.rowTransaction = new TransactionTemplate(transactionManager);
        this.rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Nhập danh sách nhà cung cấp của doanh nghiệp hiện tại. */
    public BulkImportResponse importSuppliers(List<CreateSupplierRequest> items) {
        return importRows(items, "nhà cung cấp", CreateSupplierRequest::code,
                code -> supplierRepository.findByCode(code).map(Supplier::getId),
                request -> supplierService.create(request).id());
    }

    /** Nhập danh sách khách hàng của doanh nghiệp hiện tại. */
    public BulkImportResponse importCustomers(List<CreateCustomerRequest> items) {
        return importRows(items, "khách hàng", CreateCustomerRequest::code,
                code -> customerRepository.findByCode(code).map(Customer::getId),
                request -> customerService.create(request).id());
    }

    private <T> BulkImportResponse importRows(List<T> items,
                                              String label,
                                              Function<T, String> codeOf,
                                              Function<String, Optional<Long>> existingIdByCode,
                                              Function<T, Long> create) {
        // Thiếu doanh nghiệp thì từ chối cả yêu cầu, không trả 500 dòng FAILED giống nhau
        TenantContext.requireCompanyId();

        List<BulkRowResult> results = new ArrayList<>(items.size());
        Map<String, Integer> firstRowOfCode = new HashMap<>();
        for (int index = 0; index < items.size(); index++) {
            int row = index + 1;
            T item = items.get(index);
            if (item == null) {
                results.add(BulkRowResult.failed(row, null, "Dòng không có dữ liệu"));
                continue;
            }
            String rawCode = codeOf.apply(item);
            String code = rawCode != null && !rawCode.isBlank() ? rawCode.trim().toUpperCase() : rawCode;

            String invalid = validationMessage(item);
            if (invalid != null) {
                results.add(BulkRowResult.failed(row, code, invalid));
                continue;
            }
            Integer firstRow = firstRowOfCode.putIfAbsent(code, row);
            if (firstRow != null) {
                results.add(BulkRowResult.failed(row, code,
                        "Mã " + label + " '" + code + "' trùng với dòng " + firstRow + " trong cùng danh sách"));
                continue;
            }
            results.add(importRow(row, code, item, label, existingIdByCode, create));
        }
        BulkImportResponse response = BulkImportResponse.of(results);
        log.info("Nhập hàng loạt {} cho doanh nghiệp {}: {} dòng, tạo mới {}, bỏ qua {}, lỗi {}",
                label, TenantContext.getCompanyId(), response.total(), response.created(),
                response.skipped(), response.failed());
        return response;
    }

    private <T> BulkRowResult importRow(int row, String code, T item, String label,
                                        Function<String, Optional<Long>> existingIdByCode,
                                        Function<T, Long> create) {
        try {
            return rowTransaction.execute(status -> {
                Optional<Long> existingId = existingIdByCode.apply(code);
                if (existingId.isPresent()) {
                    return BulkRowResult.skipped(row, existingId.get(), code,
                            "Mã " + label + " '" + code + "' đã tồn tại, giữ nguyên dữ liệu đang có");
                }
                return BulkRowResult.created(row, create.apply(item), code);
            });
        } catch (ApiException ex) {
            return BulkRowResult.failed(row, code, ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            // Một yêu cầu khác vừa tạo cùng mã giữa lúc kiểm tra và lúc lưu
            return BulkRowResult.failed(row, code, "Mã " + label + " '" + code + "' vừa được tạo bởi yêu cầu khác");
        } catch (RuntimeException ex) {
            log.error("Lỗi không lường trước khi nhập dòng {} ({} '{}')", row, label, code, ex);
            return BulkRowResult.failed(row, code, "Không lưu được dòng này do lỗi hệ thống");
        }
    }

    /** Thông báo lỗi kiểm tra ràng buộc của một dòng, {@code null} nếu hợp lệ. */
    private <T> String validationMessage(T item) {
        Set<ConstraintViolation<T>> violations = validator.validate(item);
        if (violations.isEmpty()) {
            return null;
        }
        return violations.stream()
                .sorted(Comparator.comparing((ConstraintViolation<T> v) -> v.getPropertyPath().toString())
                        .thenComparing(ConstraintViolation::getMessage))
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
    }
}
