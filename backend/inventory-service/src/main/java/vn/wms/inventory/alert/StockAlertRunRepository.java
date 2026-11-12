package vn.wms.inventory.alert;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.wms.common.messaging.event.StockAlertKind;

import java.time.LocalDate;
import java.util.List;

/** Dấu chống gửi trùng của tác vụ quét; truy vấn tự lọc theo doanh nghiệp hiện tại. */
public interface StockAlertRunRepository extends JpaRepository<StockAlertRun, Long> {

    boolean existsByAlertDateAndAlertKind(LocalDate alertDate, StockAlertKind alertKind);

    List<StockAlertRun> findByAlertDate(LocalDate alertDate);
}
