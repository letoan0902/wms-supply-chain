package vn.wms.inventory.dto.internal;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Yêu cầu nội bộ từ order-service khi hoàn tất phiếu xuất kho (WMS-20.4): trừ tồn đồng bộ, mỗi khóa
 * {@code dispatchKey} chỉ trừ một lần (bảng {@code stock_dispatches}).
 *
 * @param dispatchKey khóa của phiếu xuất, dạng {@code GDN-<id phiếu>}; cũng được gửi trong sự kiện
 *                    {@code order.dispatched} để listener không trừ lần hai
 * @param orderId     ID đơn bán (để hoàn tất đúng phần giữ chỗ của đơn)
 * @param orderNumber mã đơn bán (ghi vào sổ kho)
 * @param warehouseId kho xuất
 * @param dispatchedBy người xác nhận xuất
 * @param items       các dòng xuất
 */
@Schema(description = "Trừ tồn đồng bộ khi hoàn tất phiếu xuất kho")
public record DocumentDispatchRequest(
        @NotBlank(message = "dispatchKey không được để trống")
        @Size(max = 100, message = "dispatchKey tối đa 100 ký tự")
        String dispatchKey,

        Long orderId,

        @Size(max = 30, message = "orderNumber tối đa 30 ký tự")
        String orderNumber,

        @NotNull(message = "warehouseId không được để trống")
        Long warehouseId,

        String dispatchedBy,

        @NotEmpty(message = "Danh sách dòng xuất không được rỗng")
        @Valid
        List<Item> items
) {

    /**
     * @param productId sản phẩm
     * @param skuCode   mã SKU
     * @param quantity  số lượng xuất
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
