package vn.wms.common.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một dòng cảnh báo trong bản tổng hợp {@link StockAlertSummaryEvent}. Trường không áp dụng cho loại cảnh báo
 * thì để null: lô và hạn dùng chỉ có ở NEAR_EXPIRY, EXPIRED; ngày không biến động chỉ có ở DEAD_STOCK; ngưỡng
 * tồn chỉ có ở LOW_STOCK.
 *
 * @param productId           id sản phẩm bên product-service
 * @param skuCode             mã SKU, null khi không lấy được danh mục sản phẩm
 * @param productName         tên sản phẩm, null khi không lấy được danh mục sản phẩm
 * @param lotNo               số lô (null với hàng không quản lý lô hoặc cảnh báo cấp mặt hàng)
 * @param binCode             mã ô kệ chứa lô
 * @param quantity            tồn thực tế (OnHand) của lô hoặc của mặt hàng tại kho
 * @param availableQuantity   tồn khả dụng (OnHand trừ đã giữ chỗ)
 * @param expDate             hạn sử dụng
 * @param daysLeft            số ngày còn lại tới hạn dùng (0 hoặc âm khi đã hết hạn)
 * @param lastMovementAt      thời điểm biến động gần nhất của mặt hàng tại kho
 * @param daysWithoutMovement số ngày không biến động tính tới hôm nay
 * @param safetyStock         mức tồn an toàn của sản phẩm
 * @param reorderPoint        điểm đặt hàng lại của sản phẩm
 * @param severity            mức độ của dòng này
 */
public record StockAlertItem(Long productId, String skuCode, String productName, String lotNo, String binCode,
                             BigDecimal quantity, BigDecimal availableQuantity, LocalDate expDate, Long daysLeft,
                             Instant lastMovementAt, Long daysWithoutMovement, BigDecimal safetyStock,
                             BigDecimal reorderPoint, StockAlertSeverity severity) {
}
