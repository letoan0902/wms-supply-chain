package vn.wms.common.exception;

import vn.wms.common.api.ErrorCode;

/**
 * Lỗi nghiệp vụ chủ động ném ra từ tầng service. {@link GlobalExceptionHandler} sẽ chuyển thành
 * phản hồi JSON chuẩn với HTTP status tương ứng của {@link ErrorCode}.
 *
 * <pre>
 * throw ApiException.notFound("Không tìm thấy kho có mã " + code);
 * throw ApiException.withData(ErrorCode.PLAN_LIMIT_EXCEEDED, message, violation); // kèm "data" trong phản hồi
 * </pre>
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    /** Dữ liệu chi tiết trả về trong trường {@code data} của phản hồi lỗi, có thể null. */
    private final transient Object data;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null, null);
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public ApiException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, null, cause);
    }

    private ApiException(ErrorCode errorCode, String message, Object data, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.data = data;
    }

    /** Lỗi nghiệp vụ kèm dữ liệu chi tiết, {@link GlobalExceptionHandler} trả nó trong trường {@code data}. */
    public static ApiException withData(ErrorCode errorCode, String message, Object data) {
        return new ApiException(errorCode, message, data, null);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Dữ liệu chi tiết của lỗi, {@code null} nếu không có. */
    public Object getData() {
        return data;
    }

    public int getHttpStatus() {
        return errorCode.getHttpStatus();
    }

    public static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.BAD_REQUEST, message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(ErrorCode.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.CONFLICT, message);
    }
}
