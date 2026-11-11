package vn.wms.order.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.order.dto.CreateSupplierRequest;
import vn.wms.order.dto.RecordSupplierTransactionRequest;
import vn.wms.order.dto.SupplierDebtSummaryResponse;
import vn.wms.order.dto.SupplierResponse;
import vn.wms.order.dto.SupplierTransactionResponse;
import vn.wms.order.dto.UpdateSupplierRequest;
import vn.wms.order.entity.PurchaseBill;
import vn.wms.order.entity.PurchaseBillStatus;
import vn.wms.order.entity.Supplier;
import vn.wms.order.entity.SupplierTransaction;
import vn.wms.order.entity.SupplierTransactionType;
import vn.wms.order.repository.PurchaseBillRepository;
import vn.wms.order.repository.SupplierRepository;
import vn.wms.order.repository.SupplierTransactionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Service quản lý danh mục Nhà cung cấp & Theo dõi công nợ (FR09 - WMS-08.1).
 */
@Service
public class SupplierService {

    private final SupplierRepository supplierRepository;
    private final SupplierTransactionRepository transactionRepository;

    @Autowired(required = false)
    private PurchaseBillRepository purchaseBillRepository;

    public SupplierService(SupplierRepository supplierRepository,
                           SupplierTransactionRepository transactionRepository) {
        this.supplierRepository = supplierRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Danh sách nhà cung cấp có phân trang, tìm kiếm nhanh và lọc theo trạng thái hoạt động.
     */
    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(String keyword, Boolean active, Pageable pageable) {
        String trimmed = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return PageResponse.from(supplierRepository.search(trimmed, active, pageable)
                .map(SupplierResponse::from));
    }

    /**
     * Lấy chi tiết nhà cung cấp theo ID.
     */
    @Transactional(readOnly = true)
    public SupplierResponse getById(Long id) {
        return SupplierResponse.from(findSupplier(id));
    }

    /**
     * Lấy chi tiết nhà cung cấp theo Mã code.
     */
    @Transactional(readOnly = true)
    public SupplierResponse getByCode(String code) {
        Supplier supplier = supplierRepository.findByCode(code.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp với mã: " + code));
        return SupplierResponse.from(supplier);
    }

    /**
     * Tạo mới hồ sơ Nhà cung cấp (FR09).
     */
    @Transactional
    public SupplierResponse create(CreateSupplierRequest request) {
        String cleanCode = request.code().trim().toUpperCase();
        if (supplierRepository.existsByCode(cleanCode)) {
            throw ApiException.conflict("Mã nhà cung cấp '" + cleanCode + "' đã tồn tại trong hệ thống");
        }

        String cleanTaxCode = normalizeString(request.taxCode());
        if (cleanTaxCode != null && supplierRepository.existsByTaxCode(cleanTaxCode)) {
            throw ApiException.conflict("Mã số thuế '" + cleanTaxCode + "' đã được sử dụng bởi nhà cung cấp khác");
        }

        BigDecimal creditLimit = request.creditLimit() != null ? request.creditLimit() : BigDecimal.ZERO;
        BigDecimal initialDebt = request.initialDebt() != null ? request.initialDebt() : BigDecimal.ZERO;

        Supplier supplier = new Supplier();
        supplier.setCode(cleanCode);
        supplier.setName(request.name().trim());
        supplier.setTaxCode(cleanTaxCode);
        supplier.setContactName(normalizeString(request.contactName()));
        supplier.setEmail(normalizeString(request.email()));
        supplier.setPhone(normalizeString(request.phone()));
        supplier.setAddress(normalizeString(request.address()));
        supplier.setBankAccount(normalizeString(request.bankAccount()));
        supplier.setBankName(normalizeString(request.bankName()));
        supplier.setPaymentTerms(normalizeString(request.paymentTerms()));
        supplier.setCreditLimit(creditLimit);
        supplier.setCurrentDebt(initialDebt);
        supplier.setActive(true);

        Supplier saved = supplierRepository.save(supplier);

        // Nếu có công nợ ban đầu, ghi nhận 1 giao dịch số dư đầu kỳ
        if (initialDebt.compareTo(BigDecimal.ZERO) > 0) {
            SupplierTransaction initialTx = new SupplierTransaction();
            initialTx.setSupplier(saved);
            initialTx.setTransactionType(SupplierTransactionType.ADJUSTMENT);
            initialTx.setReferenceCode("DK-" + cleanCode);
            initialTx.setAmount(initialDebt);
            initialTx.setBalanceAfter(initialDebt);
            initialTx.setNote("Ghi nhận số dư công nợ ban đầu khi tạo hồ sơ nhà cung cấp");
            initialTx.setTransactionDate(Instant.now());
            transactionRepository.save(initialTx);
        }

        return SupplierResponse.from(saved);
    }

    /**
     * Cập nhật thông tin hồ sơ Nhà cung cấp (FR09).
     */
    @Transactional
    public SupplierResponse update(Long id, UpdateSupplierRequest request) {
        Supplier supplier = findSupplier(id);

        String cleanTaxCode = normalizeString(request.taxCode());
        if (cleanTaxCode != null
                && !cleanTaxCode.equalsIgnoreCase(supplier.getTaxCode())
                && supplierRepository.existsByTaxCodeAndIdNot(cleanTaxCode, id)) {
            throw ApiException.conflict("Mã số thuế '" + cleanTaxCode + "' đã được sử dụng bởi nhà cung cấp khác");
        }

        supplier.setName(request.name().trim());
        supplier.setTaxCode(cleanTaxCode);
        supplier.setContactName(normalizeString(request.contactName()));
        supplier.setEmail(normalizeString(request.email()));
        supplier.setPhone(normalizeString(request.phone()));
        supplier.setAddress(normalizeString(request.address()));
        supplier.setBankAccount(normalizeString(request.bankAccount()));
        supplier.setBankName(normalizeString(request.bankName()));
        supplier.setPaymentTerms(normalizeString(request.paymentTerms()));

        if (request.creditLimit() != null) {
            supplier.setCreditLimit(request.creditLimit());
        }

        if (request.active() != null) {
            supplier.setActive(request.active());
        }

        return SupplierResponse.from(supplierRepository.save(supplier));
    }

    /**
     * Xóa mềm nhà cung cấp (chuyển active = false để không mất tham chiếu ở Đơn mua hàng PO).
     */
    @Transactional
    public void delete(Long id) {
        Supplier supplier = findSupplier(id);
        supplier.setActive(false);
        supplierRepository.save(supplier);
    }

    /**
     * Ghi nhận giao dịch phát sinh công nợ với Nhà cung cấp (FR09).
     * Áp dụng khóa bi quan (PESSIMISTIC_WRITE) để khóa bản ghi nhà cung cấp (SELECT ... FOR UPDATE),
     * chống tranh chấp dữ liệu (Race Condition) khi có nhiều giao dịch phát sinh đồng thời.
     */
    @Transactional
    public SupplierTransactionResponse recordTransaction(Long supplierId, RecordSupplierTransactionRequest request) {
        Supplier supplier = supplierRepository.findByIdForUpdate(supplierId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp với id: " + supplierId));

        BigDecimal current = supplier.getCurrentDebt() != null ? supplier.getCurrentDebt() : BigDecimal.ZERO;
        BigDecimal newDebt;

        switch (request.type()) {
            case PURCHASE_INVOICE -> newDebt = current.add(request.amount());
            case PAYMENT, RETURN -> newDebt = current.subtract(request.amount());
            case ADJUSTMENT -> newDebt = current.add(request.amount());
            default -> throw ApiException.badRequest("Loại giao dịch không hỗ trợ: " + request.type());
        }

        supplier.setCurrentDebt(newDebt);
        supplierRepository.save(supplier);

        // Đồng bộ giảm remainingAmount trên các hóa đơn mua chưa trả đủ (nếu có) khi thanh toán
        if (request.type() == SupplierTransactionType.PAYMENT && purchaseBillRepository != null) {
            try {
                List<PurchaseBill> unpaidBills = purchaseBillRepository.findBySupplierIdAndStatusInOrderByBillDateAsc(
                        supplierId, List.of(PurchaseBillStatus.UNPAID, PurchaseBillStatus.PARTIALLY_PAID));
                BigDecimal rem = request.amount();
                for (PurchaseBill bill : unpaidBills) {
                    if (rem.compareTo(BigDecimal.ZERO) <= 0) break;
                    BigDecimal billRemain = bill.getRemainingAmount() != null ? bill.getRemainingAmount() : BigDecimal.ZERO;
                    if (billRemain.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal payForBill = rem.min(billRemain);
                    bill.recordPayment(payForBill);
                    purchaseBillRepository.save(bill);
                    rem = rem.subtract(payForBill);
                }
            } catch (Exception ex) {
                // Ghi log cảnh báo nhưng không làm thất bại giao dịch công nợ NCC
            }
        }

        SupplierTransaction tx = new SupplierTransaction();
        tx.setSupplier(supplier);
        tx.setTransactionType(request.type());
        tx.setReferenceCode(normalizeString(request.referenceCode()));
        tx.setAmount(request.amount());
        tx.setBalanceAfter(newDebt);
        tx.setNote(normalizeString(request.note()));
        tx.setTransactionDate(Instant.now());

        SupplierTransaction savedTx = transactionRepository.save(tx);
        return SupplierTransactionResponse.from(savedTx);
    }

    /**
     * Danh sách lịch sử giao dịch công nợ của một nhà cung cấp.
     */
    @Transactional(readOnly = true)
    public PageResponse<SupplierTransactionResponse> listTransactions(Long supplierId, Pageable pageable) {
        findSupplier(supplierId); // kiểm tra tồn tại
        return PageResponse.from(transactionRepository.findBySupplierIdOrderByIdDesc(supplierId, pageable)
                .map(SupplierTransactionResponse::from));
    }

    /**
     * Tổng hợp tình hình công nợ toàn bộ nhà cung cấp.
     */
    @Transactional(readOnly = true)
    public SupplierDebtSummaryResponse getDebtSummary() {
        long total = supplierRepository.count();
        long active = supplierRepository.countByActiveTrue();
        long overLimit = supplierRepository.countOverLimitSuppliers();
        BigDecimal totalDebt = supplierRepository.sumTotalDebt();

        return new SupplierDebtSummaryResponse(
                total,
                active,
                overLimit,
                totalDebt != null ? totalDebt : BigDecimal.ZERO
        );
    }

    private Supplier findSupplier(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp với id: " + id));
    }

    private String normalizeString(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        return val.trim();
    }
}
