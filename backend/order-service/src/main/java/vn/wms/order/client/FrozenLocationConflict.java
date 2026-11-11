package vn.wms.order.client;

/**
 * Một vị trí đang đóng băng kiểm kê do inventory-service trả về (WMS-20.4), cùng cấu trúc với trường {@code data}
 * của lỗi 409 bên inventory-service.
 *
 * @param sessionId       ID đợt kiểm kê
 * @param sessionCode     mã đợt kiểm kê
 * @param binLocationId   ID vị trí ô kệ
 * @param binLocationCode mã vị trí ô kệ
 * @param productId       sản phẩm của dòng bị chặn
 */
public record FrozenLocationConflict(Long sessionId, String sessionCode, Long binLocationId,
                                     String binLocationCode, Long productId) {
}
