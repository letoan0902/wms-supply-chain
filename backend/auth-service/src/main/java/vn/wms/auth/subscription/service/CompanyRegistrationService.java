package vn.wms.auth.subscription.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.account.entity.AccountTokenType;
import vn.wms.auth.account.repository.AccountTokenRepository;
import vn.wms.auth.account.service.AccountEmailProperties;
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
import vn.wms.auth.subscription.repository.SubscriptionRepository;
import vn.wms.common.exception.ApiException;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Đăng ký doanh nghiệp mới (WMS-23.1): trong một giao dịch tạo doanh nghiệp, ADMIN đầu tiên và thuê bao dùng thử.
 * Từ WMS-23.6 doanh nghiệp mới ở trạng thái {@code PENDING} và nhận email chứa liên kết kích hoạt; chỉ khi tắt
 * {@code app.registration.require-activation} (máy phát triển không có mail) thì doanh nghiệp được kích hoạt ngay.
 *
 * <p>Bản đăng ký bỏ dở (doanh nghiệp chưa từng kích hoạt, quá hạn liên kết kích hoạt, thường do gõ nhầm email) không
 * giữ mãi tên đăng nhập, email và mã số thuế: lần đăng ký mới trùng các giá trị đó gỡ hẳn bản cũ trong cùng giao dịch.
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
    private final AccountEmailProperties accountEmailProperties;
    private final AccountTokenRepository accountTokenRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public CompanyRegistrationResponse register(RegisterCompanyRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();
        String taxCode = request.getTaxCode() != null && !request.getTaxCode().isBlank() ? request.getTaxCode().trim() : null;

        // 0. Gỡ bản đăng ký bỏ dở đang giữ tên đăng nhập, email hoặc mã số thuế này
        releaseAbandonedRegistrations(username, email, taxCode);

        // 1. Kiểm tra username & email toàn hệ thống
        if (userRepository.existsByUsername(username)) {
            throw ApiException.conflict("Tên đăng nhập đã tồn tại: " + username);
        }
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email đã tồn tại: " + email);
        }

        // 2. Kiểm tra taxCode nếu có
        if (taxCode != null && companyRepository.existsByTaxCode(taxCode)) {
            throw ApiException.conflict("Mã số thuế đã tồn tại: " + taxCode);
        }

        // 3. Tạo Company
        Company company = new Company();
        company.setName(request.getCompanyName().trim());
        company.setTaxCode(taxCode);
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

    /**
     * Gỡ các doanh nghiệp đăng ký bỏ dở đang giữ {@code username}, {@code email} hoặc {@code taxCode}: người đăng ký gõ
     * nhầm email nên không bao giờ nhận được liên kết, gửi lại chỉ đi tới đúng địa chỉ nhầm đó, và đăng ký lại bị 409.
     * Mỗi doanh nghiệp được khóa dòng rồi xét lại trước khi gỡ; chỉ doanh nghiệp thỏa {@link #isAbandoned} bị gỡ.
     */
    private void releaseAbandonedRegistrations(String username, String email, String taxCode) {
        Set<Long> companyIds = new LinkedHashSet<>(userRepository.findCompanyIdsByUsernameOrEmail(username, email));
        if (taxCode != null) {
            companyIds.addAll(companyRepository.findIdsByTaxCode(taxCode));
        }
        Instant now = Instant.now();
        for (Long companyId : companyIds) {
            companyRepository.findByIdForUpdate(companyId)
                    .filter(company -> isAbandoned(company, now))
                    .ifPresent(this::removeAbandoned);
        }
    }

    /**
     * Bản đăng ký bỏ dở: doanh nghiệp còn {@code PENDING}, chưa từng kích hoạt ({@code activated_at} rỗng), đăng ký từ
     * quá thời hạn liên kết kích hoạt (24 giờ) và không còn liên kết kích hoạt nào dùng được (liên kết vừa gửi lại vẫn
     * giữ chỗ tới khi hết hạn). Doanh nghiệp đang hoặc từng hoạt động không bao giờ thỏa điều kiện này.
     */
    private boolean isAbandoned(Company company, Instant now) {
        return company.getStatus() == CompanyStatus.PENDING
                && company.getActivatedAt() == null
                && company.getCreatedAt() != null
                && company.getCreatedAt().isBefore(now.minus(accountEmailProperties.activationTtl()))
                && !accountTokenRepository.existsByCompanyIdAndTypeAndUsedAtIsNullAndExpiresAtAfter(
                        company.getId(), AccountTokenType.COMPANY_ACTIVATION, now);
    }

    /** Xóa doanh nghiệp bỏ dở cùng mã email, thuê bao dùng thử và tài khoản của nó (chỉ có ADMIN đầu tiên). */
    private void removeAbandoned(Company company) {
        Long companyId = company.getId();
        accountTokenRepository.deleteAllByCompanyId(companyId);
        subscriptionRepository.findByCompanyId(companyId).ifPresent(subscriptionRepository::delete);
        userRepository.deleteAll(userRepository.findAllByCompanyId(companyId));
        companyRepository.delete(company);
        companyRepository.flush();
        log.info("Gỡ bản đăng ký bỏ dở của doanh nghiệp {} (companyId={}, đăng ký lúc {}) để đăng ký lại",
                company.getCode(), companyId, company.getCreatedAt());
    }
}
