package vn.wms.order.client;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kết quả trừ tồn theo phiếu xuất từ inventory-service (WMS-20.4).
 *
 * @param dispatchKey           khóa của phiếu xuất
 * @param alreadyProcessed      true nếu khóa đã được trừ trước đó, lần này không trừ thêm
 * @param orderId               ID đơn bán
 * @param warehouseId           kho xuất
 * @param totalDeductedQuantity tổng đã trừ ở lần gọi này (null khi {@code alreadyProcessed})
 * @param processedAt           thời điểm trừ tồn
 */
public record DocumentDispatchResponse(String dispatchKey, boolean alreadyProcessed, Long orderId, Long warehouseId,
                                       BigDecimal totalDeductedQuantity, Instant processedAt) {
}
