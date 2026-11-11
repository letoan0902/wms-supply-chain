package vn.wms.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Ghi số đếm mù cho một dòng kiểm kê (WMS-14.1, màn hình quét mã). Xác định dòng bằng {@code lineId}, hoặc bằng
 * vị trí ({@code binLocationId} hoặc {@code binLocationCode} quét từ mã vạch ô kệ) cùng {@code productId} và
 * {@code lotNo}. Không có dòng khớp thì hệ thống tạo dòng hàng phát sinh (không có trong ảnh chụp tồn).
 *
 * @param lineId          ID dòng kiểm kê (ưu tiên nếu có)
 * @param binLocationId   ID vị trí
 * @param binLocationCode mã vị trí (dùng khi quét mã vạch ô kệ)
 * @param productId       ID sản phẩm (bắt buộc khi không có lineId)
 * @param lotNo           số lô; để trống nghĩa là hàng không theo lô
 * @param quantity        số lượng: ADD là số cộng thêm (lớn hơn 0), SET là số đếm cuối cùng (từ 0)
 * @param mode            ADD (mỗi lần quét cộng dồn, mặc định) hoặc SET (nhập tay ghi đè)
 */
public record StocktakeCountRequest(
        Long lineId,
        Long binLocationId,

        @Size(max = 80, message = "Mã vị trí tối đa 80 ký tự")
        String binLocationCode,

        Long productId,

        @Size(max = 50, message = "Số lô tối đa 50 ký tự")
        String lotNo,

        @NotNull(message = "Số lượng đếm không được để trống")
        @DecimalMin(value = "0", message = "Số lượng đếm không được âm")
        @Digits(integer = 15, fraction = 3, message = "Số lượng đếm tối đa 15 chữ số phần nguyên và 3 chữ số thập phân")
        BigDecimal quantity,

        CountMode mode
) {

    /** Cách ghi số đếm. */
    public enum CountMode {
        /** Cộng dồn vào số đã đếm (mỗi lần quét mã). */
        ADD,
        /** Ghi đè số đếm (nhập tay). */
        SET
    }

    public CountMode modeOrDefault() {
        return mode != null ? mode : CountMode.ADD;
    }
}
