package vn.wms.inventory.alert;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.messaging.event.StockAlertKind;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Giữ chỗ (doanh nghiệp, ngày, loại) trong bảng {@code stock_alert_runs}, mỗi thao tác một transaction riêng để dấu được
 * ghi xuống ngay, độc lập với phần phát sự kiện. Người gọi phải đặt TenantContext trước.
 *
 * <p>Hai instance cùng giữ chỗ một lúc: một bên ghi được, bên kia nhận {@code DataIntegrityViolationException} từ
 * ràng buộc duy nhất; người gọi coi trường hợp đó như "đã có lần chạy khác".
 */
@Component
public class StockAlertRunGuard {

    private final StockAlertRunRepository repository;

    public StockAlertRunGuard(StockAlertRunRepository repository) {
        this.repository = repository;
    }

    /** @return id của dấu vừa ghi, rỗng nếu ngày này đã có lần chạy cho loại này */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> claim(LocalDate alertDate, StockAlertKind kind, String triggeredBy) {
        if (repository.existsByAlertDateAndAlertKind(alertDate, kind)) {
            return Optional.empty();
        }
        StockAlertRun run = new StockAlertRun(alertDate, kind, truncate(triggeredBy, 50));
        return Optional.of(repository.saveAndFlush(run).getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long runId, int warehouseCount, int itemCount, int emailCount, String note) {
        StockAlertRun run = repository.findById(runId).orElseThrow();
        run.setStatus(StockAlertRun.STATUS_COMPLETED);
        run.setWarehouseCount(warehouseCount);
        run.setItemCount(itemCount);
        run.setEmailCount(emailCount);
        run.setNote(truncate(note, 500));
        repository.save(run);
    }

    /** Bỏ dấu khi phát sự kiện lỗi, để lần quét sau trong ngày được thử lại. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(Long runId) {
        if (repository.existsById(runId)) {
            repository.deleteById(runId);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
