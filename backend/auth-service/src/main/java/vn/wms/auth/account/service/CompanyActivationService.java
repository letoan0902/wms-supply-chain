package vn.wms.auth.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.account.dto.ActivationResponse;
import vn.wms.auth.account.entity.AccountToken;
import vn.wms.auth.account.entity.AccountTokenType;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.company.entity.CompanyStatus;
import vn.wms.auth.company.repository.CompanyRepository;
import vn.wms.auth.entity.User;
import vn.wms.auth.repository.UserRepository;
import vn.wms.auth.subscription.service.SubscriptionService;

import java.time.Instant;
import java.util.Optional;

/**
 * Kích hoạt doanh nghiệp qua email (WMS-23.6): phát liên kết khi đăng ký và khi xin gửi lại, kích hoạt khi bấm liên
 * kết. 30 ngày dùng thử vẫn tính từ lúc đăng ký (thuê bao tạo lúc đăng ký, kích hoạt không đổi thuê bao).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyActivationService {

    private final AccountTokenService tokenService;
    private final AccountEmailPublisher emailPublisher;
    private final AccountEmailProperties properties;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final SubscriptionService subscriptionService;

    /** Phát liên kết kích hoạt cho doanh nghiệp vừa đăng ký, gọi trong transaction đăng ký. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void issueForNewCompany(Company company, User admin) {
        AccountTokenService.IssuedToken token = tokenService.issue(AccountTokenType.COMPANY_ACTIVATION,
                company.getId(), admin.getId(), properties.activationTtl());
        emailPublisher.publishActivation(company, admin, token);
    }

    /**
     * Kích hoạt doanh nghiệp bằng mã trong liên kết: doanh nghiệp {@code PENDING} chuyển {@code ACTIVE} và ghi
     * {@code activated_at}. Mã sai, đã dùng hoặc quá 24 giờ trả {@code 400 TOKEN_INVALID}.
     */
    @Transactional
    public ActivationResponse activate(String rawToken) {
        AccountToken token = tokenService.consume(rawToken, AccountTokenType.COMPANY_ACTIVATION);
        Company company = companyRepository.findById(token.getCompanyId())
                .orElseThrow(AccountTokenService::invalid);
        if (company.getStatus() == CompanyStatus.PENDING) {
            company.setStatus(CompanyStatus.ACTIVE);
            company.setActivatedAt(Instant.now());
            companyRepository.save(company);
            log.info("Đã kích hoạt doanh nghiệp {} (companyId={})", company.getCode(), company.getId());
        } else if (company.getStatus() != CompanyStatus.ACTIVE) {
            // Doanh nghiệp bị tạm khóa sau khi phát liên kết: liên kết không mở khóa được
            throw AccountTokenService.invalid();
        }
        // Ghi lại bộ nhớ đệm thuê bao (mục 2.7) để gateway thấy ngay khi doanh nghiệp bắt đầu dùng
        subscriptionService.refreshForLogin(company.getId());
        String username = Optional.ofNullable(token.getUserId())
                .flatMap(userRepository::findById)
                .map(User::getUsername)
                .orElse(null);
        return new ActivationResponse(company.getCode(), company.getName(), company.getStatus().name(),
                company.getActivatedAt(), username);
    }

    /**
     * Gửi lại liên kết kích hoạt nếu email thuộc tài khoản đang bật của một doanh nghiệp còn {@code PENDING}.
     * Trường hợp khác không làm gì; người gọi luôn trả cùng một phản hồi.
     */
    @Transactional
    public void resend(String normalizedEmail) {
        Optional<User> user = userRepository.findByEmail(normalizedEmail);
        if (user.isEmpty() || !user.get().isEnabled()) {
            return;
        }
        Company company = user.get().getCompany();
        if (company == null || company.getStatus() != CompanyStatus.PENDING) {
            return;
        }
        AccountTokenService.IssuedToken token = tokenService.issue(AccountTokenType.COMPANY_ACTIVATION,
                company.getId(), user.get().getId(), properties.activationTtl());
        emailPublisher.publishActivation(company, user.get(), token);
    }
}
