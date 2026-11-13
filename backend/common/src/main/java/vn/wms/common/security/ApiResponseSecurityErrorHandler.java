package vn.wms.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Trả lỗi 401 và 403 của Spring Security theo đúng khung {@link ApiResponse} của mọi API:
 * <ul>
 *   <li>401 {@link ErrorCode#UNAUTHORIZED}: request không có danh tính khi service không cho phép (máy chủ).</li>
 *   <li>403 {@link ErrorCode#FORBIDDEN}: vai trò không có trong {@code @PreAuthorize} của API. Lỗi phát sinh trong
 *       controller được {@code SecurityExceptionPassthroughAdvice} chuyển tiếp tới ExceptionTranslationFilter rồi về đây,
 *       nên không bị {@code GlobalExceptionHandler} gom thành 500.</li>
 * </ul>
 * Không bao giờ đưa thông điệp hay stack trace của framework vào thân phản hồi.
 */
public class ApiResponseSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    static final String UNAUTHORIZED_MESSAGE =
            "Chưa đăng nhập hoặc thiếu thông tin người dùng, vui lòng đăng nhập và gọi API qua cổng API";

    private final ObjectMapper objectMapper;

    public ApiResponseSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED, UNAUTHORIZED_MESSAGE);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.getDefaultMessage());
    }

    private void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(code, message));
    }
}
