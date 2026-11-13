package vn.wms.inventory.dto;

/**
 * Dữ liệu kèm lỗi 409 khi thao tác nhập, xuất, giữ chỗ hoặc điều chuyển chạm vào vị trí đang đóng băng kiểm kê
 * (trường {@code data} của phản hồi lỗi).
 *
 * @param sessionId       ID đợt kiểm kê đang đóng băng vị trí
 * @param sessionCode     mã đợt kiểm kê
 * @param binLocationId   ID vị trí ô/kệ
 * @param binLocationCode mã vị trí ô/kệ
 * @param productId       sản phẩm của thao tác bị chặn (có thể null)
 */
public record FrozenLocationConflict(Long sessionId, String sessionCode, Long binLocationId,
                                     String binLocationCode, Long productId) {
}
