package vn.wms.common.persistence;

import org.springframework.data.domain.AuditorAware;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * Xác định người thực hiện cho các cột created_by / updated_by: lấy tên người dùng từ header X-User-Name
 * (api-gateway thêm sau khi kiểm tra JWT). Ngoài ngữ cảnh HTTP (consumer RabbitMQ, job định kỳ) hoặc khi
 * không có header thì ghi "system".
 *
 * <p>Lưu ý: chỉ nên tin header này khi service chỉ nhận request từ gateway (mạng nội bộ).
 */
public class RequestHeaderAuditorAware implements AuditorAware<String> {

    public static final String USER_HEADER = "X-User-Name";
    public static final String SYSTEM_AUDITOR = "system";
    /** Độ dài tối đa của cột created_by / updated_by. */
    static final int MAX_LENGTH = 50;

    @Override
    public Optional<String> getCurrentAuditor() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            String user = servletAttributes.getRequest().getHeader(USER_HEADER);
            if (user != null && !user.isBlank()) {
                String trimmed = user.trim();
                return Optional.of(trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed);
            }
        }
        return Optional.of(SYSTEM_AUDITOR);
    }
}
