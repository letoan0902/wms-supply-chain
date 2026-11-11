package vn.wms.inventory.alert;

import io.swagger.v3.oas.annotations.media.Schema;
import vn.wms.common.messaging.event.StockAlertItem;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSeverity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một cảnh báo tồn kho tính trực tiếp từ tồn hiện tại (WMS-15.1). Cảnh báo theo lô (NEAR_EXPIRY, EXPIRED) gắn với một
 * dòng tồn ({@code stockItemId}); cảnh báo theo mặt hàng (LOW_STOCK, DEAD_STOCK) gắn với cặp (kho, sản phẩm) và để
 * trống các trường của lô.
 */
@Schema(description = "Cảnh báo tồn kho")
public record StockAlertResponse(
        @Schema(description = "Khóa ổn định của cảnh báo, ví dụ NEAR_EXPIRY:15 hoặc LOW_STOCK:2:100")
        String id,
        @Schema(description = "Loại cảnh báo: NEAR_EXPIRY, EXPIRED, DEAD_STOCK, LOW_STOCK")
        StockAlertKind kind,
        @Schema(description = "Mức độ: CRITICAL (đỏ), WARNING (vàng), INFO (xanh dương)")
        StockAlertSeverity severity,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        Long productId,
        @Schema(description = "Mã SKU, null khi không đọc được danh mục sản phẩm")
        String skuCode,
        @Schema(description = "Tên sản phẩm, null khi không đọc được danh mục sản phẩm")
        String productName,
        @Schema(description = "ID dòng tồn (stock_items) với cảnh báo theo lô")
        Long stockItemId,
        String lotNo,
        Long binLocationId,
        String binCode,
        @Schema(description = "Tồn thực tế của lô, hoặc tổng tồn dùng được của mặt hàng tại kho với LOW_STOCK, "
                + "hoặc tổng tồn của mặt hàng tại kho với DEAD_STOCK")
        BigDecimal quantity,
        @Schema(description = "Tồn khả dụng (tồn trừ đã giữ chỗ)")
        BigDecimal availableQuantity,
        LocalDate expDate,
        @Schema(description = "Số ngày còn tới hạn dùng; 0 hoặc âm là đã hết hạn")
        Long daysLeft,
        @Schema(description = "Lần biến động gần nhất của mặt hàng tại kho (UTC)")
        Instant lastMovementAt,
        @Schema(description = "Số ngày không biến động tính tới hôm nay")
        Long daysWithoutMovement,
        BigDecimal safetyStock,
        BigDecimal reorderPoint,
        @Schema(description = "Nội dung cảnh báo tiếng Việt, dùng thẳng cho giao diện")
        String message) {

    /** Dòng tương ứng trong sự kiện tổng hợp gửi notification-service. */
    public StockAlertItem toItem() {
        return new StockAlertItem(productId, skuCode, productName, lotNo, binCode, quantity, availableQuantity,
                expDate, daysLeft, lastMovementAt, daysWithoutMovement, safetyStock, reorderPoint, severity);
    }
}
