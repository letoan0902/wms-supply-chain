package vn.wms.auth.subscription.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.wms.auth.repository.UserRepository;
import vn.wms.common.plan.PlanLimit;
import vn.wms.common.plan.PlanLimitChecker;

/**
 * Giới hạn số người dùng theo gói (WMS-23.2, docs/SAAS_SPEC.md mục 2.5): đếm tài khoản đang bật
 * ({@code enabled = true}) của doanh nghiệp.
 *
 * <p>auth-service không dùng {@code TenantContext}; mọi hàm nhận {@code companyId} của người đang đăng nhập (lấy
 * từ JWT) và gọi các bản nhận doanh nghiệp trực tiếp của {@link PlanLimitChecker}. Checker là tùy chọn: không có
 * Redis hoặc tắt {@code app.plan-limit.enabled} thì bỏ qua. Luồng đăng ký doanh nghiệp mới không đi qua bước
 * kiểm tra (tài khoản ADMIN đầu tiên luôn được tạo).
 */
@Component
@RequiredArgsConstructor
public class UserPlanLimitGuard {

    private final UserRepository userRepository;
    private final ObjectProvider<PlanLimitChecker> planLimitChecker;

    /**
     * Kiểm tra doanh nghiệp còn được có thêm một tài khoản đang bật hay không (tạo mới hoặc mở khóa).
     * Vượt giới hạn thì ném {@code PLAN_LIMIT_EXCEEDED} với {@code data = {limit: USERS, current, max, plan}}.
     */
    public void assertCanAddEnabledUser(Long companyId) {
        PlanLimitChecker checker = planLimitChecker.getIfAvailable();
        if (checker == null || companyId == null) {
            return;
        }
        checker.assertCanCreate(companyId, PlanLimit.USERS, userRepository.countByCompanyIdAndEnabledTrue(companyId));
    }

    /**
     * Đếm lại tài khoản đang bật và ghi vào hash {@code wms:usage:{companyId}}. Gọi sau khi lưu thay đổi; nếu đang
     * trong giao dịch thì số đếm được chụp ngay nhưng chỉ ghi lên Redis sau khi commit.
     */
    public void reportUsage(Long companyId) {
        PlanLimitChecker checker = planLimitChecker.getIfAvailable();
        if (checker == null || companyId == null) {
            return;
        }
        long count = userRepository.countByCompanyIdAndEnabledTrue(companyId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    checker.reportUsage(companyId, PlanLimit.USERS, count);
                }
            });
        } else {
            checker.reportUsage(companyId, PlanLimit.USERS, count);
        }
    }
}
