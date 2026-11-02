package vn.wms.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request tạo vị trí ô/kệ đơn lẻ (WMS-06.1).
 */
public record CreateBinLocationRequest(
        @NotNull(message = "Mã kho không được để trống")
        Long warehouseId,

        @NotBlank(message = "Khu vực (Zone) không được để trống")
        @Size(max = 20, message = "Khu vực tối đa 20 ký tự")
        String zone,

        @NotBlank(message = "Kệ (Rack) không được để trống")
        @Size(max = 20, message = "Kệ tối đa 20 ký tự")
        String rack,

        @NotBlank(message = "Ô (Bin) không được để trống")
        @Size(max = 20, message = "Ô tối đa 20 ký tự")
        String bin,

        @PositiveOrZero(message = "Tải trọng tối đa phải lớn hơn hoặc bằng 0")
        BigDecimal maxWeight,

        Boolean active
) {}

