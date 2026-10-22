package vn.wms.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Khung phản hồi thống nhất cho mọi API của hệ thống WMS.
 *
 * <pre>
 * { "success": true,  "code": "OK",        "message": "Thành công",       "data": {...}, "timestamp": "..." }
 * { "success": false, "code": "NOT_FOUND", "message": "Không tìm thấy...", "timestamp": "..." }
 * </pre>
 *
 * @param success   true nếu xử lý thành công
 * @param code      "OK" khi thành công, ngược lại là tên {@link ErrorCode}
 * @param message   thông báo cho người dùng / lập trình viên frontend
 * @param data      dữ liệu trả về (có thể null, khi đó bị bỏ khỏi JSON)
 * @param timestamp thời điểm sinh phản hồi (UTC)
 * @param <T>       kiểu dữ liệu của {@code data}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String code, String message, T data, Instant timestamp) {

    public static final String OK_CODE = "OK";
    public static final String OK_MESSAGE = "Thành công";

    /** Thành công, có dữ liệu. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, OK_CODE, OK_MESSAGE, data, Instant.now());
    }

    /** Thành công, kèm thông báo riêng. */
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, OK_CODE, message, data, Instant.now());
    }

    /** Thành công, không có dữ liệu. */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, OK_CODE, OK_MESSAGE, null, Instant.now());
    }

    /** Thất bại, không có dữ liệu đi kèm. */
    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(false, code.name(), message, null, Instant.now());
    }

    /** Thất bại, kèm dữ liệu chi tiết (ví dụ danh sách lỗi theo từng trường). */
    public static <T> ApiResponse<T> error(ErrorCode code, String message, T data) {
        return new ApiResponse<>(false, code.name(), message, data, Instant.now());
    }
}
