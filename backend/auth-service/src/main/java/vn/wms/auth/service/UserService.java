package vn.wms.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.dto.users.AdminUpdateUserRequest;
import vn.wms.auth.dto.users.ChangePasswordRequest;
import vn.wms.auth.dto.users.CreateUserRequest;
import vn.wms.auth.dto.users.UpdateProfileRequest;
import vn.wms.auth.dto.users.UserDetailResponse;
import vn.wms.auth.dto.users.UserProfileResponse;
import vn.wms.auth.entity.Role;
import vn.wms.auth.entity.User;
import vn.wms.auth.repository.RoleRepository;
import vn.wms.auth.repository.UserRepository;
import vn.wms.common.api.PageResponse;
import vn.wms.common.constant.Roles;
import vn.wms.common.exception.ApiException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ==========================================
    // 1. NGHIỆP VỤ HỒ SƠ CÁ NHÂN (SELF-SERVICE)
    // ==========================================

    /**
     * Lấy thông tin hồ sơ của người dùng đang đăng nhập.
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String username) {
        User user = findUserByUsername(username);
        return mapToProfileResponse(user);
    }

    /**
     * Người dùng tự cập nhật họ tên và email.
     */
    @Transactional
    public UserProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        User user = findUserByUsername(username);

        // Kiểm tra email nếu thay đổi có bị trùng với tài khoản khác không
        if (!user.getEmail().equalsIgnoreCase(request.email())) {
            if (userRepository.existsByEmail(request.email())) {
                throw ApiException.conflict("Email '" + request.email() + "' đã được sử dụng bởi tài khoản khác");
            }
            user.setEmail(request.email().trim().toLowerCase());
        }

        user.setFullName(request.fullName().trim());
        User updated = userRepository.save(user);
        return mapToProfileResponse(updated);
    }

    /**
     * Người dùng tự đổi mật khẩu.
     */
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw ApiException.badRequest("Mật khẩu xác nhận không khớp với mật khẩu mới");
        }

        User user = findUserByUsername(username);

        // Kiểm tra mật khẩu hiện tại
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Mật khẩu hiện tại không chính xác");
        }

        // Không cho phép đặt mật khẩu mới trùng mật khẩu cũ
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Mật khẩu mới không được trùng với mật khẩu hiện tại");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    // ==========================================
    // 2. NGHIỆP VỤ QUẢN TRỊ NGƯỜI DÙNG & PHÂN QUYỀN KHO (ADMIN)
    // ==========================================

    /**
     * Lấy danh sách người dùng có lọc và phân trang.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserDetailResponse> getUsers(
            String keyword, String role, Long warehouseId, Boolean enabled, Pageable pageable) {
        Page<User> page = userRepository.searchUsers(keyword, role, warehouseId, enabled, pageable);
        return PageResponse.from(page.map(this::mapToUserDetailResponse));
    }

    /**
     * Lấy thông tin chi tiết của một người dùng theo ID.
     */
    @Transactional(readOnly = true)
    public UserDetailResponse getUserById(Long id) {
        User user = findUserById(id);
        return mapToUserDetailResponse(user);
    }

    /**
     * Quản trị viên tạo mới người dùng và phân quyền kho.
     */
    @Transactional
    public UserDetailResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw ApiException.conflict("Tên đăng nhập '" + request.username() + "' đã tồn tại");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("Email '" + request.email() + "' đã tồn tại");
        }

        Set<Role> roles = resolveRoles(request.roles());

        User user = new User();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setFullName(request.fullName() != null ? request.fullName().trim() : null);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setRoles(roles);
        user.setCompanyId(request.companyId());

        if (request.warehouseIds() != null) {
            user.setWarehouseIds(new HashSet<>(request.warehouseIds()));
        }

        User saved = userRepository.save(user);
        return mapToUserDetailResponse(saved);
    }

    /**
     * Quản trị viên cập nhật thông tin người dùng, vai trò và phân quyền kho.
     */
    @Transactional
    public UserDetailResponse updateUser(Long id, AdminUpdateUserRequest request) {
        return updateUser(id, request, null);
    }

    @Transactional
    public UserDetailResponse updateUser(Long id, AdminUpdateUserRequest request, String currentAdminUsername) {
        User user = findUserById(id);
        boolean isCurrentlyAdmin = user.getRoles() != null && user.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));

        // Chặn admin tự khóa mình hoặc khóa admin cuối cùng qua updateUser
        if (request.enabled() != null && !request.enabled() && user.isEnabled()) {
            if (currentAdminUsername != null && user.getUsername().equalsIgnoreCase(currentAdminUsername)) {
                throw ApiException.badRequest("Quản trị viên không được phép tự khóa tài khoản của chính mình");
            }
            if (isCurrentlyAdmin && userRepository.countActiveAdmins() <= 1) {
                throw ApiException.badRequest("Không thể khóa quản trị viên duy nhất còn đang hoạt động trong hệ thống");
            }
        }

        // Chặn admin tự gỡ quyền ADMIN hoặc gỡ quyền ADMIN của admin cuối cùng
        if (isCurrentlyAdmin && request.roles() != null && !request.roles().contains("ADMIN")) {
            if (currentAdminUsername != null && user.getUsername().equalsIgnoreCase(currentAdminUsername)) {
                throw ApiException.badRequest("Quản trị viên không được phép tự gỡ vai trò ADMIN của chính mình");
            }
            if (userRepository.countActiveAdmins() <= 1) {
                throw ApiException.badRequest("Không thể gỡ vai trò ADMIN của quản trị viên duy nhất trong hệ thống");
            }
        }

        if (!user.getEmail().equalsIgnoreCase(request.email().trim())) {
            if (userRepository.existsByEmail(request.email().trim())) {
                throw ApiException.conflict("Email '" + request.email() + "' đã tồn tại");
            }
            user.setEmail(request.email().trim().toLowerCase());
        }

        user.setFullName(request.fullName() != null ? request.fullName().trim() : null);
        user.setRoles(resolveRoles(request.roles()));

        if (request.warehouseIds() != null) {
            user.setWarehouseIds(new HashSet<>(request.warehouseIds()));
        }

        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }

        User updated = userRepository.save(user);
        return mapToUserDetailResponse(updated);
    }

    /**
     * Phân quyền danh sách kho cho người dùng.
     */
    @Transactional
    public UserDetailResponse assignWarehouses(Long id, Set<Long> warehouseIds) {
        User user = findUserById(id);
        user.setWarehouseIds(warehouseIds != null ? new HashSet<>(warehouseIds) : new HashSet<>());
        User updated = userRepository.save(user);
        return mapToUserDetailResponse(updated);
    }

    /**
     * Khóa hoặc mở khóa tài khoản người dùng.
     */
    @Transactional
    public UserDetailResponse updateUserStatus(Long id, boolean enabled) {
        return updateUserStatus(id, enabled, null);
    }

    @Transactional
    public UserDetailResponse updateUserStatus(Long id, boolean enabled, String currentAdminUsername) {
        User user = findUserById(id);

        if (!enabled && user.isEnabled()) {
            if (currentAdminUsername != null && user.getUsername().equalsIgnoreCase(currentAdminUsername)) {
                throw ApiException.badRequest("Quản trị viên không được phép tự khóa tài khoản của chính mình");
            }
            boolean isCurrentlyAdmin = user.getRoles() != null && user.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));
            if (isCurrentlyAdmin && userRepository.countActiveAdmins() <= 1) {
                throw ApiException.badRequest("Không thể khóa quản trị viên duy nhất còn đang hoạt động trong hệ thống");
            }
        }

        user.setEnabled(enabled);
        User updated = userRepository.save(user);
        return mapToUserDetailResponse(updated);
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng '" + username + "'"));
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng với ID " + id));
    }

    private Set<Role> resolveRoles(Set<String> roleNames) {
        Set<Role> roles = new HashSet<>();
        for (String roleName : roleNames) {
            if (!Roles.isValid(roleName)) {
                throw ApiException.badRequest("Vai trò không hợp lệ: " + roleName);
            }
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> ApiException.badRequest("Không tìm thấy vai trò '" + roleName + "' trong hệ thống"));
            roles.add(role);
        }
        return roles;
    }

    private UserProfileResponse mapToProfileResponse(User user) {
        List<String> roles = user.getRoles() != null
                ? user.getRoles().stream().map(Role::getName).sorted().toList()
                : List.of();

        Set<Long> warehouseIds = user.getWarehouseIds() != null
                ? new HashSet<>(user.getWarehouseIds())
                : Set.of();

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.isEnabled(),
                roles,
                warehouseIds
        );
    }

    private UserDetailResponse mapToUserDetailResponse(User user) {
        List<String> roles = user.getRoles() != null
                ? user.getRoles().stream().map(Role::getName).sorted().toList()
                : List.of();

        Set<Long> warehouseIds = user.getWarehouseIds() != null
                ? new HashSet<>(user.getWarehouseIds())
                : Set.of();

        return new UserDetailResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.isEnabled(),
                roles,
                warehouseIds,
                user.getCompanyId(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
