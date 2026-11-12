package vn.wms.inventory.dto.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Yêu cầu nội bộ từ order-service trước khi lưu phiếu nhập kho (WMS-20.4): các cặp vị trí và sản phẩm sẽ được cộng
 * tồn. Vị trí được xác định đúng như khi xử lý sự kiện {@code order.received} (theo ID, rồi theo mã, cuối cùng là ô
 * đầu tiên của kho), nên kết quả kiểm tra khớp với nơi hàng thực sự được cất.
 *
 * @param warehouseId kho nhận hàng của phiếu nhập
 * @param lines       các dòng có số lượng đạt lớn hơn 0
 */
@Schema(description = "Kiểm tra vị trí nhận hàng của phiếu nhập trước khi lưu")
public record ReceiptLocationCheckRequest(
        @NotNull(message = "warehouseId không được để trống")
        Long warehouseId,

        @NotEmpty(message = "Danh sách dòng nhập kho không được rỗng")
        @Valid
        List<Line> lines
) {

    /**
     * Một dòng nhập kho.
     *
     * @param productId    sản phẩm
     * @param locationId   ID vị trí ô kệ chọn trên phiếu (có thể null)
     * @param locationCode mã vị trí ô kệ chọn trên phiếu (có thể null)
     */
    public record Line(
            @NotNull(message = "productId không được để trống")
            Long productId,
            Long locationId,
            String locationCode
    ) {
    }
}
