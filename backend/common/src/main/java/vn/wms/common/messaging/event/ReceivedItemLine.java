package vn.wms.common.messaging.event;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Chi tiết dòng hàng nhập kho đạt chuẩn / từ chối trong sự kiện {@link OrderReceivedEvent} (WMS-10.3).
 *
 * @param productId         ID sản phẩm
 * @param skuCode           mã SKU sản phẩm
 * @param productName       tên sản phẩm
 * @param acceptedQuantity  số lượng đạt chuẩn nhập kho (tăng OnHand)
 * @param rejectedQuantity  số lượng bị từ chối / phế phẩm
 * @param uom               đơn vị tính
 * @param locationId        ID vị trí ô/kệ cất hàng (nếu có)
 * @param locationCode      mã vị trí ô/kệ (nếu có)
 * @param batchNumber       số lô hàng (Batch/Lot - FR12)
 * @param mfgDate           ngày sản xuất
 * @param expDate           hạn sử dụng
 */
public record ReceivedItemLine(
        Long productId,
        String skuCode,
        String productName,
        BigDecimal acceptedQuantity,
        BigDecimal rejectedQuantity,
        String uom,
        Long locationId,
        String locationCode,
        String batchNumber,
        LocalDate mfgDate,
        LocalDate expDate
) {
}
