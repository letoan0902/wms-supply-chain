package vn.wms.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.notification.entity.NotificationLog;
import vn.wms.notification.entity.NotificationStatus;

import java.util.List;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

    List<NotificationLog> findByStatus(NotificationStatus status);
}
