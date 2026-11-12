package vn.wms.inventory.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.repository.StockItemRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tác vụ quét cảnh báo tồn kho hằng ngày (WMS-15.1), mặc định 00:00 giờ Việt Nam ({@code app.alerts.cron},
 * {@code app.alerts.zone}). Luồng @Scheduled không có doanh nghiệp nên lấy danh sách doanh nghiệp đang có tồn bằng
 * SQL gốc ({@link StockItemRepository#findAllCompanyIds()}) rồi quét lần lượt từng doanh nghiệp trong
 * {@code TenantContext.callAs}. Một doanh nghiệp lỗi chỉ ghi log, không làm dừng các doanh nghiệp còn lại.
 * Chạy trên nhiều instance cùng lúc vẫn không gửi trùng nhờ dấu trong {@code stock_alert_runs}.
 */
@Component
public class StockAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(StockAlertScheduler.class);

    private final StockItemRepository stockItemRepository;
    private final StockAlertScanService scanService;
    private final StockAlertProperties properties;

    public StockAlertScheduler(StockItemRepository stockItemRepository, StockAlertScanService scanService,
                               StockAlertProperties properties) {
        this.stockItemRepository = stockItemRepository;
        this.scanService = scanService;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.alerts.cron:0 0 0 * * *}", zone = "${app.alerts.zone:Asia/Ho_Chi_Minh}")
    public void scheduledScan() {
        if (!properties.enabled()) {
            log.debug("Tác vụ quét cảnh báo tồn kho đang tắt (app.alerts.enabled=false)");
            return;
        }
        scanAllCompanies();
    }

    /** @return kết quả theo doanh nghiệp (doanh nghiệp lỗi không có trong kết quả) */
    public Map<Long, StockAlertScanResponse> scanAllCompanies() {
        List<Long> companyIds = stockItemRepository.findAllCompanyIds();
        Map<Long, StockAlertScanResponse> results = new LinkedHashMap<>();
        for (Long companyId : companyIds) {
            if (companyId == null) {
                continue;
            }
            try {
                results.put(companyId, TenantContext.callAs(companyId,
                        () -> scanService.scanCurrentCompany(StockAlertScanService.SCHEDULER)));
            } catch (RuntimeException ex) {
                log.error("Quét cảnh báo tồn kho cho doanh nghiệp {} thất bại, chuyển sang doanh nghiệp tiếp theo",
                        companyId, ex);
            }
        }
        return results;
    }
}
