package vn.wms.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request tạo mới kho hàng (WMS-05.1).
 */
public record CreateWarehouseRequest(
        @NotBlank(message = "Mã kho không được để trống")
        @Size(max = 30, message = "Mã kho tối đa 30 ký tự")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Mã kho chỉ gồm chữ cái, chữ số, dấu gạch ngang hoặc gạch dưới")
        String code,

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

