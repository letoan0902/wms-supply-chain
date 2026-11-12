package vn.wms.auth.internal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.auth.entity.Role;
import vn.wms.auth.entity.User;
import vn.wms.common.constant.Roles;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Chọn người nhận email cảnh báo tồn kho (WMS-15.2): quản lý của kho có cảnh báo và admin của doanh nghiệp, tài khoản
 * đang hoạt động, có địa chỉ email gửi được. Luôn lọc theo doanh nghiệp được truyền vào.
 */
@Service
public class AlertRecipientService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /**
     * Tên miền dành riêng cho thử nghiệm hoặc mạng nội bộ (RFC 2606, RFC 6761), thư gửi tới đó không bao giờ tới nơi;
     * tài khoản mẫu như {@code admin_b@wms.local} bị bỏ qua.
     */
    private static final List<String> UNDELIVERABLE_SUFFIXES = List.of(
            ".local", ".localhost", ".invalid", ".test", ".example",
            "@example.com", "@example.org", "@example.net", ".example.com", ".example.org", ".example.net");

    private final AlertRecipientRepository repository;

    public AlertRecipientService(AlertRecipientRepository repository) {
        this.repository = repository;
    }

    /**
     * @param companyId   doanh nghiệp sở hữu tồn kho (bắt buộc)
     * @param warehouseId kho có cảnh báo; null thì chỉ trả admin
     */
    @Transactional(readOnly = true)
    public List<AlertRecipientResponse> findRecipients(Long companyId, Long warehouseId) {
        return repository.findAlertRecipients(companyId, warehouseId).stream()
                .filter(user -> isDeliverable(user.getEmail()))
                .map(user -> new AlertRecipientResponse(user.getFullName(), user.getEmail().trim(), primaryRole(user)))
                .sorted(Comparator.comparing((AlertRecipientResponse r) -> Roles.ADMIN.equals(r.role()) ? 0 : 1)
                        .thenComparing(AlertRecipientResponse::email))
                .toList();
    }

    private static String primaryRole(User user) {
        Set<String> names = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        return names.contains(Roles.ADMIN) ? Roles.ADMIN : Roles.WAREHOUSE_MANAGER;
    }

    static boolean isDeliverable(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(normalized).matches()) {
            return false;
        }
        return UNDELIVERABLE_SUFFIXES.stream().noneMatch(normalized::endsWith);
    }
}
