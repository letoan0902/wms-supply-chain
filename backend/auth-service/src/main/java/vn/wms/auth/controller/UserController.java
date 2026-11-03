package vn.wms.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.wms.auth.dto.users.*;
import vn.wms.auth.service.UserService;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;

@RestController
@RequestMapping({"/api/auth/users", "/api/users"})
@Tag(name = "User", description = "Quản lý hồ sơ cá nhân và người dùng")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // 1. NGHIỆP VỤ HỒ SƠ CÁ NHÂN (SELF-SERVICE)
    @GetMapping("/profile")
    @Operation(summary = "Xem hồ sơ người dùng đang đăng nhập")
    public ApiResponse<UserProfileResponse> getProfile(Authentication authentication) {
        return ApiResponse.ok(userService.getProfile(authentication.getName()));
    }

    @PutMapping("/profile")
    @Operation(summary = "Cập nhật họ tên và email của người dùng đang đăng nhập")
    public ApiResponse<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        return ApiResponse.ok(userService.updateProfile(authentication.getName(), request));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu người dùng đang đăng nhập")
    public ApiResponse<String> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        userService.changePassword(authentication.getName(), request);
        return ApiResponse.ok("Đổi mật khẩu thành công");
    }

    // 2. NGHIỆP VỤ QUẢN TRỊ NGƯỜI DÙNG & PHÂN QUYỀN KHO (ADMIN)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Lấy danh sách người dùng có phân trang và bộ lọc")
    public ApiResponse<PageResponse<UserDetailResponse>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(userService.getUsers(keyword, role, warehouseId, enabled, pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Xem chi tiết một người dùng")
    public ApiResponse<UserDetailResponse> getUserById(@PathVariable Long id) {
        return ApiResponse.ok(userService.getUserById(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Tạo người dùng mới và phân quyền kho")
    public ApiResponse<UserDetailResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userService.createUser(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Cập nhật thông tin, vai trò và phân quyền kho")
    public ApiResponse<UserDetailResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateUserRequest request,
            Authentication authentication) {
        return ApiResponse.ok(userService.updateUser(id, request, authentication.getName()));
    }

    @PutMapping("/{id}/warehouses")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Phân quyền kho cho người dùng")
    public ApiResponse<UserDetailResponse> assignWarehouses(
            @PathVariable Long id,
            @Valid @RequestBody AssignWarehousesRequest request) {
        return ApiResponse.ok(userService.assignWarehouses(id, request.warehouseIds()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: Khóa hoặc mở khóa tài khoản")
    public ApiResponse<UserDetailResponse> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request,
            Authentication authentication) {
        return ApiResponse.ok(userService.updateUserStatus(id, request.enabled(), authentication.getName()));
    }
}
