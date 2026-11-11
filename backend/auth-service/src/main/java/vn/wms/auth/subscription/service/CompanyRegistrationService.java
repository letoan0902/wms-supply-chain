package vn.wms.auth.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.account.service.CompanyActivationService;
import vn.wms.auth.account.service.RegistrationProperties;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.company.repository.CompanyRepository;
import vn.wms.auth.entity.Role;
import vn.wms.auth.entity.User;
import vn.wms.auth.repository.RoleRepository;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.subscription.dto.CompanyRegistrationResponse;
import vn.wms.auth.subscription.dto.RegisterCompanyRequest;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.common.exception.ApiException;

import java.time.Instant;
import java.util.Set;

/**
 * Đăng ký doanh nghiệp mới (WMS-23.1): trong một giao dịch tạo doanh nghiệp, ADMIN đầu tiên và thuê bao dùng thử.
 * Từ WMS-23.6 doanh nghiệp mới ở trạng thái {@code PENDING} và nhận email chứa liên kết kích hoạt; chỉ khi tắt
 * {@code app.registration.require-activation} (máy phát triển không có mail) thì doanh nghiệp được kích hoạt ngay.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyRegistrationService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final SubscriptionService subscriptionService;
    private final PasswordEncoder passwordEncoder;
    private final UserPlanLimitGuard userPlanLimitGuard;
    private final RegistrationProperties registrationProperties;
    private final CompanyActivationService activationService;

    @Transactional
    public CompanyRegistrationResponse register(RegisterCompanyRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();

        // 1. Kiểm tra username & email toàn hệ thống
        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("Tên đăng nhập đã tồn tại: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email đã tồn tại: " + email);
        }

        // 2. Kiểm tra taxCode nếu có
        if (request.getTaxCode() != null && !request.getTaxCode().isBlank()) {
            String cleanTaxCode = request.getTaxCode().trim();
            if (companyRepository.existsByTaxCode(cleanTaxCode)) {
                throw ApiException.conflict("Mã số thuế đã tồn tại: " + cleanTaxCode);
            }
        }

        // 3. Tạo Company
        Company company = new Company();
        company.setName(request.getCompanyName().trim());
        company.setTaxCode(request.getTaxCode() != null && !request.getTaxCode().isBlank() ? request.getTaxCode().trim() : null);
        company.setPhone(request.getPhone() != null && !request.getPhone().isBlank() ? request.getPhone().trim() : null);
        company.setEmail(email);
        boolean requireActivation = registrationProperties.requireActivation();
        // Chờ kích hoạt qua email (WMS-23.6); tắt yêu cầu kích hoạt thì hoạt động ngay
        company.setStatus(requireActivation ? CompanyStatus.PENDING : CompanyStatus.ACTIVE);
        company.setActivatedAt(requireActivation ? null : Instant.now());
        company.setCode("TMP" + System.currentTimeMillis() % 1000000000L); // Mã tạm thời duy nhất
        company = companyRepository.save(company);

        String generatedCode = String.format("CTY%06d", company.getId());
        company.setCode(generatedCode);
        company = companyRepository.save(company);

        // 4. Tạo tài khoản ADMIN đại diện
        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> ApiException.badRequest("Không tìm thấy vai trò ADMIN"));

        User adminUser = new User();
        adminUser.setUsername(username);
        adminUser.setEmail(email);
        adminUser.setFullName(request.getFullName().trim());
        adminUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        adminUser.setEnabled(true);
        adminUser.setCompany(company);
        adminUser.setRoles(Set.of(adminRole));
        adminUser = userRepository.save(adminUser);

        // 5. Khởi tạo thuê bao dùng thử 30 ngày (TRIAL), tính từ lúc đăng ký kể cả khi chưa kích hoạt
        Subscription subscription = subscriptionService.startTrial(company);
        // Tài khoản ADMIN đầu tiên không qua kiểm tra giới hạn, chỉ ghi lại mức sử dụng (WMS-23.2)
        userPlanLimitGuard.reportUsage(company.getId());

        // 6. Liên kết kích hoạt: lưu bản băm của mã, sự kiện gửi email chỉ phát sau khi giao dịch commit
        if (requireActivation) {
            activationService.issueForNewCompany(company, adminUser);
        }

        log.info("Đăng ký thành công doanh nghiệp {} (Mã: {}), Admin: {}, trạng thái {}", company.getName(),
                company.getCode(), adminUser.getUsername(), company.getStatus());

        return CompanyRegistrationResponse.builder()
                .activationRequired(requireActivation)
                .company(CompanyRegistrationResponse.CompanyDto.builder()
                        .id(company.getId())
                        .code(company.getCode())
                        .name(company.getName())
                        .status(company.getStatus().name())
                        .build())
                .admin(CompanyRegistrationResponse.AdminDto.builder()
                        .username(adminUser.getUsername())
                        .email(adminUser.getEmail())
                        .build())
                .subscription(CompanyRegistrationResponse.SubscriptionDto.builder()
                        .plan(subscription.getPlan().getCode())
                        .status(subscription.getStatus().name())
                        .trialEndsAt(subscription.getTrialEndsAt())
                        .build())
                .build();
    }
}
