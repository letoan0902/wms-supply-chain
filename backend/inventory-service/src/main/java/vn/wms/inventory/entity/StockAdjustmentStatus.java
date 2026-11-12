package vn.wms.inventory.entity;

/**
 * Trạng thái phiếu điều chỉnh tồn kho (WMS-14.2):
 * <ul>
 *   <li>PENDING_APPROVAL: Quản lý kho đã duyệt kết quả kiểm kê, phiếu chờ Kế toán duyệt.</li>
 *   <li>APPROVED: Kế toán đã duyệt, chênh lệch đã được ghi vào tồn kho.</li>
 *   <li>REJECTED: Bị từ chối, tồn kho giữ nguyên.</li>
 * </ul>
 */
public enum StockAdjustmentStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
