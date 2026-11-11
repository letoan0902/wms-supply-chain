package vn.wms.auth.account.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Phần không cần Spring của WMS-23.6: dựng liên kết, sinh mã và xử lý nền của yêu cầu gửi email. */
class AccountEmailUnitTest {

    @Test
    void frontendBaseUrlComesFromSettingThenFromPayOsReturnUrl() {
        assertThat(FrontendLinks.resolveBaseUrl("https://wms.example.vn/", null)).isEqualTo("https://wms.example.vn");
        assertThat(FrontendLinks.resolveBaseUrl("  ", "http://localhost/payment/result")).isEqualTo("http://localhost");
        assertThat(FrontendLinks.resolveBaseUrl(null, "https://wms.example.vn:8443/payment/result"))
                .isEqualTo("https://wms.example.vn:8443");
        assertThat(FrontendLinks.resolveBaseUrl("", "")).isEqualTo("http://localhost:5173");
    }

    @Test
    void rawTokensAreLongUrlSafeAndUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String token = AccountTokenService.newRawToken();
            // 32 byte Base64 không đệm = 43 ký tự trong bảng chữ URL-safe
            assertThat(token).hasSize(43).matches("^[A-Za-z0-9_-]+$");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(200);
        assertThat(AccountTokenService.hash("abc")).hasSize(64).isNotEqualTo("abc");
    }

    @Test
    @SuppressWarnings("unchecked")
    void asyncModeAnswersImmediatelyAndRunsWorkOnBackgroundThread() throws Exception {
        CompanyActivationService activationService = mock(CompanyActivationService.class);
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> worker = new AtomicReference<>();
        doAnswer(invocation -> {
            worker.set(Thread.currentThread().getName());
            done.countDown();
            return null;
        }).when(passwordResetService).sendResetLink("nguoi.dung@wms.vn");

        AccountMailRequestService service = new AccountMailRequestService(activationService, passwordResetService,
                redis, new AccountEmailProperties(true, Duration.ofHours(24), Duration.ofMinutes(30),
                Duration.ofMinutes(1)));
        try {
            service.requestPasswordReset("  Nguoi.Dung@WMS.vn ");
            assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(worker.get()).startsWith("account-mail-").isNotEqualTo(Thread.currentThread().getName());
        } finally {
            service.destroy();
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void failuresAndRedisOutagesNeverReachTheCaller() {
        CompanyActivationService activationService = mock(CompanyActivationService.class);
        PasswordResetService passwordResetService = mock(PasswordResetService.class);
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("Redis không chạy"));
        doThrow(new IllegalStateException("cơ sở dữ liệu lỗi")).when(activationService).resend(anyString());

        AccountMailRequestService service = new AccountMailRequestService(activationService, passwordResetService,
                redis, new AccountEmailProperties(false, Duration.ofHours(24), Duration.ofMinutes(30),
                Duration.ofMinutes(1)));

        // Redis lỗi thì vẫn xử lý (cho qua), lỗi nghiệp vụ chỉ ghi log
        service.requestActivationResend("a@wms.vn");
        org.mockito.Mockito.verify(activationService).resend("a@wms.vn");
    }
}
