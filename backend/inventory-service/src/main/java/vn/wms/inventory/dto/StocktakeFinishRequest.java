package vn.wms.inventory.dto;

/**
 * Kết thúc đếm (WMS-14.2). Còn dòng chưa đếm thì API trả 409; gọi lại với {@code confirmUncountedAsZero = true}
 * sau khi người dùng xác nhận để tính các dòng chưa đếm bằng 0.
 */
public record StocktakeFinishRequest(Boolean confirmUncountedAsZero) {

    public boolean confirmed() {
        return Boolean.TRUE.equals(confirmUncountedAsZero);
    }
}
