package vn.wms.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request sinh tự động dải vị trí ô/kệ hàng loạt (WMS-06.1).
 */
public record BulkCreateBinLocationsRequest(
        @NotNull(message = "Kho không được để trống")
        Long warehouseId,

        @NotBlank(message = "Khu vực (Zone) không được để trống")
        @Size(max = 20, message = "Khu vực tối đa 20 ký tự")
        String zone,

        @NotBlank(message = "Tiền tố Kệ không được để trống")
        String rackPrefix,

        @Min(value = 1, message = "Kệ bắt đầu từ 1")
        int rackFrom,

        @Min(value = 1, message = "Kệ kết thúc phải >= 1")
        int rackTo,

        @NotBlank(message = "Tiền tố Ô không được để trống")
        String binPrefix,

        @Min(value = 1, message = "Ô bắt đầu từ 1")
        int binFrom,

        @Min(value = 1, message = "Ô kết thúc phải >= 1")
        int binTo,

        BigDecimal maxWeight
) {}

