package vn.wms.common.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RequestHeaderAuditorAwareTest {

    private final RequestHeaderAuditorAware auditorAware = new RequestHeaderAuditorAware();

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static void bindRequestWithHeader(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (headerValue != null) {
            request.addHeader(RequestHeaderAuditorAware.USER_HEADER, headerValue);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void outsideHttpContextAuditorIsSystem() {
        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }

    @Test
    void userNameHeaderIsUsedWhenPresent() {
        bindRequestWithHeader("  manager01 ");

        assertThat(auditorAware.getCurrentAuditor()).contains("manager01");
    }

    @Test
    void missingOrBlankHeaderFallsBackToSystem() {
        bindRequestWithHeader(null);
        assertThat(auditorAware.getCurrentAuditor()).contains("system");

        bindRequestWithHeader("   ");
        assertThat(auditorAware.getCurrentAuditor()).contains("system");
    }

    @Test
    void overlongHeaderIsTruncatedToColumnLength() {
        bindRequestWithHeader("x".repeat(80));

        assertThat(auditorAware.getCurrentAuditor().orElseThrow()).hasSize(RequestHeaderAuditorAware.MAX_LENGTH);
    }
}
