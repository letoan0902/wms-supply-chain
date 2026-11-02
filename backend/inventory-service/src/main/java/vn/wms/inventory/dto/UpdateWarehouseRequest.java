package vn.wms.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request cập nhật thông tin kho hàng (WMS-05.1).
 */
public record UpdateWarehouseRequest(
        @NotBlank(message = "Tên kho không được để trống")
        @Size(max = 150, message = "Tên kho tối đa 150 ký tự")
        String name,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address,

        @Size(max = 100, message = "Tên trưởng kho tối đa 100 ký tự")
        String managerName,

        @Size(max = 30, message = "Số điện thoại liên hệ tối đa 30 ký tự")
        String contactPhone,

        @Min(value = 0, message = "Sức chứa kho phải lớn hơn hoặc bằng 0")
        Integer capacity,

        Boolean active
) {}

