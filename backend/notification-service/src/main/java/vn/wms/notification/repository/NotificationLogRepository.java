package vn.wms.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.entity.NotificationStatus;

import java.util.List;
import java.util.Optional;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByStatus(NotificationStatus status);

    /** Truy vấn tự lọc theo doanh nghiệp hiện tại: khóa trùng của doanh nghiệp khác không ảnh hưởng. */
    boolean existsByDedupKey(String dedupKey);

    Optional<NotificationLog> findByDedupKey(String dedupKey);
}
