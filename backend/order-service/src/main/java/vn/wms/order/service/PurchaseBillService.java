package vn.wms.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.order.dto.PurchaseBillResponse;
import vn.wms.order.dto.PurchaseBillSummaryResponse;
import vn.wms.order.dto.RecordBillPaymentRequest;
import vn.wms.order.entity.GoodsReceiptNote;
import vn.wms.order.entity.GoodsReceiptNoteItem;
import vn.wms.order.entity.PurchaseBill;
import vn.wms.order.entity.PurchaseBillItem;
import vn.wms.order.entity.PurchaseBillStatus;
import vn.wms.order.entity.Supplier;
import vn.wms.order.entity.SupplierTransaction;
import vn.wms.order.entity.SupplierTransactionType;
import vn.wms.order.repository.PurchaseBillRepository;
import vn.wms.order.repository.SupplierRepository;
import vn.wms.order.repository.SupplierTransactionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service Quản lý Hóa đơn mua hàng (Purchase Bill) & Công nợ phải trả NCC (FR11, FR13 - WMS-10.3).
 */
@Service
public class PurchaseBillService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseBillService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Pattern DAYS_PATTERN = Pattern.compile("(\\d+)");

    private final PurchaseBillRepository purchaseBillRepository;
    private final SupplierRepository supplierRepository;
    private final SupplierTransactionRepository transactionRepository;

    private final ConcurrentHashMap<String, AtomicInteger> dailySequences = new ConcurrentHashMap<>();

    public PurchaseBillService(PurchaseBillRepository purchaseBillRepository,
                               SupplierRepository supplierRepository,
                               SupplierTransactionRepository transactionRepository) {
        this.purchaseBillRepository = purchaseBillRepository;
        this.supplierRepository = supplierRepository;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Tự động tạo Hóa đơn mua hàng (Purchase Bill) liên kết với Phiếu nhập kho (GRN)
     * và cập nhật tăng công nợ phải trả Nhà cung cấp (FR11, FR13 - WMS-10.3).
     */
    @Transactional
    public PurchaseBill createFromGrn(GoodsReceiptNote grn) {
        if (grn == null) {
            throw ApiException.badRequest("Phiếu nhập kho (GRN) không được null");
        }

        // Kiểm tra xem GRN này đã có Hóa đơn mua tương ứng chưa (tránh tạo trùng lặp)
        if (purchaseBillRepository.findByGoodsReceiptNoteId(grn.getId()).isPresent()) {
            log.info("Phiếu nhập kho GRN {} đã có Hóa đơn mua hàng tương ứng, bỏ qua tạo mới", grn.getGrnNumber());
            return purchaseBillRepository.findByGoodsReceiptNoteId(grn.getId()).get();
        }

        Supplier supplier = grn.getSupplier();
        if (supplier == null && grn.getPurchaseOrder() != null) {
            supplier = grn.getPurchaseOrder().getSupplier();
        }
        if (supplier == null) {
            throw ApiException.badRequest("Phiếu nhập kho #" + grn.getGrnNumber() + " không có thông tin Nhà cung cấp");
        }

        LocalDate billDate = grn.getReceiptDate() != null ? grn.getReceiptDate() : LocalDate.now();
        LocalDate dueDate = calculateDueDate(billDate, supplier.getPaymentTerms());

        BigDecimal totalAmount = grn.getTotalAmount() != null ? grn.getTotalAmount() : BigDecimal.ZERO;

        PurchaseBill bill = new PurchaseBill();
        bill.setBillNumber(generateBillNumber(billDate));
        bill.setGoodsReceiptNote(grn);
        bill.setGrnNumber(grn.getGrnNumber());
        bill.setPurchaseOrder(grn.getPurchaseOrder());
        bill.setPoNumber(grn.getPoNumber());
        bill.setSupplier(supplier);
        bill.setSupplierName(supplier.getName());
        bill.setBillDate(billDate);
        bill.setDueDate(dueDate);
        bill.setTotalAmount(totalAmount);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setRemainingAmount(totalAmount);
        bill.setStatus(PurchaseBillStatus.UNPAID);
        bill.setNote("Tự động tạo từ phiếu nhập kho " + grn.getGrnNumber() + " (Đơn mua " + grn.getPoNumber() + ")");

        if (grn.getItems() != null) {
            for (GoodsReceiptNoteItem grnItem : grn.getItems()) {
                if (grnItem.getAcceptedQuantity() != null && grnItem.getAcceptedQuantity().compareTo(BigDecimal.ZERO) > 0) {
                    PurchaseBillItem billItem = new PurchaseBillItem();
                    billItem.setProductId(grnItem.getProductId());
                    billItem.setSkuCode(grnItem.getSkuCode());
                    billItem.setProductName(grnItem.getProductName());
                    billItem.setUom(grnItem.getUom());
                    billItem.setQuantity(grnItem.getAcceptedQuantity());
                    billItem.setUnitPrice(grnItem.getUnitPrice());
                    billItem.setLineTotal(grnItem.getLineTotal());
                    billItem.setBatchNumber(grnItem.getBatchNumber());
                    bill.addItem(billItem);
                }
            }
        }

        PurchaseBill savedBill = purchaseBillRepository.save(bill);

        // Ghi nhận công nợ Nhà cung cấp nếu giá trị hóa đơn > 0
        if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
            final Long supplierId = supplier.getId();
            Supplier lockedSupplier = supplierRepository.findByIdForUpdate(supplierId)
                    .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp ID: " + supplierId));

            BigDecimal curDebt = lockedSupplier.getCurrentDebt() != null ? lockedSupplier.getCurrentDebt() : BigDecimal.ZERO;
            BigDecimal newDebt = curDebt.add(totalAmount);
            lockedSupplier.setCurrentDebt(newDebt);
            supplierRepository.save(lockedSupplier);

            SupplierTransaction tx = new SupplierTransaction();
            tx.setSupplier(lockedSupplier);
            tx.setTransactionType(SupplierTransactionType.PURCHASE_INVOICE);
            tx.setReferenceCode(savedBill.getBillNumber());
            tx.setAmount(totalAmount);
            tx.setBalanceAfter(newDebt);
            tx.setNote("Hóa đơn mua hàng " + savedBill.getBillNumber() + " từ phiếu nhập " + grn.getGrnNumber());
            tx.setTransactionDate(Instant.now());
            transactionRepository.save(tx);

            log.info("Đã tạo Purchase Bill '{}' cho GRN '{}' và cập nhật công nợ NCC '{}': số tiền={} VNĐ, nợ mới={} VNĐ",
                    savedBill.getBillNumber(), grn.getGrnNumber(), lockedSupplier.getName(), totalAmount, newDebt);
        }

        return savedBill;
    }

    /**
     * Ghi nhận thanh toán tiền hàng cho Nhà cung cấp theo Hóa đơn mua (FR13).
     * Cập nhật số tiền đã trả, chuyển trạng thái hóa đơn sang PAID khi trả đủ, và giảm công nợ NCC.
     */
    @Transactional
    public PurchaseBillResponse recordPayment(Long billId, RecordBillPaymentRequest request, String username) {
        PurchaseBill bill = purchaseBillRepository.findById(billId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy Hóa đơn mua hàng với ID: " + billId));

        if (bill.getStatus() == PurchaseBillStatus.PAID) {
            throw ApiException.badRequest("Hóa đơn '" + bill.getBillNumber() + "' đã được thanh toán đầy đủ 100%!");
        }

        if (bill.getStatus() == PurchaseBillStatus.CANCELLED) {
            throw ApiException.badRequest("Hóa đơn '" + bill.getBillNumber() + "' đã bị hủy, không thể ghi nhận thanh toán!");
        }

        BigDecimal paymentAmount = request.amount();
        bill.recordPayment(paymentAmount);
        PurchaseBill saved = purchaseBillRepository.save(bill);

        // Giảm công nợ nhà cung cấp với Khóa bi quan
        Supplier supplier = supplierRepository.findByIdForUpdate(bill.getSupplier().getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp ID: " + bill.getSupplier().getId()));

        BigDecimal curDebt = supplier.getCurrentDebt() != null ? supplier.getCurrentDebt() : BigDecimal.ZERO;
        BigDecimal newDebt = curDebt.subtract(paymentAmount);
        supplier.setCurrentDebt(newDebt);
        supplierRepository.save(supplier);

        // Ghi nhận lịch sử giao dịch thanh toán
        SupplierTransaction tx = new SupplierTransaction();
        tx.setSupplier(supplier);
        tx.setTransactionType(SupplierTransactionType.PAYMENT);
        tx.setReferenceCode(request.referenceCode() != null && !request.referenceCode().isBlank()
                ? request.referenceCode().trim() : "UNC-" + bill.getBillNumber());
        tx.setAmount(paymentAmount);
        tx.setBalanceAfter(newDebt);
        tx.setNote(request.note() != null && !request.note().isBlank()
                ? request.note().trim() : ("Thanh toán tiền hàng cho hóa đơn mua " + bill.getBillNumber()));
        tx.setTransactionDate(Instant.now());
        transactionRepository.save(tx);

        log.info("Đã ghi nhận thanh toán {} VNĐ cho hóa đơn '{}' bởi {}. Trạng thái mới: {}, công nợ NCC còn lại: {} VNĐ",
                paymentAmount, bill.getBillNumber(), username, saved.getStatus(), newDebt);

        return PurchaseBillResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PurchaseBillResponse getById(Long id) {
        PurchaseBill bill = purchaseBillRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy Hóa đơn mua hàng với ID: " + id));
        return PurchaseBillResponse.from(bill);
    }

    @Transactional(readOnly = true)
    public PurchaseBillResponse getByBillNumber(String billNumber) {
        PurchaseBill bill = purchaseBillRepository.findByBillNumber(billNumber.trim())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy Hóa đơn mua hàng với mã: " + billNumber));
        return PurchaseBillResponse.from(bill);
    }

    @Transactional(readOnly = true)
    public PurchaseBillResponse getByGrnId(Long grnId) {
        PurchaseBill bill = purchaseBillRepository.findByGoodsReceiptNoteId(grnId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy Hóa đơn mua hàng cho phiếu nhập ID: " + grnId));
        return PurchaseBillResponse.from(bill);
    }

    @Transactional(readOnly = true)
    public List<PurchaseBillResponse> listByPoId(Long poId) {
        return purchaseBillRepository.findByPurchaseOrderIdOrderByIdDesc(poId).stream()
                .map(PurchaseBillResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PurchaseBillResponse> search(
            String keyword,
            PurchaseBillStatus status,
            Long supplierId,
            Long poId,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {
        String trimmed = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return PageResponse.from(purchaseBillRepository.search(
                trimmed, status, supplierId, poId, fromDate, toDate, pageable
        ).map(PurchaseBillResponse::from));
    }

    @Transactional(readOnly = true)
    public PurchaseBillSummaryResponse getSummary() {
        long totalBills = purchaseBillRepository.count();
        long unpaidBills = purchaseBillRepository.countByStatus(PurchaseBillStatus.UNPAID);
        long partiallyPaidBills = purchaseBillRepository.countByStatus(PurchaseBillStatus.PARTIALLY_PAID);
        long paidBills = purchaseBillRepository.countByStatus(PurchaseBillStatus.PAID);

        BigDecimal totalPayables = purchaseBillRepository.sumTotalAmount();
        BigDecimal totalPaid = purchaseBillRepository.sumPaidAmount();
        BigDecimal totalRemaining = purchaseBillRepository.sumRemainingAmount();

        return new PurchaseBillSummaryResponse(
                totalBills,
                unpaidBills,
                partiallyPaidBills,
                paidBills,
                totalPayables != null ? totalPayables : BigDecimal.ZERO,
                totalPaid != null ? totalPaid : BigDecimal.ZERO,
                totalRemaining != null ? totalRemaining : BigDecimal.ZERO
        );
    }

    private String generateBillNumber(LocalDate date) {
        String dateStr = date.format(DATE_FORMATTER);
        AtomicInteger seq = dailySequences.computeIfAbsent(dateStr, k -> new AtomicInteger(0));
        int nextVal = seq.incrementAndGet();
        return String.format("PB-%s-%04d", dateStr, nextVal);
    }

    private LocalDate calculateDueDate(LocalDate billDate, String paymentTerms) {
        int days = 30; // Mặc định thời hạn thanh toán 30 ngày nếu không có quy định
        if (paymentTerms != null && !paymentTerms.isBlank()) {
            Matcher matcher = DAYS_PATTERN.matcher(paymentTerms);
            if (matcher.find()) {
                try {
                    days = Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return billDate.plusDays(days);
    }
}
