package vn.wms.order.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.PageResponse;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.OrderDispatchedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.common.messaging.event.OrderType;
import vn.wms.order.client.InventoryGateway;
import vn.wms.order.dto.CreateGdnItemRequest;
import vn.wms.order.dto.CreateGdnRequest;
import vn.wms.order.dto.GdnItemResponse;
import vn.wms.order.dto.GdnResponse;
import vn.wms.order.dto.GdnSummaryResponse;
import vn.wms.order.dto.GeneratePickingListRequest;
import vn.wms.order.dto.PickingItemDemand;
import vn.wms.order.dto.PickingListResponse;
import vn.wms.order.entity.Customer;
import vn.wms.order.entity.GoodsDeliveryNote;
import vn.wms.order.entity.GoodsDeliveryNoteItem;
import vn.wms.order.entity.GoodsDeliveryStatus;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.SalesOrder;
import vn.wms.order.entity.SalesOrderItem;
import vn.wms.order.repository.GoodsDeliveryNoteItemRepository;
import vn.wms.order.repository.GoodsDeliveryNoteRepository;
import vn.wms.order.repository.SalesOrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service Quản lý Phiếu xuất kho (GDN) & Điều phối xuất hàng (SO -> GDN, FR17 - WMS-12.1).
 */
@Service
public class GoodsDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(GoodsDeliveryService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final GoodsDeliveryNoteRepository gdnRepository;
    private final GoodsDeliveryNoteItemRepository gdnItemRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final InventoryGateway inventoryGateway;

    @Autowired(required = false)
    private WmsEventPublisher wmsEventPublisher;

    private final DocumentNumberService documentNumberService;

    public GoodsDeliveryService(GoodsDeliveryNoteRepository gdnRepository,
                                GoodsDeliveryNoteItemRepository gdnItemRepository,
                                SalesOrderRepository salesOrderRepository,
                                InventoryGateway inventoryGateway,
                                DocumentNumberService documentNumberService) {
        this.gdnRepository = gdnRepository;
        this.gdnItemRepository = gdnItemRepository;
        this.salesOrderRepository = salesOrderRepository;
        this.inventoryGateway = inventoryGateway;
        this.documentNumberService = documentNumberService;
    }

    /**
     * Lập mới Phiếu xuất kho (GDN) từ Đơn bán hàng SO (WMS-12.1 - FR17).
     * Hỗ trợ xuất một phần (Partial Delivery) hoặc toàn bộ đơn (Full Delivery).
     * Khi phiếu ở trạng thái COMPLETED, tự động phát sự kiện {@code order.dispatched} sang RabbitMQ
     * để inventory-service trừ OnHand với Khóa bi quan và giải phóng Reserved.
     */
    @Transactional
    public GdnResponse create(CreateGdnRequest request, String username) {
        if (request == null) {
            throw ApiException.badRequest("Yêu cầu lập phiếu xuất kho không được để trống");
        }
        if (request.soId() == null) {
            throw ApiException.badRequest("soId không được để trống");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw ApiException.badRequest("Phiếu xuất kho phải có ít nhất 1 dòng mặt hàng");
        }

        // Phiếu nháp/đang lấy hàng cũng cộng số lượng đã xuất của dòng đơn ngay khi lập, nên phiếu không được
        // lập thẳng ở trạng thái CANCELLED: nếu không, số lượng đó bị giữ mãi mà hàng không hề rời kho.
        if (request.status() == GoodsDeliveryStatus.CANCELLED) {
            throw ApiException.badRequest("Không thể lập phiếu xuất kho ở trạng thái CANCELLED");
        }

        // Khóa bi quan đơn bán: hai phiếu xuất đồng thời (bấm hai lần, hai người cùng lập) phải xếp hàng,
        // phiếu sau đọc số lượng đã xuất mới nhất nên không thể xuất vượt số lượng đặt và trừ tồn hai lần.
        SalesOrder so = salesOrderRepository.findByIdForUpdate(request.soId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn bán hàng với ID: " + request.soId()));

        if (so.getStatus() == OrderStatus.DRAFT) {
            throw ApiException.badRequest("Đơn bán hàng " + so.getSoNumber() + " đang ở trạng thái DRAFT, phải được xác nhận trước khi xuất kho");
        }
        if (so.getStatus() == OrderStatus.CANCELLED) {
            throw ApiException.badRequest("Đơn bán hàng " + so.getSoNumber() + " đã bị hủy, không thể xuất kho");
        }
        if (so.getStatus() == OrderStatus.COMPLETED) {
            throw ApiException.badRequest("Đơn bán hàng " + so.getSoNumber() + " đã hoàn tất xuất kho (COMPLETED)");
        }

        Map<Long, SalesOrderItem> soItemMap = so.getItems().stream()
                .collect(Collectors.toMap(SalesOrderItem::getId, item -> item));

        GoodsDeliveryNote gdn = new GoodsDeliveryNote();
        gdn.setGdnNumber(generateNextGdnNumber());
        gdn.setSalesOrder(so);
        gdn.setSoNumber(so.getSoNumber());
        gdn.setCustomer(so.getCustomer());
        gdn.setCustomerName(so.getCustomer() != null ? so.getCustomer().getName() : "Khách vãng lai");
        gdn.setWarehouseId(so.getWarehouseId());
        gdn.setWarehouseName(so.getWarehouseName());
        gdn.setDeliveryDate(request.deliveryDate() != null ? request.deliveryDate() : LocalDate.now());

        GoodsDeliveryStatus status = request.status() != null ? request.status() : GoodsDeliveryStatus.COMPLETED;
        gdn.setStatus(status);

        gdn.setRecipientName(normalizeString(request.recipientName() != null ? request.recipientName() : so.getRecipientName()));
        gdn.setRecipientPhone(normalizeString(request.recipientPhone() != null ? request.recipientPhone() : so.getRecipientPhone()));
        gdn.setDeliveryAddress(normalizeString(request.deliveryAddress() != null ? request.deliveryAddress() : so.getDeliveryAddress()));
        gdn.setNote(normalizeString(request.note()));

        String operator = (username != null && !username.isBlank()) ? username.trim() : "system";
        if (status == GoodsDeliveryStatus.COMPLETED) {
            gdn.setDispatchedBy(operator);
            gdn.setDispatchedAt(Instant.now());
        }

        BigDecimal totalQuantity = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CreateGdnItemRequest itemReq : request.items()) {
            SalesOrderItem soItem = soItemMap.get(itemReq.soItemId());
            if (soItem == null) {
                throw ApiException.badRequest("Dòng mặt hàng ID " + itemReq.soItemId() + " không thuộc đơn bán " + so.getSoNumber());
            }

            BigDecimal deliveredQty = itemReq.deliveredQuantity() != null ? itemReq.deliveredQuantity() : BigDecimal.ZERO;
            if (deliveredQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("Số lượng xuất của sản phẩm '" + soItem.getSkuCode() + "' phải lớn hơn 0");
            }

            BigDecimal currentDelivered = soItem.getDeliveredQuantity() != null ? soItem.getDeliveredQuantity() : BigDecimal.ZERO;
            BigDecimal remainingToDeliver = soItem.getQuantity().subtract(currentDelivered).max(BigDecimal.ZERO);

            if (deliveredQty.compareTo(remainingToDeliver) > 0) {
                throw ApiException.badRequest(String.format(
                        "Số lượng xuất của sản phẩm '%s' (%s) vượt quá số lượng còn lại cần giao (%s) của đơn %s",
                        soItem.getSkuCode(), deliveredQty, remainingToDeliver, so.getSoNumber()));
            }

            // Cập nhật số lượng đã xuất trên dòng đơn bán hàng
            soItem.setDeliveredQuantity(currentDelivered.add(deliveredQty));

            BigDecimal unitPrice = itemReq.unitPrice() != null ? itemReq.unitPrice() : soItem.getUnitPrice();
            BigDecimal lineTotal = deliveredQty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

            GoodsDeliveryNoteItem gdnItem = new GoodsDeliveryNoteItem();
            gdnItem.setSalesOrderItem(soItem);
            gdnItem.setProductId(soItem.getProductId());
            gdnItem.setSkuCode(soItem.getSkuCode());
            gdnItem.setProductName(soItem.getProductName());
            gdnItem.setUom(soItem.getUom());
            gdnItem.setOrderedQuantity(soItem.getQuantity());
            gdnItem.setDeliveredQuantity(deliveredQty);
            gdnItem.setUnitPrice(unitPrice);
            gdnItem.setLineTotal(lineTotal);
            gdnItem.setBatchNumber(normalizeString(itemReq.batchNumber()));
            gdnItem.setLocationId(itemReq.locationId());
            gdnItem.setLocationCode(normalizeString(itemReq.locationCode()));
            gdnItem.setNote(normalizeString(itemReq.note()));

            gdn.addItem(gdnItem);

            totalQuantity = totalQuantity.add(deliveredQty);
            totalAmount = totalAmount.add(lineTotal);
        }

        gdn.setTotalQuantity(totalQuantity);
        gdn.setTotalAmount(totalAmount);

        // Kiểm tra xem đơn hàng đã xuất hết 100% chưa
        boolean isAllDelivered = so.getItems().stream()
                .allMatch(it -> it.getDeliveredQuantity() != null
                        && it.getDeliveredQuantity().compareTo(it.getQuantity()) >= 0);

        if (status == GoodsDeliveryStatus.COMPLETED && isAllDelivered) {
            so.setStatus(OrderStatus.COMPLETED);
        }

        salesOrderRepository.save(so);
        GoodsDeliveryNote saved = gdnRepository.save(gdn);

        log.info("Đã lập thành công Phiếu xuất kho '{}' cho đơn '{}' (SL: {}, tiền: {} VNĐ, trạng thái: {})",
                saved.getGdnNumber(), so.getSoNumber(), totalQuantity, totalAmount, saved.getStatus());

        // Phát sự kiện order.dispatched nếu hoàn tất xuất kho
        if (saved.getStatus() == GoodsDeliveryStatus.COMPLETED) {
            publishOrderDispatchedEvent(saved);
        }

        return GdnResponse.from(saved);
    }

    /**
     * Xác nhận xuất xưởng (Dispatch) đối với phiếu xuất kho đang ở trạng thái DRAFT hoặc PICKING.
     */
    @Transactional
    public GdnResponse dispatch(Long id, String username) {
        // Khóa bi quan phiếu xuất: lần xác nhận thứ hai chờ lần đầu commit rồi thấy COMPLETED và trả về luôn,
        // không phát thêm sự kiện order.dispatched (mỗi sự kiện là một lần trừ tồn bên inventory-service).
        GoodsDeliveryNote gdn = gdnRepository.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy Phiếu xuất kho với ID: " + id));

        if (gdn.getStatus() == GoodsDeliveryStatus.COMPLETED) {
            return GdnResponse.from(gdn);
        }
        if (gdn.getStatus() == GoodsDeliveryStatus.CANCELLED) {
            throw ApiException.badRequest("Không thể xuất kho phiếu đã bị hủy (" + gdn.getGdnNumber() + ")");
        }

        gdn.setStatus(GoodsDeliveryStatus.COMPLETED);
        gdn.setDispatchedBy((username != null && !username.isBlank()) ? username.trim() : "system");
        gdn.setDispatchedAt(Instant.now());

        // Kiểm tra và cập nhật trạng thái đơn SO nếu đã giao đủ
        SalesOrder so = gdn.getSalesOrder();
        if (so != null) {
            boolean isAllDelivered = so.getItems().stream()
                    .allMatch(it -> it.getDeliveredQuantity() != null
                            && it.getDeliveredQuantity().compareTo(it.getQuantity()) >= 0);
            if (isAllDelivered) {
                so.setStatus(OrderStatus.COMPLETED);
                salesOrderRepository.save(so);
            }
        }

        GoodsDeliveryNote saved = gdnRepository.save(gdn);
        publishOrderDispatchedEvent(saved);

        return GdnResponse.from(saved);
    }

    /**
     * Tự động sinh danh sách lấy hàng (Picking List) gợi ý vị trí ô kệ và lô hàng cho Đơn bán hàng (WMS-12.1).
     */
    @Transactional(readOnly = true)
    public PickingListResponse generatePickingListForSo(Long soId) {
        SalesOrder so = salesOrderRepository.findById(soId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn bán hàng với ID: " + soId));

        if (so.getWarehouseId() == null) {
            throw ApiException.badRequest("Đơn bán hàng " + so.getSoNumber() + " chưa được chỉ định kho xuất hàng");
        }

        List<PickingItemDemand> demands = so.getItems().stream()
                .map(item -> {
                    BigDecimal remaining = item.getQuantity()
                            .subtract(item.getDeliveredQuantity() != null ? item.getDeliveredQuantity() : BigDecimal.ZERO)
                            .max(BigDecimal.ZERO);
                    return new PickingItemDemand(
                            item.getProductId(),
                            item.getSkuCode(),
                            item.getProductName(),
                            item.getUom(),
                            remaining
                    );
                })
                .filter(d -> d.quantity().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (demands.isEmpty()) {
            throw ApiException.badRequest("Đơn bán hàng " + so.getSoNumber() + " đã hoàn tất xuất kho toàn bộ các dòng hàng");
        }

        GeneratePickingListRequest req = new GeneratePickingListRequest(
                so.getId(),
                so.getSoNumber(),
                so.getWarehouseId(),
                demands
        );

        return inventoryGateway.generatePickingList(req);
    }

    @Transactional(readOnly = true)
    public GdnResponse getById(Long id) {
        return GdnResponse.from(findGdn(id));
    }

    @Transactional(readOnly = true)
    public GdnResponse getByGdnNumber(String gdnNumber) {
        GoodsDeliveryNote gdn = gdnRepository.findByGdnNumber(gdnNumber.trim())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy Phiếu xuất kho với mã: " + gdnNumber));
        return GdnResponse.from(gdn);
    }

    @Transactional(readOnly = true)
    public List<GdnResponse> listBySoId(Long soId) {
        return gdnRepository.findBySalesOrderIdOrderByIdDesc(soId).stream()
                .map(GdnResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<GdnResponse> search(
            String keyword,
            GoodsDeliveryStatus status,
            Long customerId,
            Long warehouseId,
            Long soId,
            LocalDate fromDeliveryDate,
            LocalDate toDeliveryDate,
            Pageable pageable
    ) {
        String trimmed = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return PageResponse.from(gdnRepository.search(
                trimmed, status, customerId, warehouseId, soId, fromDeliveryDate, toDeliveryDate, pageable
        ).map(GdnResponse::from));
    }

    @Transactional(readOnly = true)
    public GdnSummaryResponse getSummary() {
        long total = gdnRepository.count();
        long draft = gdnRepository.countByStatus(GoodsDeliveryStatus.DRAFT);
        long picking = gdnRepository.countByStatus(GoodsDeliveryStatus.PICKING);
        long completed = gdnRepository.countByStatus(GoodsDeliveryStatus.COMPLETED);
        long cancelled = gdnRepository.countByStatus(GoodsDeliveryStatus.CANCELLED);

        BigDecimal totalQty = gdnRepository.sumTotalQuantityByStatus(GoodsDeliveryStatus.COMPLETED);
        BigDecimal totalAmount = gdnRepository.sumTotalAmountByStatus(GoodsDeliveryStatus.COMPLETED);

        return new GdnSummaryResponse(
                total,
                draft,
                picking,
                completed,
                cancelled,
                totalQty != null ? totalQty : BigDecimal.ZERO,
                totalAmount != null ? totalAmount : BigDecimal.ZERO
        );
    }

    private void publishOrderDispatchedEvent(GoodsDeliveryNote gdn) {
        if (wmsEventPublisher == null) {
            log.info("WmsEventPublisher không khả dụng, bỏ qua gửi sự kiện '{}' cho GDN {}",
                    WmsEvents.RoutingKeys.ORDER_DISPATCHED, gdn.getGdnNumber());
            return;
        }

        List<OrderLine> lines = gdn.getItems().stream()
                .map(i -> new OrderLine(
                        i.getProductId(),
                        i.getSkuCode(),
                        i.getProductName(),
                        i.getDeliveredQuantity(),
                        i.getUom(),
                        i.getUnitPrice()
                ))
                .toList();

        OrderDispatchedEvent event = new OrderDispatchedEvent(
                gdn.getSalesOrder() != null ? gdn.getSalesOrder().getId() : null,
                gdn.getSoNumber(),
                OrderType.SALES,
                gdn.getWarehouseId(),
                gdn.getDispatchedBy() != null ? gdn.getDispatchedBy() : "system",
                gdn.getDispatchedAt() != null ? gdn.getDispatchedAt() : Instant.now(),
                lines
        );

        try {
            wmsEventPublisher.publishAfterCommit(WmsEvents.RoutingKeys.ORDER_DISPATCHED, event);
            log.info("Đã gửi sự kiện '{}' sau commit cho phiếu xuất kho {}",
                    WmsEvents.RoutingKeys.ORDER_DISPATCHED, gdn.getGdnNumber());
        } catch (Exception ex) {
            log.warn("Không thể gửi sự kiện '{}' cho phiếu xuất kho {}: {}",
                    WmsEvents.RoutingKeys.ORDER_DISPATCHED, gdn.getGdnNumber(), ex.getMessage());
        }
    }

    /** Sinh mã phiếu xuất GDN-YYYYMMDD-XXXX, đánh số riêng cho từng doanh nghiệp (WMS-22.4b). */
    private String generateNextGdnNumber() {
        return documentNumberService.next("GDN-" + LocalDate.now().format(DATE_FORMATTER) + "-",
                prefix -> gdnRepository.findTopByGdnNumberStartingWithOrderByGdnNumberDesc(prefix)
                        .map(GoodsDeliveryNote::getGdnNumber),
                gdnRepository::existsByGdnNumber);
    }

    private GoodsDeliveryNote findGdn(Long id) {
        return gdnRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy Phiếu xuất kho với ID: " + id));
    }

    private String normalizeString(String val) {
        if (val == null || val.isBlank()) return null;
        return val.trim();
    }
}
