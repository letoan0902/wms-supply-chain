package vn.wms.order.service;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.order.dto.CreatePurchaseOrderRequest;
import vn.wms.order.dto.PurchaseOrderItemRequest;
import vn.wms.order.dto.PurchaseOrderResponse;
import vn.wms.order.dto.PurchaseOrderSummaryResponse;
import vn.wms.order.dto.RejectPurchaseOrderRequest;
import vn.wms.order.dto.UpdatePurchaseOrderRequest;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.PurchaseOrder;
import vn.wms.order.entity.PurchaseOrderItem;
import vn.wms.order.entity.Supplier;
import vn.wms.order.event.PurchaseOrderApprovedEvent;
import vn.wms.order.repository.PurchaseOrderRepository;
import vn.wms.order.repository.SupplierRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service Quản lý Đơn mua hàng (Purchase Order - PO) & Phê duyệt (FR10 - WMS-09.1).
 */
@Service
public class PurchaseOrderService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final DocumentNumberService documentNumberService;

    public PurchaseOrderService(PurchaseOrderRepository purchaseOrderRepository,
                                SupplierRepository supplierRepository,
                                ApplicationEventPublisher eventPublisher,
                                DocumentNumberService documentNumberService) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.supplierRepository = supplierRepository;
        this.eventPublisher = eventPublisher;
        this.documentNumberService = documentNumberService;
    }

    /**
     * Danh sách đơn mua hàng có phân trang (tương thích backward).
     */
    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> list(Pageable pageable) {
        return PageResponse.from(purchaseOrderRepository.findAll(pageable).map(PurchaseOrderResponse::from));
    }

    /**
     * Danh sách đơn mua hàng có tìm kiếm và bộ lọc đa tiêu chí (WMS-09.1).
     */
    @Transactional(readOnly = true)
    public PageResponse<PurchaseOrderResponse> search(
            String keyword,
            Long supplierId,
            Long warehouseId,
            OrderStatus status,
            LocalDate fromOrderDate,
            LocalDate toOrderDate,
            Pageable pageable) {
        String trimmedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return PageResponse.from(purchaseOrderRepository.search(
                trimmedKeyword, supplierId, warehouseId, status, fromOrderDate, toOrderDate, pageable
        ).map(PurchaseOrderResponse::from));
    }

    /**
     * Chi tiết đơn mua hàng theo ID.
     */
    @Transactional(readOnly = true)
    public PurchaseOrderResponse getById(Long id) {
        return PurchaseOrderResponse.from(findOrder(id));
    }

    /**
     * Chi tiết đơn mua hàng theo Mã PO.
     */
    @Transactional(readOnly = true)
    public PurchaseOrderResponse getByPoNumber(String poNumber) {
        PurchaseOrder po = purchaseOrderRepository.findByPoNumber(poNumber.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn mua hàng với mã: " + poNumber));
        return PurchaseOrderResponse.from(po);
    }

    /**
     * Tạo mới đơn mua hàng (Purchase Order - PO).
     * Tự động sinh mã PO nếu chưa có, tính toán thành tiền từng dòng, VAT, chiết khấu và Grand Total (FR10).
     */
    @Transactional
    public PurchaseOrderResponse create(CreatePurchaseOrderRequest request) {
        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp với id: " + request.supplierId()));

        LocalDate orderDate = request.orderDate() != null ? request.orderDate() : LocalDate.now();

        // 1. Xác định mã PO (tự sinh nếu không truyền)
        String poNumber;
        if (request.poNumber() != null && !request.poNumber().isBlank()) {
            poNumber = request.poNumber().trim().toUpperCase();
            if (purchaseOrderRepository.existsByPoNumber(poNumber)) {
                throw ApiException.conflict("Mã đơn mua hàng '" + poNumber + "' đã tồn tại trong hệ thống");
            }
        } else {
            poNumber = generatePoNumber(orderDate);
        }

        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber(poNumber);
        po.setSupplier(supplier);
        po.setWarehouseId(request.warehouseId());
        po.setWarehouseName(normalizeString(request.warehouseName()));
        po.setOrderDate(orderDate);
        po.setExpectedDate(request.expectedDate());
        po.setNote(normalizeString(request.note()));

        // Trạng thái khởi tạo: chỉ chấp nhận DRAFT hoặc PENDING, từ chối các giá trị khác để tránh bỏ qua phê duyệt
        OrderStatus initialStatus = OrderStatus.PENDING;
        if (request.status() != null) {
            if (request.status() != OrderStatus.DRAFT && request.status() != OrderStatus.PENDING) {
                throw ApiException.badRequest("Trạng thái khởi tạo chỉ được là DRAFT hoặc PENDING. Không được phép tạo đơn với trạng thái: " + request.status());
            }
            initialStatus = request.status();
        }
        po.setStatus(initialStatus);

        // 2. Tính toán chi tiết các dòng hàng
        applyItemsAndCalculateTotals(po, request.items());

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return PurchaseOrderResponse.from(saved);
    }

    /**
     * Cập nhật thông tin đơn mua hàng (chỉ cho phép khi ở trạng thái DRAFT hoặc PENDING).
     */
    @Transactional
    public PurchaseOrderResponse update(Long id, UpdatePurchaseOrderRequest request) {
        PurchaseOrder po = findOrder(id);

        if (po.getStatus() != OrderStatus.DRAFT && po.getStatus() != OrderStatus.PENDING) {
            throw ApiException.badRequest("Không thể cập nhật đơn mua hàng đang ở trạng thái: " + po.getStatus());
        }

        Supplier supplier = supplierRepository.findById(request.supplierId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy nhà cung cấp với id: " + request.supplierId()));

        po.setSupplier(supplier);
        po.setWarehouseId(request.warehouseId());
        po.setWarehouseName(normalizeString(request.warehouseName()));
        po.setOrderDate(request.orderDate());
        po.setExpectedDate(request.expectedDate());
        po.setNote(normalizeString(request.note()));

        // Xóa các dòng cũ và tính toán lại các dòng mới
        po.clearItems();
        applyItemsAndCalculateTotals(po, request.items());

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return PurchaseOrderResponse.from(saved);
    }

    /**
     * Xóa đơn mua hàng (chỉ cho phép khi đơn ở trạng thái DRAFT, PENDING hoặc CANCELLED).
     */
    @Transactional
    public void delete(Long id) {
        PurchaseOrder po = findOrder(id);
        if (po.getStatus() == OrderStatus.APPROVED || po.getStatus() == OrderStatus.CONFIRMED
                || po.getStatus() == OrderStatus.PARTIALLY_RECEIVED || po.getStatus() == OrderStatus.COMPLETED) {
            throw ApiException.badRequest("Không thể xóa đơn mua hàng đã được phê duyệt hoặc đang trong quá trình nhập kho (trạng thái: " + po.getStatus() + ")!");
        }
        purchaseOrderRepository.delete(po);
    }

    /**
     * Phê duyệt đơn mua hàng (PENDING -> APPROVED).
     * Phát sinh sự kiện sau commit (@TransactionalEventListener AFTER_COMMIT) sang RabbitMQ (WMS-09.2).
     */
    @Transactional
    public PurchaseOrderResponse approve(Long id, String approvedBy) {
        PurchaseOrder po = findOrder(id);

        if (po.getStatus() == OrderStatus.APPROVED) {
            throw ApiException.badRequest("Đơn mua hàng đã được phê duyệt trước đó!");
        }

        if (po.getStatus() != OrderStatus.PENDING && po.getStatus() != OrderStatus.DRAFT) {
            throw ApiException.badRequest("Không thể phê duyệt đơn mua hàng đang ở trạng thái: " + po.getStatus());
        }

        po.setStatus(OrderStatus.APPROVED);
        po.setApprovedBy(approvedBy != null && !approvedBy.isBlank() ? approvedBy.trim() : "SYSTEM");
        po.setApprovedAt(Instant.now());
        po.setRejectedReason(null);

        PurchaseOrder saved = purchaseOrderRepository.save(po);

        // Phát sự kiện Spring Event (sẽ được PurchaseOrderEventListener chuyển sang RabbitMQ sau commit)
        List<PurchaseOrderApprovedEvent.Item> eventItems = saved.getItems().stream()
                .map(i -> new PurchaseOrderApprovedEvent.Item(
                        i.getProductId(),
                        i.getSkuCode(),
                        i.getProductName(),
                        i.getQuantity(),
                        i.getUnitPrice(),
                        i.getUom()
                ))
                .toList();

        eventPublisher.publishEvent(new PurchaseOrderApprovedEvent(
                saved.getCompanyId(),
                saved.getId(),
                saved.getPoNumber(),
                saved.getSupplier() != null ? saved.getSupplier().getId() : null,
                saved.getWarehouseId(),
                saved.getWarehouseName(),
                saved.getTotalAmount(),
                saved.getStatus().name(),
                eventItems,
                Instant.now()
        ));

        return PurchaseOrderResponse.from(saved);
    }

    /**
     * Từ chối phê duyệt đơn mua hàng (PENDING -> REJECTED).
     */
    @Transactional
    public PurchaseOrderResponse reject(Long id, RejectPurchaseOrderRequest request, String rejectedBy) {
        PurchaseOrder po = findOrder(id);

        if (po.getStatus() == OrderStatus.APPROVED) {
            throw ApiException.badRequest("Không thể từ chối đơn mua hàng đã được phê duyệt!");
        }

        if (po.getStatus() != OrderStatus.PENDING && po.getStatus() != OrderStatus.DRAFT) {
            throw ApiException.badRequest("Không thể từ chối đơn mua hàng đang ở trạng thái: " + po.getStatus());
        }

        po.setStatus(OrderStatus.REJECTED);
        po.setRejectedReason(request.reason().trim());
        po.setApprovedBy(rejectedBy != null && !rejectedBy.isBlank() ? rejectedBy.trim() : null);
        po.setApprovedAt(null);

        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return PurchaseOrderResponse.from(saved);
    }

    /**
     * Hủy đơn mua hàng (chỉ cho phép khi ở trạng thái DRAFT hoặc PENDING).
     */
    @Transactional
    public PurchaseOrderResponse cancel(Long id) {
        PurchaseOrder po = findOrder(id);

        if (po.getStatus() != OrderStatus.DRAFT && po.getStatus() != OrderStatus.PENDING) {
            throw ApiException.badRequest("Chỉ được hủy đơn mua hàng ở trạng thái DRAFT hoặc PENDING. Đơn hiện tại đang ở trạng thái: " + po.getStatus());
        }

        po.setStatus(OrderStatus.CANCELLED);
        PurchaseOrder saved = purchaseOrderRepository.save(po);
        return PurchaseOrderResponse.from(saved);
    }

    /**
     * Thống kê tổng quan tình hình đơn mua hàng (Metrics Summary).
     */
    @Transactional(readOnly = true)
    public PurchaseOrderSummaryResponse getSummary() {
        long total = purchaseOrderRepository.count();
        long draft = purchaseOrderRepository.countByStatus(OrderStatus.DRAFT);
        long pending = purchaseOrderRepository.countByStatus(OrderStatus.PENDING);
        long approved = purchaseOrderRepository.countByStatus(OrderStatus.APPROVED);
        long rejected = purchaseOrderRepository.countByStatus(OrderStatus.REJECTED);
        long cancelled = purchaseOrderRepository.countByStatus(OrderStatus.CANCELLED);
        long partiallyReceived = purchaseOrderRepository.countByStatus(OrderStatus.PARTIALLY_RECEIVED);
        long completed = purchaseOrderRepository.countByStatus(OrderStatus.COMPLETED);
        BigDecimal totalApprovedAmount = purchaseOrderRepository.sumTotalAmountByStatus(OrderStatus.APPROVED);

        return new PurchaseOrderSummaryResponse(
                total,
                draft,
                pending,
                approved,
                rejected,
                cancelled,
                partiallyReceived,
                completed,
                totalApprovedAmount != null ? totalApprovedAmount : BigDecimal.ZERO
        );
    }

    /**
     * Tự động sinh mã PO theo ngày: PO-YYYYMMDD-XXXX, đánh số riêng cho từng doanh nghiệp (WMS-22.4b).
     * Bộ đếm có khóa dòng trong cơ sở dữ liệu nên giao dịch đồng thời không nhận trùng số (xem {@link DocumentNumberService}).
     */
    public String generatePoNumber(LocalDate date) {
        return documentNumberService.next("PO-" + date.format(DATE_FORMATTER) + "-",
                prefix -> purchaseOrderRepository.findTopByPoNumberStartingWithOrderByPoNumberDesc(prefix)
                        .map(PurchaseOrder::getPoNumber),
                purchaseOrderRepository::existsByPoNumber);
    }

    /**
     * Tính toán thành tiền từng dòng, VAT, chiết khấu và Grand Total của PO.
     */
    private void applyItemsAndCalculateTotals(PurchaseOrder po, List<PurchaseOrderItemRequest> itemRequests) {
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw ApiException.badRequest("Đơn mua hàng phải có ít nhất 1 dòng mặt hàng");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (PurchaseOrderItemRequest req : itemRequests) {
            BigDecimal qty = req.quantity() != null ? req.quantity() : BigDecimal.ZERO;
            BigDecimal price = req.unitPrice() != null ? req.unitPrice() : BigDecimal.ZERO;

            // 1. Thành tiền thô (Raw Amount)
            BigDecimal rawAmount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);

            // 2. Chiết khấu (Discount)
            BigDecimal discountPercent = req.discountPercent() != null ? req.discountPercent() : BigDecimal.ZERO;
            BigDecimal discountAmount;
            if (req.discountAmount() != null && req.discountAmount().compareTo(BigDecimal.ZERO) > 0) {
                discountAmount = req.discountAmount().setScale(2, RoundingMode.HALF_UP);
            } else if (discountPercent.compareTo(BigDecimal.ZERO) > 0) {
                discountAmount = rawAmount.multiply(discountPercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else {
                discountAmount = BigDecimal.ZERO;
            }

            // Tiền sau chiết khấu
            BigDecimal netAmount = rawAmount.subtract(discountAmount).max(BigDecimal.ZERO);

            // 3. Thuế VAT (Tax)
            BigDecimal taxRate = req.taxRate() != null ? req.taxRate() : BigDecimal.ZERO;
            BigDecimal taxAmount;
            if (req.taxAmount() != null && req.taxAmount().compareTo(BigDecimal.ZERO) > 0) {
                taxAmount = req.taxAmount().setScale(2, RoundingMode.HALF_UP);
            } else if (taxRate.compareTo(BigDecimal.ZERO) > 0) {
                taxAmount = netAmount.multiply(taxRate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            } else {
                taxAmount = BigDecimal.ZERO;
            }

            // 4. Thành tiền dòng (Line Total)
            BigDecimal lineTotal = netAmount.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setProductId(req.productId());
            item.setSkuCode(req.skuCode().trim().toUpperCase());
            item.setProductName(req.productName().trim());
            item.setUom(normalizeString(req.uom()));
            item.setQuantity(qty);
            item.setUnitPrice(price);
            item.setDiscountPercent(discountPercent);
            item.setDiscountAmount(discountAmount);
            item.setTaxRate(taxRate);
            item.setTaxAmount(taxAmount);
            item.setLineTotal(lineTotal);
            item.setNote(normalizeString(req.note()));

            po.addItem(item);

            subtotal = subtotal.add(rawAmount);
            totalDiscount = totalDiscount.add(discountAmount);
            totalTax = totalTax.add(taxAmount);
            grandTotal = grandTotal.add(lineTotal);
        }

        po.setSubtotalAmount(subtotal);
        po.setDiscountAmount(totalDiscount);
        po.setTaxAmount(totalTax);
        po.setTotalAmount(grandTotal);
    }

    private PurchaseOrder findOrder(Long id) {
        return purchaseOrderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn mua hàng với id: " + id));
    }

    private String normalizeString(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        return val.trim();
    }
}
