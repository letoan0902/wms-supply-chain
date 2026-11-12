package vn.wms.inventory.service.report;

import org.springframework.stereotype.Component;
import vn.wms.inventory.client.ProductInfo;
import vn.wms.inventory.entity.StockLedgerStart;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.StockLedgerStartRepository;
import vn.wms.inventory.repository.StockMovementRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Tra cứu dùng chung của các báo cáo kho (WMS-17.1, WMS-17.2): mốc bắt đầu sổ kho, thông tin hiển thị của sản phẩm
 * và kho. Chạy trong transaction của service gọi tới.
 */
@Component
public class ReportLookups {

    /** Mã, tên, đơn vị tính để hiển thị một sản phẩm; các trường có thể null. */
    public record ProductDisplay(String skuCode, String productName, String uom) {
        public static final ProductDisplay EMPTY = new ProductDisplay(null, null, null);
    }

    private final StockMovementRepository stockMovementRepository;
    private final StockLedgerStartRepository stockLedgerStartRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductCatalogLookup productCatalogLookup;

    public ReportLookups(StockMovementRepository stockMovementRepository,
                         StockLedgerStartRepository stockLedgerStartRepository,
                         WarehouseRepository warehouseRepository,
                         ProductCatalogLookup productCatalogLookup) {
        this.stockMovementRepository = stockMovementRepository;
        this.stockLedgerStartRepository = stockLedgerStartRepository;
        this.warehouseRepository = warehouseRepository;
        this.productCatalogLookup = productCatalogLookup;
    }

    /** Mốc bắt đầu sổ kho của doanh nghiệp hiện tại; null nếu sổ kho có từ đầu. */
    public Instant ledgerStartedAt() {
        return stockLedgerStartRepository.findFirstByOrderByStartedAtAsc()
                .map(StockLedgerStart::getStartedAt).orElse(null);
    }

    /** Ảnh chụp mã, tên mới nhất trong sổ kho; sản phẩm chưa có ảnh chụp thì hỏi product-service (bỏ qua nếu lỗi). */
    public Map<Long, ProductDisplay> productDisplay(Collection<Long> productIds) {
        Set<Long> ids = new HashSet<>(productIds);
        ids.remove(null);
        Map<Long, ProductDisplay> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (Object[] row : stockMovementRepository.findLatestProductSnapshots(ids)) {
            result.put((Long) row[0], new ProductDisplay((String) row[1], (String) row[2], (String) row[3]));
        }
        Set<Long> missing = new LinkedHashSet<>(ids);
        missing.removeAll(result.keySet());
        if (!missing.isEmpty()) {
            for (Map.Entry<Long, ProductInfo> e : productCatalogLookup.findDisplayInfo(missing).entrySet()) {
                ProductInfo info = e.getValue();
                result.put(e.getKey(), new ProductDisplay(info.sku(), info.name(),
                        info.uomName() != null ? info.uomName() : info.uomCode()));
            }
        }
        return result;
    }

    /** Kho theo ID (chỉ kho của doanh nghiệp hiện tại; kho đã xóa thì không có trong kết quả). */
    public Map<Long, Warehouse> warehouses(Collection<Long> warehouseIds) {
        Set<Long> ids = new HashSet<>(warehouseIds);
        ids.removeIf(Objects::isNull);
        Map<Long, Warehouse> result = new HashMap<>();
        if (!ids.isEmpty()) {
            warehouseRepository.findAllById(ids).forEach(w -> result.put(w.getId(), w));
        }
        return result;
    }
}
