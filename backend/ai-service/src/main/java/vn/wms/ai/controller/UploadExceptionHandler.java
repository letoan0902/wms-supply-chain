package vn.wms.ai.controller;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.ErrorCode;

/**
 * Lỗi tải file xảy ra trước khi vào controller (Spring tự từ chối file quá {@code max-file-size} hoặc request không
 * phải multipart). Mặc định {@code GlobalExceptionHandler} trả 413 với thông báo chung; ở đây trả 400 kèm thông báo
 * tiếng Việt rõ ràng như mọi lỗi file khác của trích xuất. Không gắn với controller cụ thể vì lỗi này xảy ra khi chưa
 * xác định được controller.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> tooLarge(MaxUploadSizeExceededException ex) {
        return badRequest("File vượt quá dung lượng cho phép (tối đa 10 MB).");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<Void>> malformed(MultipartException ex) {
        return badRequest("Yêu cầu tải file không hợp lệ: hãy gửi dạng multipart/form-data với trường file và "
                + "dataType.");
    }

    private static ResponseEntity<ApiResponse<Void>> badRequest(String message) {
        return ResponseEntity.status(ErrorCode.BAD_REQUEST.getHttpStatus())
                .body(ApiResponse.error(ErrorCode.BAD_REQUEST, message));
    }
}
