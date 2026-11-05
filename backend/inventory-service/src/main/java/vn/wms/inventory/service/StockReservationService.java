package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.messaging.event.OrderCreatedEvent;
import vn.wms.common.messaging.event.OrderLine;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.StockReservation;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.StockReservationRepository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service quản lý Cơ chế Giữ hàng ảo (Soft Reservation) khi xác nhận Đơn bán SO (FR16 - WMS-11.2).
 * Tự động tăng Reserved Stock -> giảm ATP = OnHand - Reserved.
 */
@Service
public class StockReservationService {

    private static final Logger log = LoggerFactory.getLogger(StockReservationService.class);

    private final StockReservationRepository stockReservationRepository;
    private final StockItemRepository stockItemRepository;

    public StockReservationService(StockReservationRepository stockReservationRepository,
                                   StockItemRepository stockItemRepository) {
        this.stockReservationRepository = stockReservationRepository;
        this.stockItemRepository = stockItemRepository;
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

        if (stockReservationRepository.existsByOrderIdAndStatus(event.orderId(), "ACTIVE")) {
            log.info("Đơn hàng {} (ID {}) đã có bản ghi giữ chỗ ACTIVE, bỏ qua giữ trùng",
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

            List<StockItem> availableItems = event.warehouseId() != null
                    ? stockItemRepository.findAvailableStockItemsByProductIdAndWarehouseId(productId, event.warehouseId())
                    : stockItemRepository.findAvailableStockItemsByProductId(productId);

            for (StockItem stockItem : availableItems) {
                BigDecimal available = stockItem.getQuantity().subtract(stockItem.getReservedQuantity()).max(BigDecimal.ZERO);
                if (available.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                BigDecimal allocate = remaining.min(available);
                stockItem.setReservedQuantity(stockItem.getReservedQuantity().add(allocate));
                stockItemRepository.save(stockItem);

                StockReservation reservation = new StockReservation();
                reservation.setOrderId(event.orderId());
                reservation.setOrderNumber(event.orderNumber());
                reservation.setProductId(productId);
                reservation.setStockItem(stockItem);
                reservation.setWarehouseId(stockItem.getBinLocation().getWarehouse().getId());
                reservation.setReservedQuantity(allocate);
                reservation.setStatus("ACTIVE");
                reservation.setMessageId(messageId);

                stockReservationRepository.save(reservation);

                remaining = remaining.subtract(allocate);
                log.debug("Đã giữ {} đơn vị sản phẩm {} tại vị trí {} cho đơn {}",
                        allocate, productId, stockItem.getBinLocation().getCode(), event.orderNumber());

                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
            }

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                log.warn("Đơn {} sản phẩm SKU {} (ID: {}) còn thiếu {} đơn vị chưa được giữ chỗ do tồn thực tế không đủ",
                        event.orderNumber(), line.skuCode(), productId, remaining);
            }
        }

        log.info("Hoàn tất giữ hàng ảo cho đơn bán {}", event.orderNumber());
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
            }

            reservation.setStatus("RELEASED");
            stockReservationRepository.save(reservation);
        }

        log.info("Đã giải phóng {} bản ghi giữ chỗ cho đơn bán ID {}", activeReservations.size(), orderId);
    }

    /**
     * Lấy danh sách bản ghi giữ chỗ của một đơn hàng.
     */
    @Transactional(readOnly = true)
    public List<StockReservation> getReservationsByOrderId(Long orderId) {
        return stockReservationRepository.findByOrderIdAndStatus(orderId, "ACTIVE");
    }
}
