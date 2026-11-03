package vn.wms.auth.dto.users;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

/**
 * DTO gán / phân quyền danh sách kho cho người dùng.
 */
public record AssignWarehousesRequest(
        @NotEmpty(message = "Danh sách mã kho không được để trống")
        Set<Long> warehouseIds
) {
}
