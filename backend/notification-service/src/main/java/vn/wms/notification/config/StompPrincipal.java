package vn.wms.notification.config;

import java.security.Principal;

/**
 * Danh tính của một phiên WebSocket sau frame CONNECT: tên đăng nhập và doanh nghiệp lấy từ claim {@code companyId}
 * của JWT đã kiểm tra chữ ký (hoặc doanh nghiệp mặc định của máy phát triển khi tắt xác thực). Không bao giờ lấy từ
 * giá trị client tự khai báo.
 *
 * @param name      tên đăng nhập (claim {@code sub})
 * @param companyId ID doanh nghiệp, quyết định các kênh {@code /topic/company/{companyId}/**} được phép đăng ký
 */
public record StompPrincipal(String name, Long companyId) implements Principal {

    @Override
    public String getName() {
        return name;
    }
}
