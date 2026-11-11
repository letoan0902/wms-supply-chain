package vn.wms.auth.account.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import vn.wms.auth.apikey.service.ApiKeyService;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Nhận yêu cầu công khai "gửi lại email kích hoạt" và "quên mật khẩu" (WMS-23.6).
 *
 * <p>Để phản hồi không để lộ email có tồn tại hay không, API luôn trả cùng một nội dung và việc tra cứu, phát mã,
 * phát sự kiện chạy ở luồng nền ({@code app.account-email.async=true}, mặc định): thời gian trả lời như nhau trong
 * mọi trường hợp. Mỗi email chỉ được xử lý tối đa một lần mỗi {@code app.account-email.resend-interval} (1 phút)
 * cho từng loại yêu cầu, đếm bằng khóa Redis {@code wms:auth:mail-throttle:{loại}:{sha256(email)}} (không lưu email
 * gốc trên Redis). Redis lỗi thì cho qua và ghi cảnh báo. Lỗi ở luồng nền chỉ ghi log, không bao giờ trả về client.
 */
@Slf4j
@Service
public class AccountMailRequestService implements DisposableBean {

    static final String THROTTLE_KEY_PREFIX = "wms:auth:mail-throttle:";
    static final String KIND_ACTIVATION = "activation";
    static final String KIND_PASSWORD_RESET = "password-reset";

    private final CompanyActivationService activationService;
    private final PasswordResetService passwordResetService;
    private final RedisTemplate<String, String> redisTemplate;
    private final AccountEmailProperties properties;
    private final ExecutorService executor;

    public AccountMailRequestService(CompanyActivationService activationService,
                                     PasswordResetService passwordResetService,
                                     RedisTemplate<String, String> redisTemplate,
                                     AccountEmailProperties properties) {
        this.activationService = activationService;
        this.passwordResetService = passwordResetService;
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.executor = properties.async() ? newExecutor() : null;
    }

    /** Xin gửi lại liên kết kích hoạt; luôn trả về ngay, không ném lỗi nghiệp vụ. */
    public void requestActivationResend(String email) {
        String normalized = normalize(email);
        submit(KIND_ACTIVATION, normalized, () -> activationService.resend(normalized));
    }

    /** Quên mật khẩu; luôn trả về ngay, không ném lỗi nghiệp vụ. */
    public void requestPasswordReset(String email) {
        String normalized = normalize(email);
        submit(KIND_PASSWORD_RESET, normalized, () -> passwordResetService.sendResetLink(normalized));
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private void submit(String kind, String normalizedEmail, Runnable work) {
        Runnable task = () -> {
            try {
                if (acquireSlot(kind, normalizedEmail)) {
                    work.run();
                }
            } catch (RuntimeException ex) {
                // Không ghi email hay mã ra log: chỉ loại yêu cầu và lỗi
                log.error("Không xử lý được yêu cầu email tài khoản loại {}: {}", kind, ex.getMessage(), ex);
            }
        };
        if (executor == null) {
            task.run();
            return;
        }
        try {
            executor.execute(task);
        } catch (RejectedExecutionException ex) {
            log.warn("Hàng đợi yêu cầu email tài khoản đầy, bỏ qua một yêu cầu loại {}", kind);
        }
    }

    /** {@code true} nếu email chưa được xử lý trong khoảng giới hạn; đồng thời giữ chỗ cho khoảng kế tiếp. */
    private boolean acquireSlot(String kind, String normalizedEmail) {
        if (normalizedEmail.isEmpty()) {
            return false;
        }
        String key = THROTTLE_KEY_PREFIX + kind + ":" + ApiKeyService.sha256Hex(normalizedEmail);
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "1", properties.resendInterval());
            if (!Boolean.TRUE.equals(acquired)) {
                log.info("Bỏ qua yêu cầu email tài khoản loại {}: cùng email đã yêu cầu trong vòng {} giây", kind,
                        properties.resendInterval().toSeconds());
                return false;
            }
            return true;
        } catch (RuntimeException ex) {
            log.warn("Không kiểm tra được giới hạn gửi email trên Redis, vẫn xử lý: {}", ex.getMessage());
            return true;
        }
    }

    private static ExecutorService newExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        // Hai luồng, hàng đợi tối đa 200 yêu cầu: đủ cho lưu lượng đăng ký, không để kẻ xấu làm cạn bộ nhớ
        ThreadPoolExecutor pool = new ThreadPoolExecutor(2, 2, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(200),
                runnable -> {
                    Thread thread = new Thread(runnable, "account-mail-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    @Override
    public void destroy() throws InterruptedException {
        if (executor != null) {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        }
    }
}
