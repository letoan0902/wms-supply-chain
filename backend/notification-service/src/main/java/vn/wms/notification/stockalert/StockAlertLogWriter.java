package vn.wms.notification.stockalert;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.repository.NotificationLogRepository;

/** Ghi nhật ký cảnh báo trong transaction riêng; kiểm tra khóa chống trùng trước để lần giao lại không lỗi. */
@Component
public class StockAlertLogWriter {

    private final NotificationLogRepository repository;

    public StockAlertLogWriter(NotificationLogRepository repository) {
        this.repository = repository;
    }

    /** @return false nếu khóa chống trùng đã có trong doanh nghiệp hiện tại */
    @Transactional
    public boolean saveIfAbsent(NotificationLog entry) {
        if (entry.getDedupKey() != null && repository.existsByDedupKey(entry.getDedupKey())) {
            return false;
        }
        repository.saveAndFlush(entry);
        return true;
    }
}
