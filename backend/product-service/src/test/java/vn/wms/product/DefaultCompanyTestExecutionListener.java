package vn.wms.product;

import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import vn.wms.common.tenant.TenantContext;

/**
 * Đặt doanh nghiệp 1 cho luồng test TRƯỚC khi {@link TransactionalTestExecutionListener} mở transaction của test và
 * xóa sau khi transaction đã rollback.
 *
 * <p>Hibernate đọc doanh nghiệp lúc mở phiên làm việc (đầu transaction), nên với lớp test đặt {@code @Transactional}
 * thì gọi {@code TenantContext.setCompanyId} trong {@code @BeforeEach} là quá muộn: phiên đã mở dưới "không doanh
 * nghiệp". Doanh nghiệp 1 cũng là {@code app.tenant.default-company-id} của profile test, nên request MockMvc không
 * gửi {@code X-Company-Id} dùng chung phiên và cùng doanh nghiệp với dữ liệu test vừa lưu.
 */
public class DefaultCompanyTestExecutionListener implements TestExecutionListener, Ordered {

    static final long DEFAULT_COMPANY_ID = 1L;

    @Override
    public int getOrder() {
        // TransactionalTestExecutionListener có thứ tự 4000: chạy trước nó khi bắt đầu, sau nó khi kết thúc.
        return 3900;
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        TenantContext.setCompanyId(DEFAULT_COMPANY_ID);
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        TenantContext.clear();
    }
}
