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
import vn.wms.common.messaging.event.OrderReceivedEvent;
import vn.wms.common.messaging.event.ReceivedItemLine;
import vn.wms.order.dto.CreateGrnItemRequest;
import vn.wms.order.dto.CreateGrnRequest;
import vn.wms.order.dto.GrnResponse;
import vn.wms.order.dto.GrnSummaryResponse;
import vn.wms.order.dto.PoReceiptProgressResponse;
import vn.wms.order.entity.GoodsReceiptNote;
import vn.wms.order.entity.GoodsReceiptNoteItem;
import vn.wms.order.entity.GoodsReceiptStatus;
import vn.wms.order.entity.OrderStatus;
import vn.wms.order.entity.PurchaseOrder;
import vn.wms.order.entity.PurchaseOrderItem;
import vn.wms.order.repository.GoodsReceiptNoteRepository;
import vn.wms.order.repository.PurchaseOrderRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service Quản lý Phiếu nhập kho (Goods Receipt Note - GRN) & Nhập từng phần (FR11 - WMS-10.1).
 */
@Service
public class GoodsReceiptService {

    private static final Logger log = LoggerFactory.getLogger(GoodsReceiptService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final GoodsReceiptNoteRepository goodsReceiptNoteRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseBillService purchaseBillService;

    @Autowired(required = false)
    private WmsEventPublisher wmsEventPublisher;

    private final DocumentNumberService documentNumberService;

    public GoodsReceiptService(GoodsReceiptNoteRepository goodsReceiptNoteRepository,
                               PurchaseOrderRepository purchaseOrderRepository,
                               PurchaseBillService purchaseBillService,
                               DocumentNumberService documentNumberService) {
        this.goodsReceiptNoteRepository = goodsReceiptNoteRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.purchaseBillService = purchaseBillService;
        this.documentNumberService = documentNumberService;
    }

    /**
     * Lập phiếu nhập kho (GRN) theo Đơn mua hàng PO (FR11 - WMS-10.1).
     * Hỗ trợ nhập đủ 100% hoặc nhập từng đợt (Partial Receipt), ghi nhận số lượng đạt (Accepted) và hỏng (Rejected).
     */
    @Transactional
    public GrnResponse create(CreateGrnRequest request, String receivedBy) {
        if (request.poId() == null) {
            throw ApiException.badRequest("ID đơn mua hàng (PO) không được để trống");
        }

        // Khóa bi quan (SELECT ... FOR UPDATE) trên đơn mua hàng để chống race condition khi 2 phiếu nhập tạo đồng thời
        PurchaseOrder po = purchaseOrderRepository.findByIdForUpdate(request.poId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn mua hàng với ID: " + request.poId()));

        // 1. Kiểm tra trạng thái đơn mua hàng: Chỉ cho phép nhập khi APPROVED hoặc PARTIALLY_RECEIVED
        if (po.getStatus() != OrderStatus.APPROVED && po.getStatus() != OrderStatus.PARTIALLY_RECEIVED) {
            throw ApiException.badRequest("Đơn mua hàng '" + po.getPoNumber() + "' đang ở trạng thái " + po.getStatus()
                    + ". Chỉ được lập phiếu nhập kho cho đơn mua đã được duyệt (APPROVED) hoặc đang nhập dở dang (PARTIALLY_RECEIVED)!");
        }

        if (po.getItems() == null || po.getItems().isEmpty()) {
            throw ApiException.badRequest("Đơn mua hàng không có danh sách dòng mặt hàng để nhập kho");
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw ApiException.badRequest("Phiếu nhập kho phải có ít nhất một dòng mặt hàng thực nhận");
        }

        Map<Long, PurchaseOrderItem> poItemMap = po.getItems().stream()
                .collect(Collectors.toMap(PurchaseOrderItem::getId, Function.identity()));

        if (po.getWarehouseId() == null) {
            throw ApiException.badRequest("Đơn mua hàng #" + po.getPoNumber() + " chưa được chỉ định kho nhận hàng (warehouseId), không thể lập phiếu nhập");
        }

        LocalDate receiptDate = request.receiptDate() != null ? request.receiptDate() : LocalDate.now();

        GoodsReceiptNote grn = new GoodsReceiptNote();
        grn.setGrnNumber(generateGrnNumber(receiptDate));
        grn.setPurchaseOrder(po);
        grn.setPoNumber(po.getPoNumber());
        grn.setSupplier(po.getSupplier());
        grn.setSupplierName(po.getSupplier() != null ? po.getSupplier().getName() : null);
        grn.setWarehouseId(po.getWarehouseId());
        grn.setWarehouseName(po.getWarehouseName());
        grn.setStatus(GoodsReceiptStatus.COMPLETED);
        grn.setReceiptDate(receiptDate);
        grn.setNote(request.note() != null ? request.note().trim() : null);
        grn.setReceivedBy(receivedBy != null && !receivedBy.isBlank() ? receivedBy.trim() : "SYSTEM");

        BigDecimal totalAccepted = BigDecimal.ZERO;
        BigDecimal totalRejected = BigDecimal.ZERO;
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 2. Xử lý từng dòng mặt hàng nhập kho
        for (CreateGrnItemRequest itemReq : request.items()) {
            if (itemReq.poItemId() == null) {
                throw ApiException.badRequest("Mỗi dòng nhập kho phải có poItemId tương ứng");
            }

            PurchaseOrderItem poItem = poItemMap.get(itemReq.poItemId());
            if (poItem == null) {
                throw ApiException.badRequest("Dòng mặt hàng poItemId " + itemReq.poItemId() + " không thuộc đơn mua " + po.getPoNumber());
            }

            BigDecimal accepted = itemReq.acceptedQuantity() != null ? itemReq.acceptedQuantity() : BigDecimal.ZERO;
            BigDecimal rejected = itemReq.rejectedQuantity() != null ? itemReq.rejectedQuantity() : BigDecimal.ZERO;

            if (accepted.compareTo(BigDecimal.ZERO) < 0 || rejected.compareTo(BigDecimal.ZERO) < 0) {
                throw ApiException.badRequest("Số lượng thực nhận đạt chuẩn và từ chối không được âm");
            }

            BigDecimal lineReceived = accepted.add(rejected);
            if (lineReceived.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("Mặt hàng " + poItem.getSkuCode() + " phải có tổng số lượng nhận (Accepted + Rejected) lớn hơn 0");
            }

            // Kiểm tra không nhập vượt quá số lượng đặt còn lại trên PO
            BigDecimal remaining = poItem.getRemainingQuantity();
            if (accepted.compareTo(remaining) > 0) {
                throw ApiException.badRequest("Mặt hàng " + poItem.getSkuCode() + " có số lượng đạt (" + accepted
                        + ") vượt quá số lượng còn lại cần nhập trên PO (" + remaining + ")!");
            }

            // Quản lý Lô & Hạn sử dụng (WMS-10.2 - FR12): Kiểm tra tính hợp lệ của Số lô, Ngày SX & Hạn dùng
            String batchNumber = itemReq.batchNumber() != null ? itemReq.batchNumber().trim() : null;
            if (batchNumber != null && batchNumber.length() > 50) {
                throw ApiException.badRequest("Số lô (batchNumber) của mặt hàng " + poItem.getSkuCode() + " vượt quá độ dài tối đa 50 ký tự");
            }

            if (itemReq.mfgDate() != null && itemReq.expDate() != null && itemReq.expDate().isBefore(itemReq.mfgDate())) {
                throw ApiException.badRequest("Hạn sử dụng (" + itemReq.expDate() + ") không được trước ngày sản xuất ("
                        + itemReq.mfgDate() + ") của sản phẩm " + poItem.getSkuCode());
            }

            // Chỉ chặn hàng hết hạn khi có số lượng đạt chuẩn (accepted > 0).
            // Nếu accepted = 0 (toàn bộ rejected), cho phép ghi nhận phiếu nhập để lưu vết hàng hết hạn bị trả lại/từ chối nhận.
            if (accepted.compareTo(BigDecimal.ZERO) > 0 && itemReq.expDate() != null && !itemReq.expDate().isAfter(receiptDate)) {
                throw ApiException.badRequest("Lô hàng '" + (batchNumber != null ? batchNumber : poItem.getSkuCode())
                        + "' đã hết hạn sử dụng (" + itemReq.expDate() + ") tính đến ngày nhận hàng (" + receiptDate + "), không thể nhập kho đạt chuẩn");
            }

            BigDecimal unitPrice = poItem.getUnitPrice() != null ? poItem.getUnitPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = accepted.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);

            GoodsReceiptNoteItem grnItem = new GoodsReceiptNoteItem();
            grnItem.setGoodsReceiptNote(grn);
            grnItem.setPurchaseOrderItem(poItem);
            grnItem.setProductId(poItem.getProductId());
            grnItem.setSkuCode(poItem.getSkuCode());
            grnItem.setProductName(poItem.getProductName());
            grnItem.setUom(poItem.getUom());
            grnItem.setOrderedQuantity(poItem.getQuantity());
            grnItem.setAcceptedQuantity(accepted);
            grnItem.setRejectedQuantity(rejected);
            grnItem.setUnitPrice(unitPrice);
            grnItem.setLineTotal(lineTotal);
            grnItem.setRejectedReason(rejected.compareTo(BigDecimal.ZERO) > 0 && itemReq.rejectedReason() != null
                    ? itemReq.rejectedReason().trim() : null);
            grnItem.setBatchNumber(batchNumber);
            grnItem.setMfgDate(itemReq.mfgDate());
            grnItem.setExpDate(itemReq.expDate());
            grnItem.setLocationId(itemReq.locationId());
            grnItem.setLocationCode(itemReq.locationCode() != null ? itemReq.locationCode().trim() : null);
            grnItem.setNote(itemReq.note() != null ? itemReq.note().trim() : null);

            grn.addItem(grnItem);

            // Cập nhật số lượng tích lũy đã nhận vào po_items
            poItem.setReceivedQuantity(poItem.getReceivedQuantity().add(accepted));

            totalAccepted = totalAccepted.add(accepted);
            totalRejected = totalRejected.add(rejected);
            totalAmount = totalAmount.add(lineTotal);
        }

        grn.setTotalAcceptedQuantity(totalAccepted);
        grn.setTotalRejectedQuantity(totalRejected);
        grn.setTotalAmount(totalAmount);

        // 3. Cập nhật trạng thái của PO (COMPLETED nếu đã nhập đủ toàn bộ các mặt hàng, PARTIALLY_RECEIVED nếu còn dư)
        boolean allFullyReceived = po.getItems().stream()
                .allMatch(item -> item.getRemainingQuantity().compareTo(BigDecimal.ZERO) <= 0);

        if (allFullyReceived) {
            po.setStatus(OrderStatus.COMPLETED);
        } else {
            po.setStatus(OrderStatus.PARTIALLY_RECEIVED);
        }

        purchaseOrderRepository.save(po);
        GoodsReceiptNote saved = goodsReceiptNoteRepository.save(grn);

        // 4. Tự động tạo Hóa đơn mua (Purchase Bill) và cập nhật công nợ NCC (FR11, FR13 - WMS-10.3).
        // Không nuốt lỗi: hóa đơn và công nợ phải cùng commit hoặc cùng rollback với GRN (lỗi trong createFromGrn
        // đã đánh dấu transaction rollback-only, nuốt lỗi chỉ đổi thành UnexpectedRollbackException 500 lúc commit).
        purchaseBillService.createFromGrn(saved);

        // 5. Phát sự kiện order.received sau khi commit transaction (WMS-10.3)
        publishOrderReceivedEvent(saved);

        return GrnResponse.from(saved);
    }

    /**
     * Xác nhận phiếu nhập kho (chuyển từ DRAFT sang COMPLETED) nếu được lập ở dạng nháp.
     */
    @Transactional
    public GrnResponse confirm(Long grnId, String confirmedBy) {
        GoodsReceiptNote grn = goodsReceiptNoteRepository.findById(grnId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập kho với ID: " + grnId));

        if (grn.getStatus() == GoodsReceiptStatus.COMPLETED) {
            throw ApiException.badRequest("Phiếu nhập kho '" + grn.getGrnNumber() + "' đã được xác nhận trước đó!");
        }
        if (grn.getStatus() == GoodsReceiptStatus.CANCELLED) {
            throw ApiException.badRequest("Phiếu nhập kho '" + grn.getGrnNumber() + "' đã bị hủy, không thể xác nhận!");
        }

        grn.setStatus(GoodsReceiptStatus.COMPLETED);
        if (confirmedBy != null && !confirmedBy.isBlank()) {
            grn.setReceivedBy(confirmedBy.trim());
        }

        GoodsReceiptNote saved = goodsReceiptNoteRepository.save(grn);

        purchaseBillService.createFromGrn(saved);

        publishOrderReceivedEvent(saved);

        return GrnResponse.from(saved);
    }

    private void publishOrderReceivedEvent(GoodsReceiptNote grn) {
        if (wmsEventPublisher == null) {
            log.info("WmsEventPublisher không khả dụng, bỏ qua gửi sự kiện '{}' cho phiếu nhập {}",
                    WmsEvents.RoutingKeys.ORDER_RECEIVED, grn.getGrnNumber());
            return;
        }

        List<ReceivedItemLine> lines = grn.getItems().stream()
                .map(i -> new ReceivedItemLine(
                        i.getProductId(),
                        i.getSkuCode(),
                        i.getProductName(),
                        i.getAcceptedQuantity(),
                        i.getRejectedQuantity(),
                        i.getUom(),
                        i.getLocationId(),
                        i.getLocationCode(),
                        i.getBatchNumber(),
                        i.getMfgDate(),
                        i.getExpDate()
                ))
                .toList();

        OrderReceivedEvent event = new OrderReceivedEvent(
                grn.getId(),
                grn.getGrnNumber(),
                grn.getPurchaseOrder() != null ? grn.getPurchaseOrder().getId() : null,
                grn.getPoNumber(),
                grn.getWarehouseId(),
                grn.getSupplier() != null ? grn.getSupplier().getId() : null,
                grn.getReceivedBy(),
                grn.getReceiptDate(),
                Instant.now(),
                lines
        );

        try {
            wmsEventPublisher.publishAfterCommit(WmsEvents.RoutingKeys.ORDER_RECEIVED, event);
            log.info("Đã phát sự kiện '{}' sau commit cho phiếu nhập kho {}",
                    WmsEvents.RoutingKeys.ORDER_RECEIVED, grn.getGrnNumber());
        } catch (Exception ex) {
            log.warn("Không thể phát sự kiện '{}' cho phiếu nhập kho {}: {}",
                    WmsEvents.RoutingKeys.ORDER_RECEIVED, grn.getGrnNumber(), ex.getMessage());
        }
    }

    /**
     * Chi tiết phiếu nhập kho theo ID.
     */
    @Transactional(readOnly = true)
    public GrnResponse getById(Long id) {
        GoodsReceiptNote grn = goodsReceiptNoteRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập kho với ID: " + id));
        return GrnResponse.from(grn);
    }

    /**
     * Chi tiết phiếu nhập kho theo Mã GRN.
     */
    @Transactional(readOnly = true)
    public GrnResponse getByGrnNumber(String grnNumber) {
        GoodsReceiptNote grn = goodsReceiptNoteRepository.findByGrnNumber(grnNumber.trim())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy phiếu nhập kho với mã: " + grnNumber));
        return GrnResponse.from(grn);
    }

    /**
     * Danh sách các phiếu nhập kho đã lập theo một PO (theo dõi các đợt Partial Receipt).
     */
    @Transactional(readOnly = true)
    public List<GrnResponse> listByPoId(Long poId) {
        return goodsReceiptNoteRepository.findByPurchaseOrderIdOrderByIdDesc(poId).stream()
                .map(GrnResponse::from)
                .toList();
    }

    /**
     * Tra cứu tiến độ nhập kho của đơn mua hàng PO (đã nhận bao nhiêu, còn lại bao nhiêu).
     */
    @Transactional(readOnly = true)
    public PoReceiptProgressResponse getPoReceiptProgress(Long poId) {
        PurchaseOrder po = purchaseOrderRepository.findById(poId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy đơn mua hàng với ID: " + poId));
        return PoReceiptProgressResponse.from(po);
    }

    /**
     * Tìm kiếm và phân trang phiếu nhập kho.
     */
    @Transactional(readOnly = true)
    public PageResponse<GrnResponse> search(
            String keyword,
            GoodsReceiptStatus status,
            Long warehouseId,
            Long supplierId,
            Long poId,
            LocalDate fromReceiptDate,
            LocalDate toReceiptDate,
            Pageable pageable) {
        String trimmed = (keyword != null && !keyword.isBlank()) ? keyword.trim() : null;
        return PageResponse.from(goodsReceiptNoteRepository.search(
                trimmed, status, warehouseId, supplierId, poId, fromReceiptDate, toReceiptDate, pageable
        ).map(GrnResponse::from));
    }

    /**
     * Thống kê tổng quan tình hình nhập kho (GRN Metrics Summary).
     */
    @Transactional(readOnly = true)
    public GrnSummaryResponse getSummary() {
        long total = goodsReceiptNoteRepository.count();
        long completed = goodsReceiptNoteRepository.countByStatus(GoodsReceiptStatus.COMPLETED);
        long draft = goodsReceiptNoteRepository.countByStatus(GoodsReceiptStatus.DRAFT);
        long cancelled = goodsReceiptNoteRepository.countByStatus(GoodsReceiptStatus.CANCELLED);
        BigDecimal acceptedQty = goodsReceiptNoteRepository.sumTotalAcceptedQuantityByStatus(GoodsReceiptStatus.COMPLETED);
        BigDecimal rejectedQty = goodsReceiptNoteRepository.sumTotalRejectedQuantityByStatus(GoodsReceiptStatus.COMPLETED);
        BigDecimal amount = goodsReceiptNoteRepository.sumTotalAmountByStatus(GoodsReceiptStatus.COMPLETED);

        return new GrnSummaryResponse(
                total,
                completed,
                draft,
                cancelled,
                acceptedQty != null ? acceptedQty : BigDecimal.ZERO,
                rejectedQty != null ? rejectedQty : BigDecimal.ZERO,
                amount != null ? amount : BigDecimal.ZERO
        );
    }

    /**
     * Sinh mã chứng từ nhập kho: GRN-YYYYMMDD-XXXX (FR11, CONVENTIONS.md), đánh số riêng cho từng doanh nghiệp
     * (WMS-22.4b).
     */
    public String generateGrnNumber(LocalDate date) {
        return documentNumberService.next("GRN-" + date.format(DATE_FORMATTER) + "-",
                prefix -> goodsReceiptNoteRepository.findTopByGrnNumberStartingWithOrderByGrnNumberDesc(prefix)
                        .map(GoodsReceiptNote::getGrnNumber),
                goodsReceiptNoteRepository::existsByGrnNumber);
    }
}
