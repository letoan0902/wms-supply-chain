package vn.wms.auth.company.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.company.dto.AdminCompanyResponse;
import vn.wms.auth.company.dto.ApproveCompanyRequest;
import vn.wms.auth.company.dto.RejectCompanyRequest;
import vn.wms.auth.company.dto.UpdateCompanyStatusRequest;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.company.repository.CompanyRepository;
import vn.wms.auth.entity.User;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.subscription.entity.BillingCycle;
import vn.wms.auth.subscription.entity.Subscription;
import vn.wms.auth.subscription.repository.SubscriptionRepository;
import vn.wms.auth.subscription.service.SubscriptionService;
import vn.wms.auth.subscription.service.UserPlanLimitGuard;
import vn.wms.common.exception.ApiException;
import vn.wms.common.messaging.WmsEventPublisher;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCompanyService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionService subscriptionService;
    private final UserPlanLimitGuard userPlanLimitGuard;
    private final WmsEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<AdminCompanyResponse> getCompanies(String keyword, CompanyStatus status, Pageable pageable) {
        return companyRepository.searchCompanies(keyword, status, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminCompanyResponse getCompanyDetail(Long id) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy doanh nghiệp với ID: " + id));
        return toResponse(company);
    }

    @Transactional
    public AdminCompanyResponse approveCompany(Long id, ApproveCompanyRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy doanh nghiệp với ID: " + id));

        if (company.getStatus() != CompanyStatus.PENDING) {
            throw ApiException.badRequest("Doanh nghiệp không ở trạng thái chờ duyệt (hiện tại: " + company.getStatus() + ")");
        }

        // 1. Cập nhật trạng thái Doanh nghiệp thành ACTIVE
        company.setStatus(CompanyStatus.ACTIVE);
        company.setActivatedAt(Instant.now());
        company = companyRepository.save(company);

        // 2. Kích hoạt tài khoản Admin đại diện
        User adminUser = userRepository.findFirstByCompanyId(id).orElse(null);
        if (adminUser != null) {
            adminUser.setEnabled(true);
            userRepository.save(adminUser);
        }

        // 3. Khởi tạo/Kích hoạt Gói dịch vụ
        Subscription subscription;
        if (request != null && SubscriptionService.PLAN_PRO.equalsIgnoreCase(request.plan())) {
            BillingCycle cycle = (request.cycle() != null && "YEARLY".equalsIgnoreCase(request.cycle()))
                    ? BillingCycle.YEARLY
                    : BillingCycle.MONTHLY;
            subscription = subscriptionService.activate(id, SubscriptionService.PLAN_PRO, cycle);
        } else {
            int trialDays = (request != null && request.trialDays() != null && request.trialDays() > 0)
                    ? request.trialDays()
                    : 30;
            subscription = subscriptionService.startTrial(company, trialDays);
        }

        userPlanLimitGuard.reportUsage(company.getId());

        // 4. Phát sự kiện gửi email chào mừng và bàn giao tài khoản cho khách hàng
        if (adminUser != null && adminUser.getEmail() != null && !adminUser.getEmail().isBlank()) {
            String subject = "[WMS Supply Chain] Chúc mừng! Doanh nghiệp của bạn đã được phê duyệt thành công";
            String content = String.format(
                    "Kính gửi %s,\n\n" +
                    "Doanh nghiệp \"%s\" (Mã định danh: %s) đã được Quản trị viên hệ thống phê duyệt thành công.\n\n" +
                    "Thông tin tài khoản Quản trị:\n" +
                    "- Tên đăng nhập: %s\n" +
                    "- Email: %s\n" +
                    "- Gói dịch vụ: %s\n" +
                    "- Trạng thái: Đã kích hoạt\n\n" +
                    "Quý khách vui lòng đăng nhập tại hệ thống WMS để bắt đầu cấu hình kho bãi và dữ liệu:\n" +
                    "Đường dẫn đăng nhập: http://localhost:5173/login\n\n" +
                    "Trân trọng,\nĐội ngũ WMS Supply Chain",
                    adminUser.getFullName() != null ? adminUser.getFullName() : adminUser.getUsername(),
                    company.getName(),
                    company.getCode(),
                    adminUser.getUsername(),
                    adminUser.getEmail(),
                    subscription.getPlan() != null ? subscription.getPlan().getName() : "Dùng thử"
            );

            eventPublisher.publishAfterCommit("alert.email", Map.of(
                    "eventType", "COMPANY_APPROVED",
                    "recipient", adminUser.getEmail(),
                    "subject", subject,
                    "content", content
            ));
            log.info("Đã phát sự kiện gửi email phê duyệt doanh nghiệp {} tới {}", company.getCode(), adminUser.getEmail());
        }

        log.info("Phê duyệt thành công doanh nghiệp {} (ID: {})", company.getName(), company.getId());
        return toResponse(company);
    }

    @Transactional
    public AdminCompanyResponse rejectCompany(Long id, RejectCompanyRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy doanh nghiệp với ID: " + id));

        if (company.getStatus() != CompanyStatus.PENDING) {
            throw ApiException.badRequest("Doanh nghiệp không ở trạng thái chờ duyệt (hiện tại: " + company.getStatus() + ")");
        }

        company.setStatus(CompanyStatus.SUSPENDED);
        company = companyRepository.save(company);

        User adminUser = userRepository.findFirstByCompanyId(id).orElse(null);
        String targetEmail = (adminUser != null && adminUser.getEmail() != null) ? adminUser.getEmail() : company.getEmail();

        if (targetEmail != null && !targetEmail.isBlank()) {
            String subject = "[WMS Supply Chain] Thông báo về yêu cầu đăng ký doanh nghiệp";
            String content = String.format(
                    "Kính gửi Quý khách,\n\n" +
                    "Yêu cầu đăng ký doanh nghiệp \"%s\" của quý khách tạm thời chưa được phê duyệt.\n" +
                    "Lý do: %s\n\n" +
                    "Nếu cần hỗ trợ thêm, quý khách vui lòng liên hệ bộ phận hỗ trợ khách hàng của WMS.\n\n" +
                    "Trân trọng,\nĐội ngũ WMS Supply Chain",
                    company.getName(),
                    request.reason()
            );

            eventPublisher.publishAfterCommit("alert.email", Map.of(
                    "eventType", "COMPANY_REJECTED",
                    "recipient", targetEmail,
                    "subject", subject,
                    "content", content
            ));
            log.info("Đã phát sự kiện gửi email từ chối doanh nghiệp {} tới {}", company.getCode(), targetEmail);
        }

        return toResponse(company);
    }

    @Transactional
    public AdminCompanyResponse updateStatus(Long id, UpdateCompanyStatusRequest request) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy doanh nghiệp với ID: " + id));

        company.setStatus(request.status());
        if (request.status() == CompanyStatus.ACTIVE && company.getActivatedAt() == null) {
            company.setActivatedAt(Instant.now());
        }
        company = companyRepository.save(company);

        // Khóa hoặc mở khóa tài khoản Admin tương ứng
        User adminUser = userRepository.findFirstByCompanyId(id).orElse(null);
        if (adminUser != null) {
            adminUser.setEnabled(request.status() == CompanyStatus.ACTIVE);
            userRepository.save(adminUser);
        }

        log.info("Cập nhật trạng thái doanh nghiệp {} sang {}", company.getCode(), request.status());
        return toResponse(company);
    }

    private AdminCompanyResponse toResponse(Company company) {
        User adminUser = userRepository.findFirstByCompanyId(company.getId()).orElse(null);
        Subscription subscription = subscriptionRepository.findByCompanyId(company.getId()).orElse(null);

        AdminCompanyResponse.AdminUserSummary adminSummary = null;
        if (adminUser != null) {
            adminSummary = AdminCompanyResponse.AdminUserSummary.builder()
                    .id(adminUser.getId())
                    .username(adminUser.getUsername())
                    .email(adminUser.getEmail())
                    .fullName(adminUser.getFullName())
                    .enabled(adminUser.isEnabled())
                    .build();
        }

        AdminCompanyResponse.CompanySubscriptionSummary subSummary = null;
        if (subscription != null) {
            subSummary = AdminCompanyResponse.CompanySubscriptionSummary.builder()
                    .plan(subscription.getPlan() != null ? subscription.getPlan().getCode() : null)
                    .status(subscription.getStatus() != null ? subscription.getStatus().name() : null)
                    .trialEndsAt(subscription.getTrialEndsAt())
                    .expiresAt(subscription.getExpiresAt())
                    .build();
        }

        return AdminCompanyResponse.builder()
                .id(company.getId())
                .code(company.getCode())
                .name(company.getName())
                .taxCode(company.getTaxCode())
                .email(company.getEmail())
                .phone(company.getPhone())
                .address(company.getAddress())
                .status(company.getStatus())
                .activatedAt(company.getActivatedAt())
                .createdAt(company.getCreatedAt())
                .adminUser(adminSummary)
                .subscription(subSummary)
                .build();
    }
}
