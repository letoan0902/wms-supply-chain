package vn.wms.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Yêu cầu kiểm tra tồn khả dụng ATP cho nhiều sản phẩm cùng lúc (WMS-11.1).
 */
@Schema(description = "Yêu cầu kiểm tra tồn ATP theo danh sách sản phẩm")
public record BatchAtpRequest(
        @Schema(description = "ID kho hàng (để trống nếu muốn kiểm tra tổng tồn trên toàn hệ thống)")
        Long warehouseId,

        @NotEmpty(message = "Danh sách ID sản phẩm không được rỗng")
        @Schema(description = "Danh sách ID sản phẩm cần kiểm tra ATP")
        List<Long> productIds
) {
}
