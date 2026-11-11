package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.inventory.dto.internal.ReservationShortage;
import vn.wms.inventory.dto.internal.ReserveOrderRequest;
import vn.wms.inventory.dto.internal.ReserveOrderResponse;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockReservation;
import vn.wms.inventory.repository.InventoryLotRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockReservationRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Service quản lý Cơ chế Giữ hàng ảo (Soft Reservation) khi xác nhận Đơn bán SO (FR16 - WMS-11.2).
 * Tự động tăng Reserved Stock -> giảm ATP = OnHand - Reserved.
 */
@Service
public class StockReservationService {

    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);

    private final StockReservationRepository stockReservationRepository;
    private final StockItemRepository stockItemRepository;
    private final InventoryLotRepository inventoryLotRepository;

    /** Không giữ chỗ đơn mới ở vị trí đang đóng băng kiểm kê (WMS-14.1). */
    @org.springframework.beans.factory.annotation.Autowired
    private StocktakeFreezeGuard stocktakeFreezeGuard;

    public StockReservationService(StockReservationRepository stockReservationRepository,
                                   StockItemRepository stockItemRepository,
                                   InventoryLotRepository inventoryLotRepository) {
        this.stockReservationRepository = stockReservationRepository;
        this.stockItemRepository = stockItemRepository;
        this.inventoryLotRepository = inventoryLotRepository;
    }

    /**
     * Giữ hàng ảo khi nhận được sự kiện đơn bán hàng CONFIRMED (OrderCreatedEvent - OrderType.SALES).
     * Đảm bảo tính Idempotent chống xử lý trùng message (dựa trên orderId và messageId).
     */
    @Transactional
    public void reserveStock(OrderCreatedEvent event, String messageId) {
        if (event == null || event.orderId() == null) {
            log.warn("Bỏ qua giữ hàng ảo vì sự kiện không hợp lệ hoặc thiếu orderId");
            return;
        }

        // 1. Kiểm tra chống xử lý trùng lặp (Idempotency)
        if (messageId != null && !messageId.isBlank() && stockReservationRepository.existsByMessageId(messageId)) {
            log.info("Bỏ qua giữ hàng ảo cho đơn {} do messageId '{}' đã được xử lý", event.orderNumber(), messageId);
            return;
        }

        // Từ WMS-20.4 order-service giữ chỗ đồng bộ khi xác nhận đơn (reserveForOrder) trước khi phát order.created,
        // nên sự kiện tới nơi thì đơn đã có giữ chỗ. Bỏ qua khi đơn đã có bất kỳ bản ghi giữ chỗ nào, không chỉ ACTIVE:
        // sự kiện tới trễ sau khi đơn đã xuất hết (giữ chỗ FULFILLED) hoặc đã hủy (RELEASED) không được giữ lại lần nữa.
        // Mỗi đơn bán chỉ được xác nhận một lần nên chỉ giữ chỗ một lần trong đời đơn.
        if (stockReservationRepository.existsByOrderId(event.orderId())) {
            log.info("Đơn hàng {} (ID {}) đã có bản ghi giữ chỗ, bỏ qua giữ trùng",
                    event.orderNumber(), event.orderId());
            return;
        }

        log.info("Bắt đầu thực hiện giữ hàng ảo (Soft Reservation) cho đơn bán {} (ID: {})",
                event.orderNumber(), event.orderId());

        // 2. Duyệt qua từng dòng mặt hàng và giữ chỗ theo thứ tự các lô/vị trí có sẵn
        for (OrderLine line : event.items()) {
            Long productId = line.productId();
            if (productId == null) {
                log.warn("Bỏ qua dòng SKU {} vì không có productId", line.skuCode());
                continue;
            }

            BigDecimal remaining = line.quantity() != null ? line.quantity() : BigDecimal.ZERO;
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            LocalDate today = LocalDate.now();
            List<StockItem> availableItems = event.warehouseId() != null
                    ? stockItemRepository.findAvailableStockItemsByProductIdAndWarehouseIdForUpdate(productId, event.warehouseId(), today)
                    : stockItemRepository.findAvailableStockItemsByProductIdForUpdate(productId, today);
            if (stocktakeFreezeGuard != null) availableItems = stocktakeFreezeGuard.excludeFrozen(availableItems);

            remaining = allocate(availableItems, remaining, event.orderId(), event.orderNumber(), productId, messageId);

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                log.warn("Đơn {} sản phẩm SKU {} (ID: {}) còn thiếu {} đơn vị chưa được giữ chỗ do tồn thực tế không đủ",
                        event.orderNumber(), line.skuCode(), productId, remaining);
            }
        }

        log.info("Hoàn tất giữ hàng ảo cho đơn bán {}", event.orderNumber());
    }

    /**
     * Giữ chỗ đồng bộ khi order-service xác nhận đơn bán (WMS-20.4), tất cả hoặc không: mọi dòng phải giữ đủ, thiếu
     * dòng nào thì ném 409 CONFLICT (kèm danh sách {@link ReservationShortage}) và transaction rollback, không để lại
     * giữ chỗ một phần. Quy tắc phân bổ như listener {@code order.created}: hạn dùng gần trước, bỏ lô khóa, lô hết hạn
     * và vị trí đang đóng băng kiểm kê.
     * <p>
     * Idempotent theo đơn: đơn đã có giữ chỗ ACTIVE thì không đổi gì và trả {@code alreadyReserved = true} (gọi lại sau
     * khi hết thời gian chờ, bấm hai lần). Các dòng tồn của mọi sản phẩm được khóa bi quan TRƯỚC khi kiểm tra giữ chỗ cũ
     * (câu lệnh đầu tiên của transaction là câu khóa), nên hai lần gọi đồng thời cho cùng đơn xếp hàng trên cùng các
     * dòng tồn và lần sau thấy giữ chỗ lần trước vừa commit (MySQL REPEATABLE READ chụp dữ liệu ở lần đọc thường đầu
     * tiên, sau khi đã có khóa).
     */
    @Transactional
    public ReserveOrderResponse reserveForOrder(ReserveOrderRequest request) {
        // Gộp theo sản phẩm, khóa theo thứ tự productId tăng dần để hai đơn cùng sản phẩm không khóa chéo nhau
        Map<Long, BigDecimal> demand = new TreeMap<>();
        Map<Long, String> skuByProduct = new HashMap<>();
        for (ReserveOrderRequest.Item item : request.items()) {
            demand.merge(item.productId(), item.quantity(), BigDecimal::add);
            if (item.skuCode() != null) {
                skuByProduct.putIfAbsent(item.productId(), item.skuCode());
            }
        }
        LocalDate today = LocalDate.now();
        Map<Long, List<StockItem>> lockedByProduct = new LinkedHashMap<>();
        for (Long productId : demand.keySet()) {
            List<StockItem> items = request.warehouseId() != null
                    ? stockItemRepository.findAvailableStockItemsByProductIdAndWarehouseIdForUpdate(productId, request.warehouseId(), today)
                    : stockItemRepository.findAvailableStockItemsByProductIdForUpdate(productId, today);
            lockedByProduct.put(productId, items);
        }

        List<StockReservation> existing = stockReservationRepository.findByOrderIdAndStatus(request.orderId(), "ACTIVE");
        if (!existing.isEmpty()) {
            log.info("Đơn bán {} (ID {}) đã có {} bản ghi giữ chỗ ACTIVE, không giữ thêm",
                    request.orderNumber(), request.orderId(), existing.size());
            return summarize(request.orderId(), true, existing);
        }

        List<ReservationShortage> shortages = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : demand.entrySet()) {
            List<StockItem> usable = lockedByProduct.get(entry.getKey());
            if (stocktakeFreezeGuard != null) {
                usable = stocktakeFreezeGuard.excludeFrozen(usable);
                lockedByProduct.put(entry.getKey(), usable);
            }
            BigDecimal available = usable.stream()
                    .map(s -> s.getQuantity().subtract(s.getReservedQuantity()).max(BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (available.compareTo(entry.getValue()) < 0) {
                shortages.add(new ReservationShortage(entry.getKey(), skuByProduct.get(entry.getKey()),
                        entry.getValue(), available, entry.getValue().subtract(available)));
            }
        }
        if (!shortages.isEmpty()) {
            String detail = shortages.stream()
                    .map(s -> "SKU " + (s.skuCode() != null ? s.skuCode() : "ID " + s.productId())
                            + " thiếu " + s.shortageQuantity().stripTrailingZeros().toPlainString()
                            + " (yêu cầu " + s.requestedQuantity().stripTrailingZeros().toPlainString()
                            + ", giữ được " + s.availableQuantity().stripTrailingZeros().toPlainString() + ")")
                    .collect(Collectors.joining("; "));
            throw ApiException.withData(ErrorCode.CONFLICT,
                    "Không đủ tồn khả dụng để giữ chỗ cho đơn " + request.orderNumber() + ": " + detail, shortages);
        }

        List<StockReservation> created = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : demand.entrySet()) {
            allocate(lockedByProduct.get(entry.getKey()), entry.getValue(), request.orderId(), request.orderNumber(),
                    entry.getKey(), null);
        }
        created.addAll(stockReservationRepository.findByOrderIdAndStatus(request.orderId(), "ACTIVE"));
        log.info("Đã giữ chỗ đồng bộ cho đơn bán {} (ID {}), {} bản ghi", request.orderNumber(), request.orderId(), created.size());
        return summarize(request.orderId(), false, created);
    }

    private ReserveOrderResponse summarize(Long orderId, boolean alreadyReserved, List<StockReservation> reservations) {
        Map<Long, BigDecimal> byProduct = new TreeMap<>();
        for (StockReservation reservation : reservations) {
            byProduct.merge(reservation.getProductId(), reservation.getReservedQuantity(), BigDecimal::add);
        }
        List<ReserveOrderResponse.Line> lines = byProduct.entrySet().stream()
                .map(e -> new ReserveOrderResponse.Line(e.getKey(), e.getValue()))
                .toList();
        return new ReserveOrderResponse(orderId, alreadyReserved, lines);
    }

    /**
     * Giữ tối đa {@code quantity} trên các dòng tồn đã khóa theo thứ tự cho sẵn, tạo một bản ghi ACTIVE cho mỗi dòng
     * tồn dùng tới. Trả về phần chưa giữ được.
     */
    private BigDecimal allocate(List<StockItem> availableItems, BigDecimal quantity, Long orderId, String orderNumber,
                                Long productId, String messageId) {
        BigDecimal remaining = quantity;
        for (StockItem stockItem : availableItems) {
            BigDecimal available = stockItem.getQuantity().subtract(stockItem.getReservedQuantity()).max(BigDecimal.ZERO);
            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal allocate = remaining.min(available);
            stockItem.setReservedQuantity(stockItem.getReservedQuantity().add(allocate));
            stockItemRepository.save(stockItem);

            syncReservedQuantityToLot(stockItem, allocate);

            StockReservation reservation = new StockReservation();
            reservation.setOrderId(orderId);
            reservation.setOrderNumber(orderNumber);
            reservation.setProductId(productId);
            reservation.setStockItem(stockItem);
            reservation.setWarehouseId(stockItem.getBinLocation().getWarehouse().getId());
            reservation.setReservedQuantity(allocate);
            reservation.setStatus("ACTIVE");
            reservation.setMessageId(messageId);

            stockReservationRepository.save(reservation);

            remaining = remaining.subtract(allocate);
            log.debug("Đã giữ {} đơn vị sản phẩm {} tại vị trí {} cho đơn {}",
                    allocate, productId, stockItem.getBinLocation().getCode(), orderNumber);

            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
        }
        return remaining;
    }

    /**
     * Giải phóng số lượng giữ hàng ảo (Soft Reservation) khi đơn bán bị hủy.
     */
    @Transactional
    public void releaseReservationsByOrderId(Long orderId) {
        if (orderId == null) {
            return;
        }

        List<StockReservation> activeReservations = stockReservationRepository.findByOrderIdAndStatus(orderId, "ACTIVE");
        if (activeReservations.isEmpty()) {
            log.info("Không có bản ghi giữ chỗ ACTIVE nào cho đơn hàng ID {}", orderId);
            return;
        }

        for (StockReservation reservation : activeReservations) {
            StockItem stockItem = reservation.getStockItem();
            if (stockItem != null) {
                BigDecimal newReserved = stockItem.getReservedQuantity()
                        .subtract(reservation.getReservedQuantity())
                        .max(BigDecimal.ZERO);
                stockItem.setReservedQuantity(newReserved);
                stockItemRepository.save(stockItem);

                syncReservedQuantityToLot(stockItem, reservation.getReservedQuantity().negate());
            }

            reservation.setStatus("RELEASED");
            stockReservationRepository.save(reservation);
        }

        log.info("Đã giải phóng {} bản ghi giữ chỗ cho đơn bán ID {}", activeReservations.size(), orderId);
    }

    private void syncReservedQuantityToLot(StockItem stockItem, BigDecimal delta) {
        if (stockItem == null || stockItem.getBinLocation() == null || stockItem.getLotNo() == null || delta == null) {
            return;
        }
        try {
            inventoryLotRepository.findByLotNumberAndBinLocationIdAndProductId(
                    stockItem.getLotNo(), stockItem.getBinLocation().getId(), stockItem.getProductId())
                    .ifPresent(lot -> {
                        BigDecimal newRes = lot.getReservedQuantity().add(delta).max(BigDecimal.ZERO);
                        lot.setReservedQuantity(newRes);
                        inventoryLotRepository.save(lot);
                    });
        } catch (Exception ex) {
            log.warn("Không thể đồng bộ reservedQuantity sang inventory_lots: {}", ex.getMessage());
        }
    }

    /**
     * Lấy danh sách bản ghi giữ chỗ của một đơn hàng.
     */
    @Transactional(readOnly = true)
    public List<StockReservation> getReservationsByOrderId(Long orderId) {
        return stockReservationRepository.findByOrderIdAndStatus(orderId, "ACTIVE");
    }
}
