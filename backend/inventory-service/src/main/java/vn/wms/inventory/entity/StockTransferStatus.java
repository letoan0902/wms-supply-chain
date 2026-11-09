package vn.wms.inventory.entity;

/**
 * Các trạng thái trong vòng đời phiếu điều chuyển hàng hóa nội bộ (FR08 - WMS-13):
 * <ul>
 *   <li>DRAFT: Bản nháp, có thể chỉnh sửa hoặc hủy.</li>
 *   <li>IN_TRANSIT: Đã duyệt xuất kho nguồn, hàng đang trên đường vận chuyển.</li>
 *   <li>COMPLETED: Kho đích đã nhận đủ hàng, hoàn tất điều chuyển.</li>
 *   <li>PARTIAL_RECEIVED: Kho đích nhận thiếu hoặc có hàng hỏng, kèm biên bản sự cố.</li>
 *   <li>CANCELLED: Đã hủy phiếu (khi còn là bản nháp).</li>
 * </ul>
 */
public enum StockTransferStatus {
    DRAFT,
    IN_TRANSIT,
    COMPLETED,
    PARTIAL_RECEIVED,
    CANCELLED
}

