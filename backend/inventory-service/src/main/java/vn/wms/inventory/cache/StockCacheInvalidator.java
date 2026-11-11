package vn.wms.inventory.cache;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.wms.common.tenant.TenantContext;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Gom các sản phẩm có tồn thay đổi trong một giao dịch và vô hiệu bộ nhớ đệm của chúng SAU KHI giao dịch commit
 * (WMS-19.1). Giao dịch rollback thì không vô hiệu gì vì tồn không đổi.
 *
 * <p>Điểm gọi duy nhất là {@link StockItemCacheListener} (callback JPA trên {@code StockItem}), nên mọi đường thay đổi
 * {@code stock_items} đều được bắt mà không phải sửa từng service: nhập kho theo phiếu GRN, trừ tồn và xuất hàng, giữ
 * chỗ và giải phóng giữ chỗ, xuất và nhận điều chuyển, duyệt điều chỉnh sau kiểm kê, khóa và mở khóa lô, tự khóa lô
 * hết hạn. Gọi ngoài giao dịch thì vô hiệu ngay.
 */
@Component
public class StockCacheInvalidator {

    private final StockTotalsCache cache;

    public StockCacheInvalidator(StockTotalsCache cache) {
        this.cache = cache;
    }

    /** Ghi nhận tồn của một sản phẩm thuộc doanh nghiệp vừa thay đổi. */
    public void stockChanged(Long companyId, Long productId) {
        if (!cache.isEnabled() || productId == null) {
            return;
        }
        Long company = companyId != null ? companyId : TenantContext.getCompanyId();
        if (company == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            cache.evict(company, Set.of(productId));
            return;
        }
        PendingEvictions pending = (PendingEvictions) TransactionSynchronizationManager.getResource(this);
        if (pending == null) {
            pending = new PendingEvictions();
            TransactionSynchronizationManager.bindResource(this, pending);
            TransactionSynchronizationManager.registerSynchronization(pending);
        }
        pending.add(company, productId);
    }

    /** Danh sách sản phẩm cần vô hiệu của một giao dịch, gắn vào giao dịch đó qua {@link TransactionSynchronizationManager}. */
    private final class PendingEvictions implements TransactionSynchronization {

        private final Map<Long, Set<Long>> productsByCompany = new LinkedHashMap<>();

        void add(Long companyId, Long productId) {
            productsByCompany.computeIfAbsent(companyId, k -> new LinkedHashSet<>()).add(productId);
        }

        @Override
        public void afterCommit() {
            productsByCompany.forEach(cache::evict);
        }

        @Override
        public void afterCompletion(int status) {
            TransactionSynchronizationManager.unbindResourceIfPossible(StockCacheInvalidator.this);
        }
    }
}
