package vn.wms.inventory.entity;

/**
 * Trạng thái của một đợt kiểm kê (WMS-14):
 * <ul>
 *   <li>DRAFT: Mới lập kế hoạch, chưa đóng băng vị trí, có thể hủy.</li>
 *   <li>COUNTING: Đã bắt đầu, vị trí trong phạm vi bị đóng băng, đã chụp tồn hệ thống, nhân viên đang đếm mù.</li>
 *   <li>COUNTED: Đã kết thúc đếm, chênh lệch đã tính, chờ Quản lý kho duyệt (có thể yêu cầu đếm lại).</li>
 *   <li>PENDING_ACCOUNTING: Quản lý kho đã duyệt, phiếu điều chỉnh chờ Kế toán duyệt.</li>
 *   <li>COMPLETED: Đã duyệt xong, tồn kho đã được điều chỉnh (nếu có lệch), vị trí đã mở đóng băng.</li>
 *   <li>REJECTED: Bị từ chối, vị trí đã mở đóng băng, tồn kho giữ nguyên.</li>
 *   <li>CANCELLED: Đã hủy trước khi duyệt, vị trí đã mở đóng băng, tồn kho giữ nguyên.</li>
 * </ul>
 */
public enum StocktakeStatus {
    DRAFT,
    COUNTING,
    COUNTED,
    PENDING_ACCOUNTING,
    COMPLETED,
    REJECTED,
    CANCELLED;

    /** Các trạng thái mà vị trí trong phạm vi đang bị đóng băng. */
    public boolean isFreezing() {
        return this == COUNTING || this == COUNTED || this == PENDING_ACCOUNTING;
    }

    /** Trạng thái kết thúc, không thao tác tiếp được nữa. */
    public boolean isClosed() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }
}
