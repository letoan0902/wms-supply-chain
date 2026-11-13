package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.InventoryUpdatedEvent;
import vn.wms.inventory.dto.DeductStockItemRequest;
import vn.wms.inventory.dto.DeductStockRequest;
import vn.wms.inventory.dto.DeductStockResponse;
import vn.wms.inventory.dto.DeductedItemDetail;
import vn.wms.inventory.dto.internal.DocumentDispatchRequest;
import vn.wms.inventory.dto.internal.DocumentDispatchResponse;
import vn.wms.inventory.entity.StockDispatch;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockReservation;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.InventoryLotRepository;
import vn.wms.inventory.repository.StockDispatchRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockReservationRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service xử lý Trừ tồn kho với Khóa bi quan (PESSIMISTIC_WRITE) chống âm kho tuyệt đối (WMS-12.2 - FR17, NFR03).
 * Đảm bảo tính nhất quán dữ liệu khi nhiều lệnh xuất kho hoặc đơn bán diễn ra đồng thời (concurrency control).
 */
@Service
public class StockDeductionService {

    private static final Logger log = LoggerFactory.getLogger(StockDeductionService.class);

    private final StockItemRepository stockItemRepository;
    private final StockReservationRepository stockReservationRepository;
    private final WarehouseRepository warehouseRepository;
    private final BinLocationRepository binLocationRepository;
    private final InventoryLotRepository inventoryLotRepository;
    private final StockDispatchRepository stockDispatchRepository;

    @Autowired(required = false)
    private WmsEventPublisher wmsEventPublisher;

    /** Chặn xuất từ vị trí đang đóng băng kiểm kê (WMS-14.1). */
    @Autowired
    private StocktakeFreezeGuard stocktakeFreezeGuard;

    /** Ghi sổ kho cho mỗi lần trừ tồn (WMS-17.1). */
    @Autowired
    private StockMovementRecorder stockMovementRecorder;

    public StockDeductionService(StockItemRepository stockItemRepository,
                                 StockReservationRepository stockReservationRepository,
                                 WarehouseRepository warehouseRepository,
                                 BinLocationRepository binLocationRepository,
                                 InventoryLotRepository inventoryLotRepository,
                                 StockDispatchRepository stockDispatchRepository) {
        this.stockItemRepository = stockItemRepository;
        this.stockReservationRepository = stockReservationRepository;
        this.warehouseRepository = warehouseRepository;
        this.binLocationRepository = binLocationRepository;
        this.inventoryLotRepository = inventoryLotRepository;
        this.stockDispatchRepository = stockDispatchRepository;
    }

    /**
     * Trừ tồn khi nhận sự kiện {@code order.dispatched} (mỗi phiếu xuất kho GDN một sự kiện), chống trừ hai lần
     * bằng nhật ký lưu trong DB: sự kiện giao lại thấy messageId đã có trong stock_dispatches thì bỏ qua. Nhật ký
     * được ghi cùng transaction với việc trừ tồn, trừ tồn lỗi thì nhật ký cũng rollback nên lần thử lại vẫn trừ được.
     * Hai lần giao đồng thời thì lần sau vấp ràng buộc duy nhất (company_id, message_id) và rollback.
     *
     * @return kết quả trừ tồn, hoặc {@code null} nếu sự kiện đã được xử lý trước đó
     */
    @Transactional(rollbackFor = Exception.class)
    public DeductStockResponse deductStockForDispatchEvent(DeductStockRequest request, String dispatchedBy, String messageId) {
        if (messageId != null && !messageId.isBlank()) {
            if (stockDispatchRepository.existsByMessageId(messageId)) {
                log.info("Bỏ qua sự kiện order.dispatched của đơn {}: messageId '{}' đã được trừ tồn trước đó",
                        request != null ? request.orderNumber() : null, messageId);
                return null;
            }
            StockDispatch dispatch = new StockDispatch();
            dispatch.setMessageId(messageId);
            dispatch.setOrderId(request != null ? request.orderId() : null);
            dispatch.setOrderNumber(request != null ? request.orderNumber() : null);
            dispatch.setWarehouseId(request != null ? request.warehouseId() : null);
            stockDispatchRepository.saveAndFlush(dispatch);
        } else {
            log.warn("Sự kiện order.dispatched của đơn {} thiếu messageId, không kiểm tra được trùng lặp",
                    request != null ? request.orderNumber() : null);
        }
        return deductStock(request, dispatchedBy);
    }

    /**
     * Trừ tồn đồng bộ khi order-service hoàn tất phiếu xuất kho (WMS-20.4). Dùng chung nhật ký {@code stock_dispatches}
     * với listener {@code order.dispatched}, khóa là {@code dispatchKey} của phiếu: mỗi khóa chỉ trừ một lần, gọi lại
     * (sau khi hết thời gian chờ) hoặc sự kiện mang cùng khóa thì không trừ thêm và trả {@code alreadyProcessed}.
     * Trừ tồn như listener: tự phân bổ theo hạn dùng, bỏ vị trí đóng băng, hoàn tất đúng phần giữ chỗ của đơn và giữ
     * nguyên phần giữ chỗ còn lại khi xuất một phần, ghi sổ kho. Không đủ tồn thì ném lỗi, transaction rollback, khóa
     * không được ghi nên lần thử lại vẫn trừ được.
     */
    @Transactional(rollbackFor = Exception.class)
    public DocumentDispatchResponse deductForDocument(DocumentDispatchRequest request) {
        List<DeductStockItemRequest> items = request.items().stream()
                .map(i -> new DeductStockItemRequest(i.productId(), i.skuCode(), i.quantity(), null, null))
                .toList();
        DeductStockRequest deduct = new DeductStockRequest(request.orderId(), request.orderNumber(), request.warehouseId(),
                "SALES_DISPATCH", "Trừ tồn đồng bộ khi hoàn tất phiếu xuất " + request.dispatchKey(), items);
        DeductStockResponse result = deductStockForDispatchEvent(deduct, request.dispatchedBy(), request.dispatchKey());
        if (result == null) {
            return findProcessedDispatch(request.dispatchKey())
                    .orElseThrow(() -> ApiException.conflict("Không đọc được nhật ký trừ tồn của " + request.dispatchKey()));
        }
        return new DocumentDispatchResponse(request.dispatchKey(), false, request.orderId(), request.warehouseId(),
                result.totalDeductedQuantity(), result.dispatchedAt());
    }

    /** Lần trừ tồn đã ghi với khóa này (trong doanh nghiệp hiện tại), dạng kết quả {@code alreadyProcessed}. */
    @Transactional(readOnly = true)
    public Optional<DocumentDispatchResponse> findProcessedDispatch(String dispatchKey) {
        return stockDispatchRepository.findByMessageId(dispatchKey)
                .map(d -> new DocumentDispatchResponse(dispatchKey, true, d.getOrderId(), d.getWarehouseId(), null,
                        d.getCreatedAt()));
    }

    /**
     * Thực hiện trừ tồn kho theo yêu cầu với Khóa bi quan (PESSIMISTIC_WRITE).
     * <p>
     * Cơ chế hoạt động:
     * 1. Kiểm tra sự tồn tại và tính hợp lệ của kho hàng.
     * 2. Khóa độc quyền các bản ghi {@link StockItem} liên quan bằng câu lệnh {@code SELECT ... FOR UPDATE}.
     * 3. Kiểm tra điều kiện tồn thực tế: nếu tổng số lượng tồn kho &lt; số lượng yêu cầu xuất, lập tức ném
     *    {@link ApiException#badRequest(String)} để Rollback toàn bộ Transaction, chặn đứng 100% tình trạng âm kho.
     * 4. Giảm tồn OnHand (quantity), đồng thời giải phóng / chuyển đổi lượng giữ hàng ảo (Reserved).
     * 5. Phát sự kiện {@code inventory.updated} để thông báo realtime và ghi nhận thẻ kho.
     *
     * @param request      Thông tin đơn xuất và danh sách mặt hàng cần trừ tồn
     * @param dispatchedBy Người thực hiện thao tác (username)
     * @return Kết quả trừ tồn chi tiết từng vị trí ô/kệ
     */
    @Transactional(rollbackFor = Exception.class)
    public DeductStockResponse deductStock(DeductStockRequest request, String dispatchedBy) {
        if (request == null) {
            throw ApiException.badRequest("Yêu cầu trừ tồn kho không được để trống");
        }

        if (request.warehouseId() == null) {
            throw ApiException.badRequest("warehouseId không được để trống");
        }

        Warehouse warehouse = warehouseRepository.findById(request.warehouseId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy kho hàng ID: " + request.warehouseId()));

        if (!warehouse.isActive()) {
            throw ApiException.badRequest("Kho hàng '" + warehouse.getName() + "' (ID: " + warehouse.getId() + ") đang tạm ngưng hoạt động");
        }

        if (request.items() == null || request.items().isEmpty()) {
            throw ApiException.badRequest("Danh sách mặt hàng xuất kho không được rỗng");
        }

        log.info("Bắt đầu xử lý trừ tồn kho với Khóa bi quan (PESSIMISTIC_WRITE) cho kho {} (ID: {}), đơn hàng: {}",
                warehouse.getCode(), warehouse.getId(), request.orderNumber());

        List<DeductedItemDetail> deductedDetails = new ArrayList<>();
        BigDecimal grandTotalDeducted = BigDecimal.ZERO;
        Instant now = Instant.now();

        // Duyệt qua từng dòng mặt hàng cần xuất
        for (DeductStockItemRequest itemReq : request.items()) {
            Long productId = itemReq.productId();
            BigDecimal requiredQty = itemReq.quantity();

            if (requiredQty == null || requiredQty.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("Số lượng cần trừ của sản phẩm ID " + productId + " phải lớn hơn 0");
            }

            BigDecimal beforeStockTotal = stockItemRepository.getStockTotalsByProductIdAndWarehouseId(productId, warehouse.getId(), LocalDate.now())
                    .stream()
                    .findFirst()
                    .map(row -> (BigDecimal) row[0])
                    .orElse(BigDecimal.ZERO);

            BigDecimal ownReservedBefore = request.orderId() != null
                    ? sumActiveReservations(request.orderId(), productId)
                    : BigDecimal.ZERO;

            // Trường hợp 1: Chỉ định vị trí ô/kệ cụ thể (BinLocationId)
            if (itemReq.binLocationId() != null) {
                DeductedItemDetail detail = deductFromSpecificBinLocation(
                        productId, itemReq.skuCode(), itemReq.binLocationId(), requiredQty,
                        request.orderId(), request.orderNumber(), warehouse.getId());
                deductedDetails.add(detail);
                grandTotalDeducted = grandTotalDeducted.add(requiredQty);
            }
            // Trường hợp 2: Không chỉ định ô/kệ -> tự động tìm và khóa toàn bộ các vị trí có hàng sẵn có (FIFO)
            else {
                List<DeductedItemDetail> details = deductFromAvailableStockItems(
                        productId, itemReq.skuCode(), requiredQty,
                        request.orderId(), request.orderNumber(), warehouse.getId());
                deductedDetails.addAll(details);
                grandTotalDeducted = grandTotalDeducted.add(requiredQty);
            }

            // Phần hàng xuất từ vị trí đơn không giữ chỗ vẫn phải trừ vào lượng giữ chỗ của đơn ở vị trí khác,
            // nếu không Reserved ở các vị trí đó bị treo vĩnh viễn. Chỉ trả lại đúng phần chênh lệch đó: phiếu xuất
            // một phần (WMS-12.1) thì phần còn lại của đơn vẫn được giữ chỗ cho các đợt xuất sau.
            if (request.orderId() != null) {
                BigDecimal fulfilledFromOwn = ownReservedBefore
                        .subtract(sumActiveReservations(request.orderId(), productId))
                        .max(BigDecimal.ZERO);
                BigDecimal toRelease = requiredQty.subtract(fulfilledFromOwn);
                if (toRelease.compareTo(BigDecimal.ZERO) > 0) {
                    releaseLeftoverReservations(request.orderId(), productId, toRelease);
                }
            }

            // Tính tồn kho sau khi trừ để phát sự kiện
            BigDecimal afterStockTotal = beforeStockTotal.subtract(requiredQty).max(BigDecimal.ZERO);
            publishInventoryUpdated(warehouse.getId(), productId, itemReq.skuCode(),
                    beforeStockTotal, afterStockTotal,
                    request.reason() != null ? request.reason() : "SALES_DISPATCH",
                    request.orderNumber(), now);
        }

        stockMovementRecorder.recordIssue(request, warehouse, deductedDetails, dispatchedBy, now);
        log.info("Hoàn tất trừ tồn kho với Khóa bi quan. Đơn: {}, Tổng số lượng đã xuất: {}",
                request.orderNumber(), grandTotalDeducted);

        return new DeductStockResponse(
                request.orderId(),
                request.orderNumber(),
                warehouse.getId(),
                request.reason() != null ? request.reason() : "SALES_DISPATCH",
                grandTotalDeducted,
                deductedDetails,
                now
        );
    }

    /**
     * Trừ tồn kho tại một vị trí ô/kệ cụ thể với Khóa bi quan (Row-level lock).
     */
    private DeductedItemDetail deductFromSpecificBinLocation(
            Long productId,
            String skuCode,
            Long binLocationId,
            BigDecimal requiredQty,
            Long orderId,
            String orderNumber,
            Long warehouseId
    ) {
        if (stocktakeFreezeGuard != null) stocktakeFreezeGuard.assertNotFrozen(binLocationId, productId, "xuất kho");
        // Áp dụng Khóa bi quan PESSIMISTIC_WRITE trên đúng dòng StockItem này
        Optional<StockItem> stockItemOpt = stockItemRepository.findByBinLocationIdAndProductIdWithPessimisticLock(binLocationId, productId);

        if (stockItemOpt.isEmpty()) {
            throw ApiException.badRequest(String.format(
                    "Không tìm thấy bản ghi tồn kho của sản phẩm ID %d tại vị trí ô/kệ ID %d để xuất kho",
                    productId, binLocationId));
        }

        StockItem stockItem = stockItemOpt.get();

        // KHÓA LÔ HÀNG (WMS-10.2 - FR12): Chặn xuất kho nếu lô đang bị khóa hoặc đã hết hạn sử dụng
        if (stockItem.isLocked()) {
            throw ApiException.badRequest(String.format(
                    "Lô hàng %s tại vị trí %s đang bị khóa (%s), không được phép xuất kho",
                    stockItem.getLotNo() != null ? "'" + stockItem.getLotNo() + "'" : "",
                    stockItem.getBinLocation().getCode(),
                    stockItem.getLockedReason() != null ? stockItem.getLockedReason() : "Đã khóa"));
        }

        if (stockItem.isExpired(java.time.LocalDate.now())) {
            throw ApiException.badRequest(String.format(
                    "Lô hàng %s tại vị trí %s đã hết hạn sử dụng (%s), tự động chặn không cho phép xuất kho",
                    stockItem.getLotNo() != null ? "'" + stockItem.getLotNo() + "'" : "",
                    stockItem.getBinLocation().getCode(),
                    stockItem.getExpDate()));
        }

        BigDecimal currentQty = stockItem.getQuantity();

        // CHỐNG ÂM KHO: Nếu số lượng hiện có nhỏ hơn số lượng yêu cầu xuất -> Ném lỗi từ chối ngay lập tức
        if (currentQty.compareTo(requiredQty) < 0) {
            throw ApiException.badRequest(String.format(
                    "Không đủ tồn kho để xuất. Sản phẩm ID %d (%s) tại vị trí %s yêu cầu xuất %s, nhưng tồn thực tế chỉ còn %s",
                    productId, skuCode != null ? skuCode : "", stockItem.getBinLocation().getCode(), requiredQty, currentQty));
        }

        // Trừ tồn OnHand
        BigDecimal remainingQty = currentQty.subtract(requiredQty);
        stockItem.setQuantity(remainingQty);

        // Giảm Reserved và hoàn tất bản ghi giữ chỗ nếu có
        BigDecimal previousReserved = stockItem.getReservedQuantity() != null ? stockItem.getReservedQuantity() : BigDecimal.ZERO;
        BigDecimal remainingReserved = processReservationFulfillment(orderId, stockItem, requiredQty);
        BigDecimal fulfilledReserved = previousReserved.subtract(remainingReserved).max(BigDecimal.ZERO);

        stockItemRepository.save(stockItem);
        deductFromInventoryLot(stockItem, requiredQty, fulfilledReserved);

        log.debug("Đã trừ {} đơn vị sản phẩm {} tại vị trí {}. Tồn OnHand còn: {}, Reserved còn: {}",
                requiredQty, productId, stockItem.getBinLocation().getCode(), remainingQty, remainingReserved);

        return new DeductedItemDetail(
                stockItem.getId(),
                productId,
                skuCode,
                binLocationId,
                stockItem.getBinLocation().getCode(),
                stockItem.getLotNo(),
                requiredQty,
                remainingQty,
                remainingReserved
        );
    }

    /**
     * Tự động trừ tồn kho qua các vị trí ô/kệ sẵn có với Khóa bi quan (Row-level locks).
     */
    private List<DeductedItemDetail> deductFromAvailableStockItems(
            Long productId,
            String skuCode,
            BigDecimal requiredQty,
            Long orderId,
            String orderNumber,
            Long warehouseId
    ) {
        // Áp dụng Khóa bi quan trên toàn bộ các vị trí chứa sản phẩm này tại kho (theo ngày hệ thống)
        List<StockItem> lockedItems = stockItemRepository
                .findAvailableStockItemsByProductIdAndWarehouseIdWithPessimisticLock(productId, warehouseId, LocalDate.now());
        if (stocktakeFreezeGuard != null) lockedItems = stocktakeFreezeGuard.excludeFrozen(lockedItems);

        BigDecimal totalOnHand = lockedItems.stream()
                .map(StockItem::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // CHỐNG ÂM KHO: Kiểm tra tổng tồn thực tế trên toàn bộ các ô kệ của kho
        if (totalOnHand.compareTo(requiredQty) < 0) {
            throw ApiException.badRequest(String.format(
                    "Không đủ tồn kho để xuất. Sản phẩm ID %d (%s) yêu cầu xuất %s, nhưng tổng tồn kho thực tế tại kho ID %d chỉ còn %s",
                    productId, skuCode != null ? skuCode : "", requiredQty, warehouseId, totalOnHand));
        }

        List<DeductedItemDetail> details = new ArrayList<>();
        BigDecimal remainingToDeduct = requiredQty;

        for (StockItem stockItem : lockedItems) {
            BigDecimal currentQty = stockItem.getQuantity();
            if (currentQty.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal alloc = remainingToDeduct.min(currentQty);
            BigDecimal remainingQty = currentQty.subtract(alloc);
            stockItem.setQuantity(remainingQty);

            BigDecimal previousReserved = stockItem.getReservedQuantity() != null ? stockItem.getReservedQuantity() : BigDecimal.ZERO;
            BigDecimal remainingReserved = processReservationFulfillment(orderId, stockItem, alloc);
            BigDecimal fulfilledReserved = previousReserved.subtract(remainingReserved).max(BigDecimal.ZERO);
            stockItemRepository.save(stockItem);
            deductFromInventoryLot(stockItem, alloc, fulfilledReserved);

            details.add(new DeductedItemDetail(
                    stockItem.getId(),
                    productId,
                    skuCode,
                    stockItem.getBinLocation().getId(),
                    stockItem.getBinLocation().getCode(),
                    stockItem.getLotNo(),
                    alloc,
                    remainingQty,
                    remainingReserved
            ));

            remainingToDeduct = remainingToDeduct.subtract(alloc);
            if (remainingToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }

        return details;
    }

    /**
     * Hoàn tất phần giữ hàng ảo (Soft Reservation) của chính đơn đang xuất tại một vị trí.
     * Reserved của vị trí chỉ giảm đúng bằng phần giữ chỗ của đơn này được dùng tới: xuất kho không gắn đơn
     * (điều chỉnh, điều chuyển) hoặc xuất từ vị trí mà đơn không giữ chỗ thì Reserved giữ nguyên, vì phần đó
     * đang giữ cho đơn khác.
     */
    private BigDecimal processReservationFulfillment(Long orderId, StockItem stockItem, BigDecimal deductedAmount) {
        BigDecimal currentReserved = stockItem.getReservedQuantity() != null ? stockItem.getReservedQuantity() : BigDecimal.ZERO;
        BigDecimal fulfilled = BigDecimal.ZERO;

        if (orderId != null) {
            BigDecimal remaining = deductedAmount;
            List<StockReservation> reservations = stockReservationRepository
                    .findByStockItemIdAndStatus(stockItem.getId(), "ACTIVE");

            for (StockReservation res : reservations) {
                if (!orderId.equals(res.getOrderId()) || remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                BigDecimal resQty = res.getReservedQuantity();
                BigDecimal used = resQty.min(remaining);
                if (used.compareTo(resQty) >= 0) {
                    res.setStatus("FULFILLED");
                } else {
                    res.setReservedQuantity(resQty.subtract(used));
                }
                stockReservationRepository.save(res);
                fulfilled = fulfilled.add(used);
                remaining = remaining.subtract(used);
            }
        }

        BigDecimal newReserved = currentReserved.subtract(fulfilled).max(BigDecimal.ZERO);
        stockItem.setReservedQuantity(newReserved);
        return newReserved;
    }

    private BigDecimal sumActiveReservations(Long orderId, Long productId) {
        return stockReservationRepository.findByOrderIdAndProductIdAndStatus(orderId, productId, "ACTIVE").stream()
                .map(StockReservation::getReservedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Trả lại tối đa {@code quantity} trong phần giữ chỗ còn ACTIVE của một đơn cho một sản phẩm (phần hàng đã
     * xuất từ vị trí đơn không giữ chỗ): khóa vị trí liên quan, giảm Reserved đúng bằng lượng trả, bản ghi trả hết
     * thì chuyển sang FULFILLED, trả một phần thì giảm số lượng giữ. Phần giữ chỗ còn lại vẫn ACTIVE cho đợt xuất sau.
     */
    private void releaseLeftoverReservations(Long orderId, Long productId, BigDecimal quantity) {
        BigDecimal remaining = quantity;
        List<StockReservation> leftovers = stockReservationRepository
                .findByOrderIdAndProductIdAndStatus(orderId, productId, "ACTIVE");
        for (StockReservation res : leftovers) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal release = res.getReservedQuantity().min(remaining);
            stockItemRepository.findByIdWithPessimisticLock(res.getStockItem().getId()).ifPresent(item -> {
                BigDecimal reserved = item.getReservedQuantity() != null ? item.getReservedQuantity() : BigDecimal.ZERO;
                item.setReservedQuantity(reserved.subtract(release).max(BigDecimal.ZERO));
                stockItemRepository.save(item);
                syncReservedQuantityToLot(item, release.negate());
            });
            if (release.compareTo(res.getReservedQuantity()) >= 0) {
                res.setStatus("FULFILLED");
            } else {
                res.setReservedQuantity(res.getReservedQuantity().subtract(release));
            }
            stockReservationRepository.save(res);
            remaining = remaining.subtract(release);
        }
    }

    /**
     * Phát sự kiện inventory.updated sang RabbitMQ để các service khác và UI realtime cập nhật.
     */
    private void publishInventoryUpdated(
            Long warehouseId,
            Long productId,
            String skuCode,
            BigDecimal beforeQty,
            BigDecimal afterQty,
            String reason,
            String referenceNumber,
            Instant now
    ) {
        if (wmsEventPublisher != null) {
            try {
                InventoryUpdatedEvent event = new InventoryUpdatedEvent(
                        warehouseId, productId, skuCode, beforeQty, afterQty, reason, referenceNumber, now
                );
                wmsEventPublisher.publishAfterCommit(WmsEvents.RoutingKeys.INVENTORY_UPDATED, event);
                log.debug("Đã phát sự kiện inventory.updated cho sản phẩm {} tại kho {}", productId, warehouseId);
            } catch (Exception ex) {
                log.warn("Không thể phát sự kiện inventory.updated cho SKU {}: {}", skuCode, ex.getMessage());
            }
        }
    }

    private void deductFromInventoryLot(StockItem stockItem, BigDecimal quantity, BigDecimal fulfilledReserved) {
        if (stockItem == null || stockItem.getBinLocation() == null || stockItem.getLotNo() == null || quantity == null) {
            return;
        }
        try {
            inventoryLotRepository.findByLotNumberAndBinLocationIdAndProductId(
                    stockItem.getLotNo(), stockItem.getBinLocation().getId(), stockItem.getProductId())
                    .ifPresent(lot -> {
                        lot.setQuantity(lot.getQuantity().subtract(quantity).max(BigDecimal.ZERO));
                        if (fulfilledReserved != null && fulfilledReserved.compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal currentRes = lot.getReservedQuantity() != null ? lot.getReservedQuantity() : BigDecimal.ZERO;
                            lot.setReservedQuantity(currentRes.subtract(fulfilledReserved).max(BigDecimal.ZERO));
                        }
                        inventoryLotRepository.save(lot);
                    });
        } catch (Exception ex) {
            log.warn("Không thể trừ tồn tương ứng trong inventory_lots: {}", ex.getMessage());
        }
    }

    private void syncReservedQuantityToLot(StockItem stockItem, BigDecimal delta) {
        if (stockItem == null || stockItem.getBinLocation() == null || stockItem.getLotNo() == null || delta == null) {
            return;
        }
        try {
            inventoryLotRepository.findByLotNumberAndBinLocationIdAndProductId(
                    stockItem.getLotNo(), stockItem.getBinLocation().getId(), stockItem.getProductId())
                    .ifPresent(lot -> {
                        BigDecimal currentRes = lot.getReservedQuantity() != null ? lot.getReservedQuantity() : BigDecimal.ZERO;
                        BigDecimal newRes = currentRes.add(delta).max(BigDecimal.ZERO);
                        lot.setReservedQuantity(newRes);
                        inventoryLotRepository.save(lot);
                    });
        } catch (Exception ex) {
            log.warn("Không thể đồng bộ reservedQuantity sang inventory_lots: {}", ex.getMessage());
        }
    }
}
