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
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockReservation;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockReservationRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.time.Instant;
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

    @Autowired(required = false)
    private WmsEventPublisher wmsEventPublisher;

    public StockDeductionService(StockItemRepository stockItemRepository,
                                 StockReservationRepository stockReservationRepository,
                                 WarehouseRepository warehouseRepository,
                                 BinLocationRepository binLocationRepository) {
        this.stockItemRepository = stockItemRepository;
        this.stockReservationRepository = stockReservationRepository;
        this.warehouseRepository = warehouseRepository;
        this.binLocationRepository = binLocationRepository;
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

            BigDecimal beforeStockTotal = stockItemRepository.getStockTotalsByProductIdAndWarehouseId(productId, warehouse.getId())
                    .stream()
                    .findFirst()
                    .map(row -> (BigDecimal) row[0])
                    .orElse(BigDecimal.ZERO);

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

            // Đơn đã xuất đủ sản phẩm này: phần giữ chỗ của đơn còn nằm ở vị trí khác (do xuất từ vị trí
            // không trùng vị trí đã giữ) phải được trả lại, nếu không Reserved ở các vị trí đó bị treo vĩnh viễn.
            if (request.orderId() != null) {
                releaseLeftoverReservations(request.orderId(), productId);
            }

            // Tính tồn kho sau khi trừ để phát sự kiện
            BigDecimal afterStockTotal = beforeStockTotal.subtract(requiredQty).max(BigDecimal.ZERO);
            publishInventoryUpdated(warehouse.getId(), productId, itemReq.skuCode(),
                    beforeStockTotal, afterStockTotal,
                    request.reason() != null ? request.reason() : "SALES_DISPATCH",
                    request.orderNumber(), now);
        }

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
        BigDecimal remainingReserved = processReservationFulfillment(orderId, stockItem, requiredQty);

        stockItemRepository.save(stockItem);

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
        // Áp dụng Khóa bi quan trên toàn bộ các vị trí chứa sản phẩm này tại kho
        List<StockItem> lockedItems = stockItemRepository
                .findAvailableStockItemsByProductIdAndWarehouseIdWithPessimisticLock(productId, warehouseId);

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

            BigDecimal remainingReserved = processReservationFulfillment(orderId, stockItem, alloc);
            stockItemRepository.save(stockItem);

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

    /**
     * Trả lại phần giữ chỗ còn ACTIVE của một đơn cho một sản phẩm sau khi sản phẩm đó đã xuất đủ:
     * khóa vị trí liên quan, giảm Reserved đúng bằng lượng đang giữ rồi chuyển bản ghi sang FULFILLED.
     * Sản phẩm khác của cùng đơn chưa xuất thì vẫn giữ chỗ.
     */
    private void releaseLeftoverReservations(Long orderId, Long productId) {
        List<StockReservation> leftovers = stockReservationRepository
                .findByOrderIdAndProductIdAndStatus(orderId, productId, "ACTIVE");
        for (StockReservation res : leftovers) {
            stockItemRepository.findByIdWithPessimisticLock(res.getStockItem().getId()).ifPresent(item -> {
                BigDecimal reserved = item.getReservedQuantity() != null ? item.getReservedQuantity() : BigDecimal.ZERO;
                item.setReservedQuantity(reserved.subtract(res.getReservedQuantity()).max(BigDecimal.ZERO));
                stockItemRepository.save(item);
            });
            res.setStatus("FULFILLED");
            stockReservationRepository.save(res);
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
}
