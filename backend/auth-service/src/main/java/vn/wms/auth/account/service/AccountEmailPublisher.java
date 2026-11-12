package vn.wms.auth.account.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.wms.auth.company.entity.Company;
import vn.wms.auth.entity.User;
import vn.wms.common.messaging.WmsEventPublisher;
import vn.wms.common.messaging.WmsEvents;
import vn.wms.common.messaging.event.AccountEmailKind;
import vn.wms.common.messaging.event.AccountEmailRequestedEvent;
import vn.wms.common.tenant.TenantContext;

import java.time.Instant;

/**
 * Phát sự kiện yêu cầu gửi email tài khoản để notification-service gửi (WMS-23.6, docs/MESSAGING.md mục 3).
 *
 * <p>Sự kiện chỉ đi sau khi transaction của nghiệp vụ commit ({@link WmsEventPublisher#publishAfterCommit}), mang
 * doanh nghiệp trong header {@code x-wms-company-id}: auth-service không dùng {@link TenantContext} cho request, nên
 * lớp này đặt doanh nghiệp của người nhận vào ngữ cảnh đúng lúc đăng ký phát. Không ghi log liên kết hay mã.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountEmailPublisher {

    private final WmsEventPublisher eventPublisher;
    private final FrontendLinks frontendLinks;

    /** Yêu cầu gửi liên kết kích hoạt doanh nghiệp tới ADMIN đầu tiên. */
    public void publishActivation(Company company, User recipient, AccountTokenService.IssuedToken token) {
        publish(AccountEmailKind.ACTIVATION, WmsEvents.RoutingKeys.AUTH_ACTIVATION_REQUESTED, company, recipient,
                frontendLinks.activation(token.rawToken()), token.expiresAt());
    }

    /** Yêu cầu gửi liên kết đặt lại mật khẩu. */
    public void publishPasswordReset(Company company, User recipient, AccountTokenService.IssuedToken token) {
        publish(AccountEmailKind.PASSWORD_RESET, WmsEvents.RoutingKeys.AUTH_PASSWORD_RESET_REQUESTED, company,
                recipient, frontendLinks.passwordReset(token.rawToken()), token.expiresAt());
    }

    private void publish(AccountEmailKind kind, String routingKey, Company company, User recipient, String link,
                         Instant expiresAt) {
        AccountEmailRequestedEvent event = new AccountEmailRequestedEvent(kind, company.getId(), company.getName(),
                recipient.getEmail(), recipient.getFullName(), link, expiresAt, Instant.now());
        TenantContext.runAs(company.getId(), () -> eventPublisher.publishAfterCommit(routingKey, event));
        log.info("Đã xếp yêu cầu gửi email {} cho tài khoản {} (companyId={})", kind, recipient.getUsername(),
                company.getId());
    }
}
