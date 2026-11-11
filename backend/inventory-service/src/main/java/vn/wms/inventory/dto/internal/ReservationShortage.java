package vn.wms.inventory.dto.internal;

import java.math.BigDecimal;

/**
 * Một sản phẩm không giữ đủ chỗ (trường {@code data} của lỗi 409 khi giữ chỗ cho đơn bán, WMS-20.4).
 *
 * @param productId          sản phẩm
 * @param skuCode            mã SKU
 * @param requestedQuantity  số lượng đơn cần
 * @param availableQuantity  số lượng giữ được (tồn trừ giữ chỗ, không tính lô khóa, hết hạn, vị trí đóng băng)
 * @param shortageQuantity   phần thiếu
 */
public record ReservationShortage(Long productId, String skuCode, BigDecimal requestedQuantity,
                                  BigDecimal availableQuantity, BigDecimal shortageQuantity) {
}
