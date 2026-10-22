package vn.wms.common.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Để lỗi của Spring Security (401/403 phát sinh trong controller, ví dụ từ {@code @PreAuthorize})
 * đi tiếp tới ExceptionTranslationFilter thay vì bị {@link GlobalExceptionHandler} gom thành 500.
 * Chỉ được đăng ký khi Spring Security có trên classpath (xem {@code WmsCommonAutoConfiguration}).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionPassthroughAdvice {

    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrow(RuntimeException ex) {
        throw ex;
    }
}
