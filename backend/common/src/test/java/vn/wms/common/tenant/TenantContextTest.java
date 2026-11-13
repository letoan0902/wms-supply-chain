package vn.wms.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void isEmptyByDefault() {
        assertThat(TenantContext.getCompanyId()).isNull();
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void requireCompanyIdThrowsCompanyRequiredWhenMissing() {
        assertThatThrownBy(TenantContext::requireCompanyId)
                .isInstanceOfSatisfying(ApiException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.COMPANY_REQUIRED);
                    assertThat(ex.getHttpStatus()).isEqualTo(403);
                });
    }

    @Test
    void setAndClear() {
        TenantContext.setCompanyId(7L);
        assertThat(TenantContext.getCompanyId()).isEqualTo(7L);
        assertThat(TenantContext.requireCompanyId()).isEqualTo(7L);
        assertThat(TenantContext.isSet()).isTrue();

        TenantContext.clear();
        assertThat(TenantContext.getCompanyId()).isNull();
    }

    @Test
    void nullOrNonPositiveIdClearsContext() {
        TenantContext.setCompanyId(7L);
        TenantContext.setCompanyId(null);
        assertThat(TenantContext.isSet()).isFalse();

        TenantContext.setCompanyId(7L);
        TenantContext.setCompanyId(0L);
        assertThat(TenantContext.isSet()).isFalse();

        TenantContext.setCompanyId(-3L);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void runAsRestoresPreviousCompanyEvenWhenActionFails() {
        TenantContext.setCompanyId(1L);

        TenantContext.runAs(2L, () -> assertThat(TenantContext.getCompanyId()).isEqualTo(2L));
        assertThat(TenantContext.getCompanyId()).isEqualTo(1L);

        assertThatThrownBy(() -> TenantContext.runAs(3L, () -> {
            throw new IllegalStateException("lỗi giả lập");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(TenantContext.getCompanyId()).isEqualTo(1L);
    }

    @Test
    void callAsReturnsValueAndLeavesContextEmptyWhenThereWasNone() {
        Long seen = TenantContext.callAs(5L, TenantContext::getCompanyId);
        assertThat(seen).isEqualTo(5L);
        assertThat(TenantContext.isSet()).isFalse();
    }

    @Test
    void contextDoesNotLeakToOtherThreads() throws Exception {
        TenantContext.setCompanyId(9L);
        Long seenInOtherThread = CompletableFuture.supplyAsync(TenantContext::getCompanyId).get();
        assertThat(seenInOtherThread).isNull();
    }

    @Test
    void parseAcceptsOnlyPositiveNumbers() {
        assertThat(TenantContext.parse("12")).isEqualTo(12L);
        assertThat(TenantContext.parse(" 12 ")).isEqualTo(12L);
        assertThat(TenantContext.parse(null)).isNull();
        assertThat(TenantContext.parse("")).isNull();
        assertThat(TenantContext.parse("abc")).isNull();
        assertThat(TenantContext.parse("0")).isNull();
        assertThat(TenantContext.parse("-1")).isNull();
        assertThat(TenantContext.parse("1; DROP TABLE users")).isNull();
    }
}
