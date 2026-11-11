package vn.wms.inventory.dto.internal;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Kết quả trừ tồn theo phiếu xuất (WMS-20.4).
 *
 * @param dispatchKey           khóa của phiếu xuất
 * @param alreadyProcessed      true nếu khóa đã được trừ tồn trước đó (gọi lại sau khi hết thời gian chờ, listener),
 *                              lần này không trừ thêm
 * @param orderId               ID đơn bán đã ghi ở lần trừ đầu
 * @param warehouseId           kho xuất
 * @param totalDeductedQuantity tổng số lượng đã trừ ở lần gọi này; null khi {@code alreadyProcessed} (lần đầu không
 *                              lưu tổng, chi tiết xem ở thẻ kho)
 * @param processedAt           thời điểm trừ tồn lần đầu
 */
public record DocumentDispatchResponse(String dispatchKey, boolean alreadyProcessed, Long orderId, Long warehouseId,
                                       BigDecimal totalDeductedQuantity, Instant processedAt) {
}
