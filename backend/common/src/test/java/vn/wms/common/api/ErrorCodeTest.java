package vn.wms.common.api;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void saasCodesHaveExpectedHttpStatus() {
        assertThat(ErrorCode.COMPANY_REQUIRED.getHttpStatus()).isEqualTo(403);
        assertThat(ErrorCode.COMPANY_INACTIVE.getHttpStatus()).isEqualTo(403);
        assertThat(ErrorCode.SUBSCRIPTION_EXPIRED.getHttpStatus()).isEqualTo(403);
        assertThat(ErrorCode.PLAN_LIMIT_EXCEEDED.getHttpStatus()).isEqualTo(403);
        assertThat(ErrorCode.PAYMENT_INVALID.getHttpStatus()).isEqualTo(400);
        assertThat(ErrorCode.TOKEN_INVALID.getHttpStatus()).isEqualTo(400);
    }

    @Test
    void everyCodeHasVietnameseDefaultMessage() {
        assertThat(Arrays.stream(ErrorCode.values()))
                .allSatisfy(code -> assertThat(code.getDefaultMessage()).isNotBlank());
    }

    @Test
    void genericStatusStillMapsToGenericCode() {
        // Lỗi do Spring MVC tự sinh chỉ có HTTP status: không được quy nhầm sang mã nghiệp vụ SaaS
        assertThat(ErrorCode.fromHttpStatus(403)).isEqualTo(ErrorCode.FORBIDDEN);
        assertThat(ErrorCode.fromHttpStatus(400)).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ErrorCode.fromHttpStatus(404)).isEqualTo(ErrorCode.NOT_FOUND);
        assertThat(ErrorCode.fromHttpStatus(418)).isEqualTo(ErrorCode.BAD_REQUEST);
        assertThat(ErrorCode.fromHttpStatus(502)).isEqualTo(ErrorCode.INTERNAL_ERROR);
    }
}
