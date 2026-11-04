package vn.wms.common.messaging.event;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Sự kiện {@code inventory.updated}: tồn kho của một SKU tại một kho vừa thay đổi.
 *
 * @param warehouseId     kho
 * @param productId       id sản phẩm
 * @param skuCode         mã SKU
 * @param quantityBefore  tồn trước khi thay đổi
 * @param quantityAfter   tồn sau khi thay đổi
 * @param reason          lý do, ví dụ GOODS_RECEIPT, GOODS_ISSUE, ADJUSTMENT, TRANSFER
 * @param referenceNumber chứng từ liên quan (mã PO, SO, phiếu kiểm kê)
 * @param updatedAt       thời điểm thay đổi
 */
public record InventoryUpdatedEvent(Long warehouseId, Long productId, String skuCode, BigDecimal quantityBefore,
                                    BigDecimal quantityAfter, String reason, String referenceNumber,
                                    Instant updatedAt) {
}
