package vn.wms.common.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bộ xử lý lỗi toàn cục: mọi lỗi đều được trả về dưới dạng {@link ApiResponse} thống nhất.
 * Kế thừa {@link ResponseEntityExceptionHandler} để các lỗi do Spring MVC tự sinh
 * (404, 405, 415, thiếu tham số, JSON sai định dạng, ...) cũng được chuẩn hóa cùng một khung.
 *
 * <p>Bean này được đăng ký tự động bởi {@code WmsCommonAutoConfiguration}, service chỉ cần thêm
 * {@code implementation project(':common')}.
 *
 * <p>TODO: bổ sung xử lý DataIntegrityViolationException (trùng khóa duy nhất) trả về 409 CONFLICT
 * khi các service bắt đầu ghi dữ liệu.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Lỗi nghiệp vụ chủ động ném ra từ service. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException ex) {
        if (ex.getHttpStatus() >= 500) {
            log.error("Business error {}: {}", ex.getErrorCode(), ex.getMessage(), ex);
        } else {
            log.debug("Business error {}: {}", ex.getErrorCode(), ex.getMessage());
        }
        return ResponseEntity.status(ex.getHttpStatus()).body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    /** Vi phạm ràng buộc khi dùng {@code @Validated} trên tham số (query param, path variable). */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code, code.getDefaultMessage(), errors));
    }

    /** Mọi lỗi không lường trước: ghi log đầy đủ nhưng chỉ trả thông báo chung, không lộ chi tiết nội bộ. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.error(code, code.getDefaultMessage()));
    }

    /** Lỗi {@code @Valid} trên request body: trả danh sách lỗi theo từng trường trong {@code data}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        ErrorCode code = ErrorCode.VALIDATION_ERROR;
        return new ResponseEntity<>(ApiResponse.error(code, code.getDefaultMessage(), errors), headers, status);
    }

    /**
     * Chuẩn hóa body của các lỗi Spring MVC tự sinh (vốn là ProblemDetail, có thể null) về {@link ApiResponse}.
     * Thông báo luôn bằng tiếng Việt, không chuyển tiếp câu chữ tiếng Anh của framework.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ErrorCode code = ErrorCode.fromHttpStatus(statusCode.value());
        return new ResponseEntity<>(ApiResponse.error(code, messageFor(ex, code)), headers, statusCode);
    }

    private static String messageFor(Exception ex, ErrorCode code) {
        if (ex instanceof MissingServletRequestParameterException missing) {
            return "Thiếu tham số bắt buộc: " + missing.getParameterName();
        }
        if (ex instanceof TypeMismatchException mismatch && mismatch.getPropertyName() != null) {
            return "Giá trị của tham số " + mismatch.getPropertyName() + " không hợp lệ";
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return "Nội dung yêu cầu không đúng định dạng";
        }
        return code.getDefaultMessage();
    }
}
