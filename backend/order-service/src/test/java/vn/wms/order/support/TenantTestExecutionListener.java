package vn.wms.order.support;

import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import vn.wms.common.tenant.TenantContext;

/**
 * Đặt doanh nghiệp mặc định (1) cho luồng chạy test TRƯỚC khi {@link TransactionalTestExecutionListener} mở
 * transaction của test {@code @Transactional}, và xóa sau khi transaction đã kết thúc.
 *
 * <p>Hibernate đọc doanh nghiệp lúc mở phiên làm việc (docs/MULTI_TENANT.md, mục 3.6): đặt
 * {@code TenantContext} trong {@code @BeforeEach} là quá muộn với test {@code @Transactional}, vì transaction đã mở
 * từ trước. Đăng ký cho mọi lớp test của order-service qua {@code META-INF/spring.factories}. Test cách ly tự đổi
 * doanh nghiệp bằng {@code TenantContext.runAs/callAs} hoặc header {@code X-Company-Id}.
 */
public class TenantTestExecutionListener implements TestExecutionListener, Ordered {

    public static final Long DEFAULT_TEST_COMPANY = 1L;

    @Override
    public int getOrder() {
        // TransactionalTestExecutionListener có thứ tự 4000: chạy trước khi mở và sau khi đóng transaction của test
        return 3900;
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        TenantContext.setCompanyId(DEFAULT_TEST_COMPANY);
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        TenantContext.clear();
    }
}
