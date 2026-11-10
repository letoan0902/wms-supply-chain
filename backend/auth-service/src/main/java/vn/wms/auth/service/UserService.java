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
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.repository.CompanyRepository;
import vn.wms.auth.entity.Role;
import vn.wms.auth.entity.User;
import vn.wms.auth.repository.RoleRepository;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.subscription.service.UserPlanLimitGuard;
import vn.wms.common.api.ErrorCode;
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
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserPlanLimitGuard userPlanLimitGuard;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       CompanyRepository companyRepository,
                       PasswordEncoder passwordEncoder,
                       UserPlanLimitGuard userPlanLimitGuard) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.companyRepository = companyRepository;
        this.passwordEncoder = passwordEncoder;
        this.userPlanLimitGuard = userPlanLimitGuard;
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
    // Mọi hàm nhận companyId của ADMIN đang đăng nhập (từ JWT) và chỉ thao tác trong doanh nghiệp đó.
    // ==========================================

    /**
     * Lấy danh sách người dùng của doanh nghiệp, có lọc và phân trang.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserDetailResponse> getUsers(
            Long companyId, String keyword, String role, Long warehouseId, Boolean enabled, Pageable pageable) {
        Page<User> page = userRepository.searchUsers(companyId, keyword, role, warehouseId, enabled, pageable);
        return PageResponse.from(page.map(this::mapToUserDetailResponse));
    }

    /**
     * Lấy thông tin chi tiết của một người dùng theo ID trong doanh nghiệp.
     */
    @Transactional(readOnly = true)
    public UserDetailResponse getUserById(Long id, Long companyId) {
        User user = findUserById(id, companyId);
        return mapToUserDetailResponse(user);
    }

    /**
     * Quản trị viên tạo mới người dùng và phân quyền kho. Tài khoản mới luôn thuộc doanh nghiệp của ADMIN đang tạo.
     * Vượt giới hạn số người dùng của gói thì trả {@code PLAN_LIMIT_EXCEEDED} (WMS-23.2).
     */
    @Transactional
    public UserDetailResponse createUser(CreateUserRequest request, Long companyId) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ApiException(ErrorCode.COMPANY_REQUIRED,
                        "Không tìm thấy doanh nghiệp của tài khoản đang đăng nhập"));

        if (userRepository.existsByUsername(request.username())) {
            throw ApiException.conflict("Tên đăng nhập '" + request.username() + "' đã tồn tại");
        }

        if (userRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("Email '" + request.email() + "' đã tồn tại");
        }

        Set<Role> roles = resolveRoles(request.roles());

        // Tài khoản mới luôn ở trạng thái bật nên tính vào giới hạn số người dùng của gói
        userPlanLimitGuard.assertCanAddEnabledUser(companyId);

        User user = new User();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim().toLowerCase());
        user.setFullName(request.fullName() != null ? request.fullName().trim() : null);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setRoles(roles);
        user.setCompany(company);

        if (request.warehouseIds() != null) {
            user.setWarehouseIds(new HashSet<>(request.warehouseIds()));
        }

        User saved = userRepository.save(user);
        userPlanLimitGuard.reportUsage(companyId);
        return mapToUserDetailResponse(saved);
    }

    /**
     * Quản trị viên cập nhật thông tin người dùng, vai trò và phân quyền kho.
     */
    @Transactional
    public UserDetailResponse updateUser(Long id, AdminUpdateUserRequest request, String currentAdminUsername,
                                         Long companyId) {
        User user = findUserById(id, companyId);
        boolean isCurrentlyAdmin = user.getRoles() != null && user.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));

        // Chặn admin tự khóa mình hoặc khóa admin cuối cùng qua updateUser
        if (request.enabled() != null && !request.enabled() && user.isEnabled()) {
            if (currentAdminUsername != null && user.getUsername().equalsIgnoreCase(currentAdminUsername)) {
                throw ApiException.badRequest("Quản trị viên không được phép tự khóa tài khoản của chính mình");
            }
            if (isCurrentlyAdmin && userRepository.countActiveAdmins(companyId) <= 1) {
                throw ApiException.badRequest("Không thể khóa quản trị viên duy nhất còn đang hoạt động của doanh nghiệp");
            }
        }

        // Chặn admin tự gỡ quyền ADMIN hoặc gỡ quyền ADMIN của admin cuối cùng
        if (isCurrentlyAdmin && request.roles() != null && !request.roles().contains("ADMIN")) {
            if (currentAdminUsername != null && user.getUsername().equalsIgnoreCase(currentAdminUsername)) {
                throw ApiException.badRequest("Quản trị viên không được phép tự gỡ vai trò ADMIN của chính mình");
            }
            if (userRepository.countActiveAdmins(companyId) <= 1) {
                throw ApiException.badRequest("Không thể gỡ vai trò ADMIN của quản trị viên duy nhất của doanh nghiệp");
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

        boolean enabledChanged = request.enabled() != null && request.enabled() != user.isEnabled();
        if (enabledChanged && request.enabled()) {
            // Mở khóa tài khoản cũng tính là có thêm một người dùng
            userPlanLimitGuard.assertCanAddEnabledUser(companyId);
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }

        User updated = userRepository.save(user);
        if (enabledChanged) {
            userPlanLimitGuard.reportUsage(companyId);
        }
        return mapToUserDetailResponse(updated);
    }

    /**
     * Phân quyền danh sách kho cho người dùng.
     */
    @Transactional
    public UserDetailResponse assignWarehouses(Long id, Set<Long> warehouseIds, Long companyId) {
        User user = findUserById(id, companyId);
        user.setWarehouseIds(warehouseIds != null ? new HashSet<>(warehouseIds) : new HashSet<>());
        User updated = userRepository.save(user);
        return mapToUserDetailResponse(updated);
    }

    /**
     * Khóa hoặc mở khóa tài khoản người dùng. Mở khóa tính là có thêm một người dùng nên phải qua giới hạn của gói.
     */
    @Transactional
    public UserDetailResponse updateUserStatus(Long id, boolean enabled, String currentAdminUsername,
                                               Long companyId) {
        User user = findUserById(id, companyId);

        if (!enabled && user.isEnabled()) {
            if (currentAdminUsername != null && user.getUsername().equalsIgnoreCase(currentAdminUsername)) {
                throw ApiException.badRequest("Quản trị viên không được phép tự khóa tài khoản của chính mình");
            }
            boolean isCurrentlyAdmin = user.getRoles() != null && user.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));
            if (isCurrentlyAdmin && userRepository.countActiveAdmins(companyId) <= 1) {
                throw ApiException.badRequest("Không thể khóa quản trị viên duy nhất còn đang hoạt động của doanh nghiệp");
            }
        }

        boolean enabledChanged = enabled != user.isEnabled();
        if (enabledChanged && enabled) {
            userPlanLimitGuard.assertCanAddEnabledUser(companyId);
        }

        user.setEnabled(enabled);
        User updated = userRepository.save(user);
        if (enabledChanged) {
            userPlanLimitGuard.reportUsage(companyId);
        }
        return mapToUserDetailResponse(updated);
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng '" + username + "'"));
    }

    /** Tìm người dùng theo ID trong doanh nghiệp; người dùng của doanh nghiệp khác trả 404 như không tồn tại. */
    private User findUserById(Long id, Long companyId) {
        return userRepository.findByIdAndCompanyId(id, companyId)
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
