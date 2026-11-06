package vn.wms.common.api;

/**
 * Mã lỗi chuẩn dùng chung cho mọi service. Mỗi mã gắn với một HTTP status và thông báo mặc định (tiếng Việt).
 * Client (frontend) dựa vào {@code code} để xử lý, không phụ thuộc vào nội dung thông báo.
 */
public enum ErrorCode {

    BAD_REQUEST(400, "Yêu cầu không hợp lệ"),
    VALIDATION_ERROR(400, "Dữ liệu không hợp lệ"),
    UNAUTHORIZED(401, "Chưa đăng nhập hoặc token không hợp lệ"),
    FORBIDDEN(403, "Bạn không có quyền thực hiện thao tác này"),
    NOT_FOUND(404, "Không tìm thấy dữ liệu"),
    METHOD_NOT_ALLOWED(405, "Phương thức HTTP không được hỗ trợ"),
    CONFLICT(409, "Dữ liệu bị xung đột hoặc đã tồn tại"),
    UNSUPPORTED_MEDIA_TYPE(415, "Định dạng nội dung không được hỗ trợ"),
    INTERNAL_ERROR(500, "Lỗi hệ thống, vui lòng thử lại sau"),
    SERVICE_UNAVAILABLE(503, "Dịch vụ phụ thuộc chưa sẵn sàng, vui lòng thử lại sau"),

    // --- Mã lỗi của mô hình SaaS đa doanh nghiệp (docs/SAAS_SPEC.md, mục 2.4). ---
    // Đặt sau các mã chung để fromHttpStatus(403) vẫn trả FORBIDDEN và fromHttpStatus(400) vẫn trả BAD_REQUEST.
    COMPANY_REQUIRED(403, "Không xác định được doanh nghiệp của yêu cầu"),
    COMPANY_INACTIVE(403, "Doanh nghiệp chưa được kích hoạt hoặc đang bị tạm khóa"),
    SUBSCRIPTION_EXPIRED(403, "Gói dịch vụ đã hết hạn, hệ thống đang ở chế độ chỉ đọc"),
    PLAN_LIMIT_EXCEEDED(403, "Đã đạt giới hạn của gói dịch vụ hiện tại"),
    PAYMENT_INVALID(400, "Thông tin thanh toán không hợp lệ"),
    TOKEN_INVALID(400, "Mã xác nhận không hợp lệ hoặc đã hết hạn");

    private final int httpStatus;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    /**
     * Suy ra mã lỗi từ HTTP status, dùng khi Spring MVC tự sinh lỗi (404, 405, 415, thiếu tham số, ...).
     * Status không có mã riêng sẽ quy về BAD_REQUEST (4xx) hoặc INTERNAL_ERROR (5xx).
     */
    public static ErrorCode fromHttpStatus(int status) {
        for (ErrorCode code : values()) {
            if (code != VALIDATION_ERROR && code.httpStatus == status) {
                return code;
            }
        }
        return status >= 500 ? INTERNAL_ERROR : BAD_REQUEST;
    }
}
