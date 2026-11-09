package vn.wms.order.service;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.order.dto.CreateCustomerRequest;
import vn.wms.order.dto.CustomerCreditCheckResponse;
import vn.wms.order.dto.CustomerDebtSummaryResponse;
import vn.wms.order.dto.CustomerResponse;
import vn.wms.order.dto.CustomerTransactionResponse;
import vn.wms.order.dto.RecordCustomerTransactionRequest;
import vn.wms.order.dto.UpdateCustomerRequest;
import vn.wms.order.entity.Customer;
import vn.wms.order.entity.CustomerTransaction;
import vn.wms.order.entity.CustomerType;
import vn.wms.order.repository.CustomerRepository;
import vn.wms.order.repository.CustomerTransactionRepository;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.Instant;

/**
 * Service Quản lý hồ sơ Khách hàng / Đại lý & Kiểm soát hạn mức công nợ (FR14 - WMS-08.2).
 */
@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerTransactionRepository transactionRepository;

    public CustomerService(CustomerRepository customerRepository,
                           CustomerTransactionRepository transactionRepository) {
        this.customerRepository = customerRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Danh sách khách hàng có phân trang, tìm kiếm và lọc theo loại (Sỉ/Lẻ), trạng thái hoạt động / khóa nợ / vượt hạn mức.
     */
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> list(String keyword, CustomerType type, Boolean active, Boolean debtLocked, Boolean overLimit, Pageable pageable) {
        String trimmed = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return PageResponse.from(customerRepository.search(trimmed, type, active, debtLocked, overLimit, pageable)
                .map(CustomerResponse::from));
    }

    /**
     * Chi tiết khách hàng theo ID.
     */
    @Transactional(readOnly = true)
    public CustomerResponse getById(Long id) {
        return CustomerResponse.from(findCustomer(id));
    }

    /**
     * Chi tiết khách hàng theo Mã code.
     */
    @Transactional(readOnly = true)
    public CustomerResponse getByCode(String code) {
        Customer customer = customerRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng với mã: " + code));
        return CustomerResponse.from(customer);
    }

    /**
     * Tạo mới hồ sơ Khách hàng / Đại lý (FR14).
     */
    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        String cleanCode = request.code().trim().toUpperCase();
        if (customerRepository.existsByCode(cleanCode)) {
            throw ApiException.conflict("Mã khách hàng '" + cleanCode + "' đã tồn tại trong hệ thống");
        }

        String cleanTaxCode = normalizeString(request.taxCode());
        if (cleanTaxCode != null && customerRepository.existsByTaxCode(cleanTaxCode)) {
            throw ApiException.conflict("Mã số thuế '" + cleanTaxCode + "' đã được sử dụng bởi khách hàng khác");
        }

        BigDecimal creditLimit = request.creditLimit() != null ? request.creditLimit() : BigDecimal.ZERO;
        BigDecimal initialDebt = request.initialDebt() != null ? request.initialDebt() : BigDecimal.ZERO;

        Customer customer = new Customer();
        customer.setCode(cleanCode);
        customer.setName(request.name().trim());
        customer.setCustomerType(request.customerType() != null ? request.customerType() : CustomerType.RETAIL);
        customer.setTaxCode(cleanTaxCode);
        customer.setContactName(normalizeString(request.contactName()));
        customer.setEmail(normalizeString(request.email()));
        customer.setPhone(normalizeString(request.phone()));
        customer.setAddress(normalizeString(request.address()));
        customer.setPaymentTerms(normalizeString(request.paymentTerms()));
        customer.setCreditLimit(creditLimit);
        customer.setCurrentDebt(initialDebt);
        customer.setDebtLocked(false);
        customer.setNotes(normalizeString(request.notes()));
        customer.setActive(true);

        Customer saved = customerRepository.save(customer);

        // Ghi nhận số dư công nợ ban đầu nếu có
        if (initialDebt.compareTo(BigDecimal.ZERO) > 0) {
            CustomerTransaction initialTx = new CustomerTransaction();
            initialTx.setCustomer(saved);
            initialTx.setTransactionType(vn.wms.order.entity.CustomerTransactionType.ADJUSTMENT);
            initialTx.setReferenceCode("DK-" + cleanCode);
            initialTx.setAmount(initialDebt);
            initialTx.setBalanceAfter(initialDebt);
            initialTx.setNote("Ghi nhận số dư công nợ ban đầu khi tạo hồ sơ khách hàng");
            initialTx.setTransactionDate(Instant.now());
            transactionRepository.save(initialTx);
        }

        return CustomerResponse.from(saved);
    }

    /**
     * Cập nhật thông tin hồ sơ Khách hàng / Đại lý (FR14).
     */
    @Transactional
    public CustomerResponse update(Long id, UpdateCustomerRequest request) {
        Customer customer = findCustomer(id);

        String cleanTaxCode = normalizeString(request.taxCode());
        if (cleanTaxCode != null
                && !cleanTaxCode.equalsIgnoreCase(customer.getTaxCode())
                && customerRepository.existsByTaxCodeAndIdNot(cleanTaxCode, id)) {
            throw ApiException.conflict("Mã số thuế '" + cleanTaxCode + "' đã được sử dụng bởi khách hàng khác");
        }

        customer.setName(request.name().trim());
        if (request.customerType() != null) {
            customer.setCustomerType(request.customerType());
        }
        customer.setTaxCode(cleanTaxCode);
        customer.setContactName(normalizeString(request.contactName()));
        customer.setEmail(normalizeString(request.email()));
        customer.setPhone(normalizeString(request.phone()));
        customer.setAddress(normalizeString(request.address()));
        customer.setPaymentTerms(normalizeString(request.paymentTerms()));

        if (request.creditLimit() != null) {
            customer.setCreditLimit(request.creditLimit());
        }

        if (request.debtLocked() != null) {
            customer.setDebtLocked(request.debtLocked());
        }

        if (request.notes() != null) {
            customer.setNotes(normalizeString(request.notes()));
        }

        if (request.active() != null) {
            customer.setActive(request.active());
        }

        return CustomerResponse.from(customerRepository.save(customer));
    }

    /**
     * Xóa mềm khách hàng (chuyển active = false để không mất tham chiếu ở Đơn bán hàng SO).
     */
    @Transactional
    public void delete(Long id) {
        Customer customer = findCustomer(id);
        customer.setActive(false);
        customerRepository.save(customer);
    }

    /**
     * API Kiểm tra hạn mức công nợ (Credit Check) trước khi tạo / duyệt đơn bán hàng (FR14).
     *
     * @param customerId  ID khách hàng
     * @param orderAmount Giá trị đơn hàng dự kiến xuất bán (VNĐ)
     * @return Kết quả đánh giá: cho phép tiếp tục (canProceed), cảnh báo (warning) hoặc chặn (blocked)
     */
    @Transactional(readOnly = true)
    public CustomerCreditCheckResponse checkCredit(Long customerId, BigDecimal orderAmount) {
        Customer customer = findCustomer(customerId);

        BigDecimal currentDebt = customer.getCurrentDebt() != null ? customer.getCurrentDebt() : BigDecimal.ZERO;
        BigDecimal creditLimit = customer.getCreditLimit() != null ? customer.getCreditLimit() : BigDecimal.ZERO;
        BigDecimal amount = (orderAmount != null && orderAmount.compareTo(BigDecimal.ZERO) > 0) ? orderAmount : BigDecimal.ZERO;
        BigDecimal projectedDebt = currentDebt.add(amount);

        BigDecimal availableCredit = creditLimit.compareTo(BigDecimal.ZERO) > 0
                ? creditLimit.subtract(currentDebt).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;

        DecimalFormat df = new DecimalFormat("#,### đ");

        // 1. Kiểm tra trạng thái hoạt động
        if (!customer.isActive()) {
            return new CustomerCreditCheckResponse(
                    customer.getId(),
                    customer.getCode(),
                    customer.getName(),
                    customer.getCustomerType(),
                    creditLimit,
                    currentDebt,
                    availableCredit,
                    amount,
                    projectedDebt,
                    customer.isDebtLocked(),
                    true,
                    false,
                    "BLOCKED_INACTIVE",
                    "Khách hàng đang trong trạng thái ngưng hoạt động. Không thể tạo đơn bán hàng."
            );
        }

        // 2. Kiểm tra cờ khóa nợ chủ động (nợ xấu / nợ quá hạn)
        if (customer.isDebtLocked()) {
            return new CustomerCreditCheckResponse(
                    customer.getId(),
                    customer.getCode(),
                    customer.getName(),
                    customer.getCustomerType(),
                    creditLimit,
                    currentDebt,
                    availableCredit,
                    amount,
                    projectedDebt,
                    true,
                    true,
                    false,
                    "BLOCKED_DEBT_LOCKED",
                    "Khách hàng đang bị KHÓA NỢ do có công nợ quá hạn hoặc nợ xấu. Vui lòng thanh toán công nợ trước khi tiếp tục đặt hàng!"
            );
        }

        // 3. Khách hàng không có hạn mức nợ (Credit Limit = 0)
        if (creditLimit.compareTo(BigDecimal.ZERO) <= 0) {
            // Đối với khách lẻ (RETAIL) hoặc khách sỉ không được nợ
            return new CustomerCreditCheckResponse(
                    customer.getId(),
                    customer.getCode(),
                    customer.getName(),
                    customer.getCustomerType(),
                    BigDecimal.ZERO,
                    currentDebt,
                    BigDecimal.ZERO,
                    amount,
                    projectedDebt,
                    false,
                    false,
                    true,
                    "ALLOWED",
                    "Khách hàng không áp dụng hạn mức nợ trả chậm (áp dụng hình thức thanh toán ngay)."
            );
        }

        // 4. Kiểm tra vượt hạn mức nợ (Over Credit Limit)
        if (projectedDebt.compareTo(creditLimit) > 0) {
            BigDecimal exceeded = projectedDebt.subtract(creditLimit);
            return new CustomerCreditCheckResponse(
                    customer.getId(),
                    customer.getCode(),
                    customer.getName(),
                    customer.getCustomerType(),
                    creditLimit,
                    currentDebt,
                    availableCredit,
                    amount,
                    projectedDebt,
                    false,
                    true,
                    false,
                    "BLOCKED_OVER_LIMIT",
                    "Đơn hàng này khiến tổng công nợ dự kiến (" + df.format(projectedDebt)
                            + ") vượt quá hạn mức nợ tối đa (" + df.format(creditLimit)
                            + ") một khoản là " + df.format(exceeded) + ". Đơn hàng bị CHẶN theo quy định FR14!"
            );
        }

        // 5. Cảnh báo sắp chạm hạn mức nợ (>= 90% hạn mức)
        BigDecimal warningThreshold = creditLimit.multiply(new BigDecimal("0.90"));
        if (projectedDebt.compareTo(warningThreshold) >= 0) {
            return new CustomerCreditCheckResponse(
                    customer.getId(),
                    customer.getCode(),
                    customer.getName(),
                    customer.getCustomerType(),
                    creditLimit,
                    currentDebt,
                    availableCredit,
                    amount,
                    projectedDebt,
                    false,
                    false,
                    true,
                    "WARNING_NEAR_LIMIT",
                    "CẢNH BÁO: Công nợ sau đơn hàng này (" + df.format(projectedDebt)
                            + ") đã đạt " + (projectedDebt.multiply(BigDecimal.valueOf(100)).divide(creditLimit, 0, java.math.RoundingMode.HALF_UP))
                            + "% hạn mức nợ cho phép (" + df.format(creditLimit) + ")."
            );
        }

        // 6. Cho phép duyệt bình thường
        return new CustomerCreditCheckResponse(
                customer.getId(),
                customer.getCode(),
                customer.getName(),
                customer.getCustomerType(),
                creditLimit,
                currentDebt,
                availableCredit,
                amount,
                projectedDebt,
                false,
                false,
                true,
                "ALLOWED",
                "Hạn mức công nợ hợp lệ. Cho phép duyệt đơn bán hàng."
        );
    }

    /**
     * Ghi nhận giao dịch phát sinh công nợ với Khách hàng (FR14).
     * Áp dụng khóa bi quan (PESSIMISTIC_WRITE) để khóa dòng khách hàng (SELECT ... FOR UPDATE),
     * đảm bảo tính tuần tự, chống mất dữ liệu khi ghi nhận nhiều giao dịch đồng thời.
     */
    @Transactional
    public CustomerTransactionResponse recordTransaction(Long customerId, RecordCustomerTransactionRequest request) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng với id: " + customerId));

        BigDecimal current = customer.getCurrentDebt() != null ? customer.getCurrentDebt() : BigDecimal.ZERO;
        BigDecimal newDebt;

        switch (request.type()) {
            case SALES_INVOICE -> newDebt = current.add(request.amount());
            case PAYMENT -> {
                if (current.compareTo(BigDecimal.ZERO) <= 0) {
                    throw ApiException.badRequest("Khách hàng '" + customer.getName() + "' hiện không có dư nợ, không thể lập phiếu thu tiền!");
                }
                if (request.amount().compareTo(current) > 0) {
                    throw ApiException.badRequest("Số tiền thu không được vượt quá số nợ hiện tại (" + current + " đ) của khách hàng!");
                }
                newDebt = current.subtract(request.amount());
            }
            case RETURN -> newDebt = current.subtract(request.amount());
            case ADJUSTMENT -> newDebt = current.add(request.amount());
            default -> throw ApiException.badRequest("Loại giao dịch không hỗ trợ: " + request.type());
        }

        customer.setCurrentDebt(newDebt);
        customerRepository.save(customer);

        CustomerTransaction tx = new CustomerTransaction();
        tx.setCustomer(customer);
        tx.setTransactionType(request.type());
        tx.setReferenceCode(normalizeString(request.referenceCode()));
        tx.setAmount(request.amount());
        tx.setBalanceAfter(newDebt);
        tx.setNote(normalizeString(request.note()));
        tx.setTransactionDate(Instant.now());

        CustomerTransaction savedTx = transactionRepository.save(tx);
        return CustomerTransactionResponse.from(savedTx);
    }

    /**
     * Lịch sử giao dịch công nợ của một khách hàng.
     */
    @Transactional(readOnly = true)
    public PageResponse<CustomerTransactionResponse> listTransactions(Long customerId, Pageable pageable) {
        findCustomer(customerId);
        return PageResponse.from(transactionRepository.findByCustomerIdOrderByIdDesc(customerId, pageable)
                .map(CustomerTransactionResponse::from));
    }

    /**
     * Tổng hợp tình hình công nợ toàn bộ khách hàng.
     */
    @Transactional(readOnly = true)
    public CustomerDebtSummaryResponse getDebtSummary() {
        long total = customerRepository.count();
        long active = customerRepository.countByActiveTrue();
        long debtLocked = customerRepository.countByDebtLockedTrue();
        long overLimit = customerRepository.countOverLimitCustomers();
        BigDecimal totalDebt = customerRepository.sumTotalDebt();

        return new CustomerDebtSummaryResponse(
                total,
                active,
                debtLocked,
                overLimit,
                totalDebt != null ? totalDebt : BigDecimal.ZERO
        );
    }

    private Customer findCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng với id: " + id));
    }

    private String normalizeString(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        return val.trim();
    }
}
