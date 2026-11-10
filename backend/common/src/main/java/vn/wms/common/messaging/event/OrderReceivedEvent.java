package vn.wms.common.messaging.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Sự kiện {@code order.received}: Phiếu nhập kho GRN được xác nhận hoàn tất (WMS-10.3 - FR11, FR13).
 * Thông báo cho inventory-service tăng OnHand stock, giảm Incoming stock và cập nhật chi tiết Lô hàng.
 *
 * @param grnId       ID phiếu nhập kho trong order-service
 * @param grnNumber   mã phiếu nhập kho duy nhất (GRN-YYYYMMDD-XXXX)
 * @param poId        ID đơn mua hàng PO tương ứng
 * @param poNumber    mã đơn mua hàng PO
 * @param warehouseId ID kho tiếp nhận hàng hóa
 * @param supplierId  ID nhà cung cấp giao hàng
 * @param receivedBy  người xác nhận phiếu nhập kho
 * @param receiptDate ngày nhập kho thực tế
 * @param receivedAt  thời điểm xác nhận
 * @param items       danh sách chi tiết dòng hàng thực nhận
 */
public record OrderReceivedEvent(
        Long grnId,
        String grnNumber,
        Long poId,
        String poNumber,
        Long warehouseId,
        Long supplierId,
        String receivedBy,
        LocalDate receiptDate,
        Instant receivedAt,
        List<ReceivedItemLine> items
) {
    public OrderReceivedEvent {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
