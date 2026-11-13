package vn.wms.inventory.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.dto.AutoLockResponse;
import vn.wms.inventory.repository.StockItemRepository;

import java.util.List;

/**
 * Tác vụ định kỳ khóa lô hết hạn cho mọi doanh nghiệp (WMS-10.2, WMS-22.4a).
 *
 * <p>Luồng @Scheduled không có doanh nghiệp trong TenantContext nên truy vấn không trả dòng nào
 * (docs/MULTI_TENANT.md mục 3). Vì vậy phương thức ở đây KHÔNG đặt @Transactional: lấy danh sách doanh nghiệp
 * bằng SQL gốc, rồi với từng doanh nghiệp gọi {@link LotService#autoLockExpiredLots()} qua proxy của Spring
 * bên trong TenantContext.runAs, để transaction mở sau khi đã đặt doanh nghiệp.
 */
@Component
public class ExpiredLotScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiredLotScheduler.class);

    private final StockItemRepository stockItemRepository;
    private final LotService lotService;

    public ExpiredLotScheduler(StockItemRepository stockItemRepository, LotService lotService) {
        this.stockItemRepository = stockItemRepository;
        this.lotService = lotService;
    }

    /**
     * Chạy lúc 01:00 AM mỗi ngày. Một doanh nghiệp lỗi chỉ ghi log, không làm dừng các doanh nghiệp còn lại.
     *
     * @return tổng số lô đã khóa của mọi doanh nghiệp
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public int autoLockExpiredLotsForAllCompanies() {
        List<Long> companyIds = stockItemRepository.findAllCompanyIds();
        int totalLocked = 0;
        for (Long companyId : companyIds) {
            if (companyId == null) {
                continue;
            }
            try {
                AutoLockResponse result = TenantContext.callAs(companyId, lotService::autoLockExpiredLots);
                totalLocked += result.lockedCount();
                log.info("Khóa lô hết hạn cho doanh nghiệp {}: {} lô", companyId, result.lockedCount());
            } catch (RuntimeException ex) {
                log.error("Khóa lô hết hạn cho doanh nghiệp {} thất bại, chuyển sang doanh nghiệp tiếp theo", companyId, ex);
            }
        }
        return totalLocked;
    }
}
