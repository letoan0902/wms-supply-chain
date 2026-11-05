package vn.wms.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.order.client.AtpResponse;
import vn.wms.order.client.InventoryGateway;
import vn.wms.order.client.WarehouseSummary;
import vn.wms.order.dto.AtpCheckRequest;
import vn.wms.order.dto.AtpCheckResponse;
import vn.wms.order.dto.CreateSalesOrderRequest;
import vn.wms.order.dto.CustomerCreditCheckResponse;
import vn.wms.order.dto.SalesOrderItemRequest;
import vn.wms.order.dto.SalesOrderResponse;
import vn.wms.order.dto.SalesOrderSummaryResponse;
import vn.wms.order.dto.UpdateSalesOrderRequest;
import vn.wms.order.entity.Customer;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.SalesOrder;
import vn.wms.order.entity.SalesOrderItem;
import vn.wms.order.repository.CustomerRepository;
import vn.wms.order.repository.SalesOrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service Quản lý Đơn bán hàng (Sales Order - SO) & Tồn khả dụng (ATP) (FR15 - WMS-11.1).
 */
@Service
public class SalesOrderService {

    private static final Logger log = LoggerFactory.getLogger(SalesOrderService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SalesOrderRepository salesOrderRepository;
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final InventoryGateway inventoryGateway;

    @Autowired(required = false)
    private WmsEventPublisher wmsEventPublisher;

    private final ConcurrentHashMap<String, AtomicInteger> dailySequences = new ConcurrentHashMap<>();

    public SalesOrderService(SalesOrderRepository salesOrderRepository,
                             CustomerRepository customerRepository,
                             CustomerService customerService,
                             InventoryGateway inventoryGateway) {
        this.salesOrderRepository = salesOrderRepository;
        this.customerRepository = customerRepository;
        this.customerService = customerService;
        this.inventoryGateway = inventoryGateway;
    }

    /**
     * Danh sách đơn bán hàng có tìm kiếm và bộ lọc đa tiêu chí (WMS-11.1).
     */
    @Transactional(readOnly = true)
    public PageResponse<SalesOrderResponse> search(
            String keyword,
            Long customerId,
            Long warehouseId,
            OrderStatus status,
            LocalDate fromOrderDate,
            LocalDate toOrderDate,
            Pageable pageable) {
        String trimmedKeyword = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return PageResponse.from(salesOrderRepository.search(
                trimmedKeyword, customerId, warehouseId, status, fromOrderDate, toOrderDate, pageable
        ).map(SalesOrderResponse::from));
    }

    /**
     * Chi tiết đơn bán hàng theo ID.
     */
    @Transactional(readOnly = true)
    public SalesOrderResponse getById(Long id) {
        return SalesOrderResponse.from(findOrder(id));
    }

    /**
     * Chi tiết đơn bán hàng theo Mã SO.
     */
    @Transactional(readOnly = true)
    public SalesOrderResponse getBySoNumber(String soNumber) {
        SalesOrder so = salesOrderRepository.findBySoNumber(soNumber.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn bán hàng với mã: " + soNumber));
        return SalesOrderResponse.from(so);
    }

    /**
     * Kiểm tra tồn khả dụng ATP (Available To Promise) trước khi submit tạo đơn (WMS-11.1).
     * ATP = OnHand - Reserved.
     */
    @Transactional(readOnly = true)
    public AtpCheckResponse checkAtp(AtpCheckRequest request) {
        if (request.warehouseId() != null) {
            inventoryGateway.requireActiveWarehouse(request.warehouseId());
        }

        boolean canFulfillAll = true;
        List<AtpCheckResponse.ItemResult> itemResults = new ArrayList<>();

        for (AtpCheckRequest.ItemCheck item : request.items()) {
            BigDecimal requestedQty = item.quantity() != null ? item.quantity() : BigDecimal.ZERO;
            AtpResponse atp = inventoryGateway.getAtp(item.productId(), request.warehouseId());

            BigDecimal onHand = atp.onHandQuantity();
            BigDecimal reserved = atp.reservedQuantity();
            BigDecimal atpQty = atp.atpQuantity();

            boolean isSufficient = requestedQty.compareTo(atpQty) <= 0;
            if (!isSufficient) {
                canFulfillAll = false;
            }
            BigDecimal shortage = isSufficient ? BigDecimal.ZERO : requestedQty.subtract(atpQty);

            itemResults.add(new AtpCheckResponse.ItemResult(
                    item.productId(),
                    item.skuCode(),
                    item.productName(),
                    requestedQty,
                    onHand,
                    reserved,
                    atpQty,
                    isSufficient,
                    shortage
            ));
        }

        return new AtpCheckResponse(request.warehouseId(), canFulfillAll, itemResults);
    }

    /**
     * Tạo mới đơn bán hàng (Sales Order - SO).
     * Tự động sinh mã SO, tính toán thành tiền từng dòng, VAT, chiết khấu, Grand Total (FR15),
     * kiểm tra hạn mức tín dụng khách hàng (FR14) và kiểm tra tồn khả dụng ATP (OnHand - Reserved).
     * Nếu thiếu tồn kho: chuyển sang BACKORDER hoặc báo lỗi tùy thuộc allowBackorder.
     */
    @Transactional
    public SalesOrderResponse create(CreateSalesOrderRequest request, String username) {
        if (request.status() != null && request.status() != OrderStatus.DRAFT && request.status() != OrderStatus.CONFIRMED) {
            throw ApiException.badRequest("Trạng thái khởi tạo đơn bán chỉ chấp nhận DRAFT hoặc CONFIRMED");
        }

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> ApiException.badRequest("Không tìm thấy khách hàng với ID: " + request.customerId()));

        if (!customer.isActive()) {
            throw ApiException.badRequest("Khách hàng " + customer.getCode() + " đang ngừng hoạt động, không thể tạo đơn bán");
        }

        String warehouseName = request.warehouseName();
        if (request.warehouseId() != null) {
            WarehouseSummary wh = inventoryGateway.requireActiveWarehouse(request.warehouseId());
            warehouseName = wh.name();
        }

        String soNumber;
        if (request.soNumber() != null && !request.soNumber().isBlank()) {
            soNumber = request.soNumber().trim().toUpperCase();
            if (salesOrderRepository.existsBySoNumber(soNumber)) {
                throw ApiException.conflict("Mã đơn bán hàng '" + soNumber + "' đã tồn tại trong hệ thống");
            }
        } else {
            soNumber = generateNextSoNumber();
        }

        SalesOrder so = new SalesOrder();
        so.setSoNumber(soNumber);
        so.setCustomer(customer);
        so.setWarehouseId(request.warehouseId());
        so.setWarehouseName(normalizeString(warehouseName));
        so.setDeliveryAddress(normalizeString(request.deliveryAddress()));
        so.setRecipientName(normalizeString(request.recipientName()));
        so.setRecipientPhone(normalizeString(request.recipientPhone()));
        so.setOrderDate(request.orderDate() != null ? request.orderDate() : LocalDate.now());
        so.setExpectedDate(request.expectedDate());
        so.setNote(normalizeString(request.note()));

        // Tính tiền và kiểm tra ATP từng dòng hàng
        List<String> backorderReasons = new ArrayList<>();
        applyItemsAndCalculateTotals(so, request.items(), request.warehouseId(), request.isAllowBackorder(), backorderReasons);

        // Kiểm tra hạn mức công nợ khách hàng (FR14 - WMS-08.2)
        CustomerCreditCheckResponse creditCheck = customerService.checkCredit(customer.getId(), so.getTotalAmount());
        if (!creditCheck.canProceed()) {
            throw ApiException.badRequest(creditCheck.message());
        }

        // Xác định trạng thái SO
        if (request.status() == OrderStatus.DRAFT) {
            so.setStatus(OrderStatus.DRAFT);
        } else if (!backorderReasons.isEmpty()) {
            so.setStatus(OrderStatus.BACKORDER);
            so.setBackorderReason(String.join("; ", backorderReasons));
            log.info("Đơn bán hàng {} chuyển sang BACKORDER do thiếu tồn khả dụng: {}", soNumber, so.getBackorderReason());
        } else {
            so.setStatus(OrderStatus.CONFIRMED);
            so.setConfirmedBy(username != null ? username : "system");
            so.setConfirmedAt(Instant.now());
        }

        SalesOrder saved = salesOrderRepository.save(so);

        // Nếu đơn được CONFIRMED ngay lập tức, phát sự kiện order.created sang RabbitMQ
        if (saved.getStatus() == OrderStatus.CONFIRMED) {
            publishOrderCreatedEvent(saved);
        }

        return SalesOrderResponse.from(saved);
    }

    /**
     * Cập nhật đơn bán hàng (chỉ khi đang ở trạng thái DRAFT hoặc BACKORDER).
     */
    @Transactional
    public SalesOrderResponse update(Long id, UpdateSalesOrderRequest request, String username) {
        SalesOrder so = findOrder(id);

        if (so.getStatus() != OrderStatus.DRAFT && so.getStatus() != OrderStatus.BACKORDER) {
            throw ApiException.badRequest("Chỉ được chỉnh sửa đơn bán hàng khi đang ở trạng thái DRAFT hoặc BACKORDER");
        }

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> ApiException.badRequest("Không tìm thấy khách hàng với ID: " + request.customerId()));

        if (!customer.isActive()) {
            throw ApiException.badRequest("Khách hàng " + customer.getCode() + " đang ngừng hoạt động");
        }

        String warehouseName = request.warehouseName();
        if (request.warehouseId() != null) {
            WarehouseSummary wh = inventoryGateway.requireActiveWarehouse(request.warehouseId());
            warehouseName = wh.name();
        }

        so.setCustomer(customer);
        so.setWarehouseId(request.warehouseId());
        so.setWarehouseName(normalizeString(warehouseName));
        so.setDeliveryAddress(normalizeString(request.deliveryAddress()));
        so.setRecipientName(normalizeString(request.recipientName()));
        so.setRecipientPhone(normalizeString(request.recipientPhone()));
        so.setOrderDate(request.orderDate());
        so.setExpectedDate(request.expectedDate());
        so.setNote(normalizeString(request.note()));

        so.clearItems();
        List<String> backorderReasons = new ArrayList<>();
        applyItemsAndCalculateTotals(so, request.items(), request.warehouseId(), request.isAllowBackorder(), backorderReasons);

        CustomerCreditCheckResponse creditCheck = customerService.checkCredit(customer.getId(), so.getTotalAmount());
        if (!creditCheck.canProceed()) {
            throw ApiException.badRequest(creditCheck.message());
        }

        if (!backorderReasons.isEmpty()) {
            so.setStatus(OrderStatus.BACKORDER);
            so.setBackorderReason(String.join("; ", backorderReasons));
        } else if (so.getStatus() == OrderStatus.BACKORDER) {
            // Tồn kho đã đủ sau khi sửa dòng hàng
            so.setStatus(OrderStatus.CONFIRMED);
            so.setBackorderReason(null);
            so.setConfirmedBy(username != null ? username : "system");
            so.setConfirmedAt(Instant.now());
            publishOrderCreatedEvent(so);
        }

        SalesOrder updated = salesOrderRepository.save(so);
        return SalesOrderResponse.from(updated);
    }

    /**
     * Xác nhận đơn bán hàng (DRAFT hoặc BACKORDER -> CONFIRMED).
     * Re-check tồn khả dụng ATP; nếu đủ hàng chuyển sang CONFIRMED và gửi sự kiện order.created sang RabbitMQ.
     */
    @Transactional
    public SalesOrderResponse confirm(Long id, String username) {
        SalesOrder so = findOrder(id);

        if (so.getStatus() != OrderStatus.DRAFT && so.getStatus() != OrderStatus.BACKORDER) {
            throw ApiException.badRequest("Không thể xác nhận đơn bán hàng đang ở trạng thái: " + so.getStatus());
        }

        // Re-check ATP cho toàn bộ dòng hàng
        List<String> shortages = new ArrayList<>();
        for (SalesOrderItem item : so.getItems()) {
            AtpResponse atp = inventoryGateway.getAtp(item.getProductId(), so.getWarehouseId());
            item.setOnHandQuantity(atp.onHandQuantity());
            item.setReservedQuantity(atp.reservedQuantity());
            item.setAtpQuantity(atp.atpQuantity());

            if (item.getQuantity().compareTo(atp.atpQuantity()) > 0) {
                item.setBackorder(true);
                shortages.add("SKU " + item.getSkuCode() + " thiếu " + item.getQuantity().subtract(atp.atpQuantity())
                        + " (yêu cầu " + item.getQuantity() + ", tồn ATP " + atp.atpQuantity() + ")");
            } else {
                item.setBackorder(false);
            }
        }

        if (!shortages.isEmpty()) {
            throw ApiException.badRequest("Không thể xác nhận đơn do thiếu tồn khả dụng: " + String.join("; ", shortages));
        }

        // Kiểm tra lại hạn mức tín dụng khách hàng
        CustomerCreditCheckResponse creditCheck = customerService.checkCredit(so.getCustomer().getId(), so.getTotalAmount());
        if (!creditCheck.canProceed()) {
            throw ApiException.badRequest(creditCheck.message());
        }

        so.setStatus(OrderStatus.CONFIRMED);
        so.setBackorderReason(null);
        so.setConfirmedBy(username != null ? username : "system");
        so.setConfirmedAt(Instant.now());

        SalesOrder confirmed = salesOrderRepository.save(so);
        publishOrderCreatedEvent(confirmed);

        return SalesOrderResponse.from(confirmed);
    }

    /**
     * Hủy đơn bán hàng (chuyển sang trạng thái CANCELLED).
     * Giải phóng tồn kho giữ chỗ (soft reservation) nếu đơn đã xác nhận trước đó (WMS-11.2).
     */
    @Transactional
    public SalesOrderResponse cancel(Long id) {
        SalesOrder so = findOrder(id);

        if (so.getStatus() == OrderStatus.COMPLETED) {
            throw ApiException.badRequest("Không thể hủy đơn bán hàng đã hoàn tất xuất kho (COMPLETED)");
        }
        if (so.getStatus() == OrderStatus.CANCELLED) {
            return SalesOrderResponse.from(so);
        }

        OrderStatus oldStatus = so.getStatus();
        so.setStatus(OrderStatus.CANCELLED);
        SalesOrder cancelled = salesOrderRepository.save(so);

        // Nếu đơn từng ở trạng thái CONFIRMED (đã giữ hàng ảo), giải phóng giữ chỗ bên inventory-service (WMS-11.2)
        if (oldStatus == OrderStatus.CONFIRMED) {
            inventoryGateway.releaseReservations(so.getId());
        }

        return SalesOrderResponse.from(cancelled);
    }

    /**
     * Xóa đơn bán hàng (chỉ khi đang ở trạng thái DRAFT hoặc CANCELLED).
     */
    @Transactional
    public void delete(Long id) {
        SalesOrder so = findOrder(id);
        if (so.getStatus() != OrderStatus.DRAFT && so.getStatus() != OrderStatus.CANCELLED) {
            throw ApiException.badRequest("Chỉ được xóa đơn bán hàng khi đang ở trạng thái DRAFT hoặc CANCELLED");
        }
        salesOrderRepository.delete(so);
    }

    /**
     * Báo cáo thống kê tổng quan các chỉ số đơn bán hàng (SO Metrics Summary).
     * Bao gồm chỉ số 'Pending Sale Orders' (đơn bán chờ xử lý - FR16).
     */
    @Transactional(readOnly = true)
    public SalesOrderSummaryResponse getSummary() {
        long totalOrders = salesOrderRepository.count();
        long draftOrders = salesOrderRepository.countByStatus(OrderStatus.DRAFT);
        long backorders = salesOrderRepository.countByStatus(OrderStatus.BACKORDER);
        long confirmedOrders = salesOrderRepository.countByStatus(OrderStatus.CONFIRMED);
        long pendingSaleOrders = confirmedOrders; // Chỉ số 'Pending Sale Orders' (FR16)
        long completedOrders = salesOrderRepository.countByStatus(OrderStatus.COMPLETED);
        long cancelledOrders = salesOrderRepository.countByStatus(OrderStatus.CANCELLED);

        BigDecimal revenue = salesOrderRepository.sumTotalAmountByStatuses(List.of(OrderStatus.CONFIRMED, OrderStatus.COMPLETED));

        return new SalesOrderSummaryResponse(
                totalOrders,
                draftOrders,
                backorders,
                confirmedOrders,
                pendingSaleOrders,
                completedOrders,
                cancelledOrders,
                revenue != null ? revenue : BigDecimal.ZERO
        );
    }

    /**
     * Tự động sinh mã SO tuần tự theo ngày: SO-YYYYMMDD-XXXX.
     */
    private String generateNextSoNumber() {
        String todayStr = LocalDate.now().format(DATE_FORMATTER);
        String prefix = "SO-" + todayStr + "-";

        AtomicInteger seq = dailySequences.computeIfAbsent(todayStr, k -> {
            Optional<SalesOrder> latest = salesOrderRepository
                    .findTopBySoNumberStartingWithOrderBySoNumberDesc(prefix);
            if (latest.isPresent()) {
                String latestNumber = latest.get().getSoNumber();
                try {
                    String seqStr = latestNumber.substring(prefix.length());
                    return new AtomicInteger(Integer.parseInt(seqStr));
                } catch (Exception ignored) {
                }
            }
            return new AtomicInteger(0);
        });

        int nextVal = seq.incrementAndGet();
        String candidate = String.format("%s%04d", prefix, nextVal);
        while (salesOrderRepository.existsBySoNumber(candidate)) {
            nextVal = seq.incrementAndGet();
            candidate = String.format("%s%04d", prefix, nextVal);
        }
        return candidate;
    }

    /**
     * Tính toán thành tiền từng dòng, VAT, chiết khấu và kiểm tra ATP tồn khả dụng.
     */
    private void applyItemsAndCalculateTotals(
            SalesOrder so,
            List<SalesOrderItemRequest> itemRequests,
            Long warehouseId,
            boolean allowBackorder,
            List<String> backorderReasons
    ) {
        if (itemRequests == null || itemRequests.isEmpty()) {
            throw ApiException.badRequest("Đơn bán hàng phải có ít nhất 1 dòng mặt hàng");
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (SalesOrderItemRequest req : itemRequests) {
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

            // 5. Kiểm tra Tồn khả dụng ATP (FR15)
            AtpResponse atp = inventoryGateway.getAtp(req.productId(), warehouseId);
            boolean isDeficient = qty.compareTo(atp.atpQuantity()) > 0;

            if (isDeficient) {
                if (!allowBackorder) {
                    throw ApiException.badRequest(
                            "Sản phẩm '" + req.skuCode() + "' không đủ tồn khả dụng (Yêu cầu: " + qty
                            + ", ATP: " + atp.atpQuantity() + "). Đơn hàng không cho phép chuyển sang BACKORDER!"
                    );
                }
                backorderReasons.add("SKU " + req.skuCode() + " thiếu " + qty.subtract(atp.atpQuantity())
                        + " (yêu cầu " + qty + ", tồn ATP " + atp.atpQuantity() + ")");
            }

            SalesOrderItem item = new SalesOrderItem();
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
            item.setOnHandQuantity(atp.onHandQuantity());
            item.setReservedQuantity(atp.reservedQuantity());
            item.setAtpQuantity(atp.atpQuantity());
            item.setBackorder(isDeficient);
            item.setNote(normalizeString(req.note()));

            so.addItem(item);

            subtotal = subtotal.add(rawAmount);
            totalDiscount = totalDiscount.add(discountAmount);
            totalTax = totalTax.add(taxAmount);
            grandTotal = grandTotal.add(lineTotal);
        }

        so.setSubtotalAmount(subtotal);
        so.setDiscountAmount(totalDiscount);
        so.setTaxAmount(totalTax);
        so.setTotalAmount(grandTotal);
    }

    private void publishOrderCreatedEvent(SalesOrder so) {
        if (wmsEventPublisher == null) {
            log.info("WmsEventPublisher không khả dụng, bỏ qua gửi sự kiện '{}' cho đơn bán {}",
                    WmsEvents.RoutingKeys.ORDER_CREATED, so.getSoNumber());
            return;
        }

        List<OrderLine> lines = so.getItems().stream()
                .map(i -> new OrderLine(
                        i.getProductId(),
                        i.getSkuCode(),
                        i.getProductName(),
                        i.getQuantity(),
                        i.getUom(),
                        i.getUnitPrice()
                ))
                .toList();

        OrderCreatedEvent event = new OrderCreatedEvent(
                so.getId(),
                so.getSoNumber(),
                OrderType.SALES,
                so.getStatus().name(),
                null, // supplierId null for SO
                so.getCustomer() != null ? so.getCustomer().getId() : null,
                so.getWarehouseId(),
                so.getWarehouseName(),
                so.getTotalAmount(),
                so.getConfirmedBy() != null ? so.getConfirmedBy() : "system",
                so.getCreatedAt() != null ? so.getCreatedAt() : Instant.now(),
                lines
        );

        try {
            wmsEventPublisher.publishAfterCommit(WmsEvents.RoutingKeys.ORDER_CREATED, event);
            log.info("Đã gửi sự kiện '{}' sau commit cho đơn bán hàng {}",
                    WmsEvents.RoutingKeys.ORDER_CREATED, so.getSoNumber());
        } catch (Exception ex) {
            log.warn("Không thể gửi sự kiện '{}' cho đơn bán hàng {}: {}",
                    WmsEvents.RoutingKeys.ORDER_CREATED, so.getSoNumber(), ex.getMessage());
        }
    }

    private SalesOrder findOrder(Long id) {
        return salesOrderRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn bán hàng với ID: " + id));
    }

    private String normalizeString(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        return val.trim();
    }
}
