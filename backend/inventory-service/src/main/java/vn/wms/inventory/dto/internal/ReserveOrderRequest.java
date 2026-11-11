package vn.wms.inventory.dto.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * Yêu cầu nội bộ từ order-service khi xác nhận đơn bán (WMS-20.4): giữ chỗ toàn bộ số lượng của đơn ngay trong
 * bước xác nhận.
 *
 * @param orderId     ID đơn bán trong order-service (khóa chống giữ chỗ hai lần)
 * @param orderNumber mã đơn bán
 * @param warehouseId kho xuất; null thì giữ ở mọi kho của doanh nghiệp (như listener {@code order.created})
 * @param items       các dòng hàng
 */
@Schema(description = "Giữ chỗ tồn kho cho đơn bán khi xác nhận")
public record ReserveOrderRequest(
        @NotNull(message = "orderId không được để trống")
        Long orderId,

        String orderNumber,

        Long warehouseId,

        @NotEmpty(message = "Danh sách dòng hàng không được rỗng")
        @Valid
        List<Item> items
) {

    /**
     * @param productId sản phẩm
     * @param skuCode   mã SKU (để ghi vào thông báo thiếu hàng)
     * @param quantity  số lượng cần giữ
     */
    public record Item(
            @NotNull(message = "productId không được để trống")
            Long productId,
            String skuCode,
            @NotNull(message = "Số lượng không được để trống")
            @DecimalMin(value = "0.001", message = "Số lượng phải lớn hơn 0")
            BigDecimal quantity
    ) {
    }
}
