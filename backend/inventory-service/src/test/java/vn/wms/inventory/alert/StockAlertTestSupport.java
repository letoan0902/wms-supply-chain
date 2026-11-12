package vn.wms.inventory.alert;

import org.springframework.jdbc.core.JdbcTemplate;
import vn.wms.common.api.ApiResponse;
import vn.wms.common.api.PageResponse;
import vn.wms.common.tenant.TenantContext;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.Warehouse;
import vn.wms.inventory.repository.BinLocationRepository;
import vn.wms.inventory.repository.StockItemRepository;
import vn.wms.inventory.repository.WarehouseRepository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Dựng dữ liệu tồn kho cho test cảnh báo; mọi thao tác chạy trong ngữ cảnh doanh nghiệp được truyền vào. */
final class StockAlertTestSupport {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final WarehouseRepository warehouseRepository;
    private final BinLocationRepository binLocationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockAlertRunRepository runRepository;
    private final JdbcTemplate jdbcTemplate;

    StockAlertTestSupport(WarehouseRepository warehouseRepository, BinLocationRepository binLocationRepository,
                          StockItemRepository stockItemRepository, StockAlertRunRepository runRepository,
                          JdbcTemplate jdbcTemplate) {
        this.warehouseRepository = warehouseRepository;
        this.binLocationRepository = binLocationRepository;
        this.stockItemRepository = stockItemRepository;
        this.runRepository = runRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Tạo kho kèm một ô kệ, trả id của ô kệ. */
    long warehouseWithBin(Long companyId, String code, String name) {
        return TenantContext.callAs(companyId, () -> {
            Warehouse warehouse = warehouseRepository.save(new Warehouse(code, name, "Địa chỉ", "Quản lý", "0900000000", 100));
            return binLocationRepository.save(new BinLocation(warehouse, "Z1", "R1", "B1", new BigDecimal("1000"))).getId();
        });
    }

    long warehouseIdOfBin(Long companyId, long binId) {
        return TenantContext.callAs(companyId, () -> binLocationRepository.findById(binId).orElseThrow().getWarehouse().getId());
    }

    long stock(Long companyId, long binId, long productId, String lotNo, String quantity, String reserved,
               LocalDate expDate, boolean locked) {
        return TenantContext.callAs(companyId, () -> {
            StockItem item = new StockItem();
            item.setBinLocation(binLocationRepository.findById(binId).orElseThrow());
            item.setProductId(productId);
            item.setLotNo(lotNo);
            item.setQuantity(new BigDecimal(quantity));
            item.setReservedQuantity(new BigDecimal(reserved));
            item.setExpDate(expDate);
            item.setLocked(locked);
            return stockItemRepository.save(item).getId();
        });
    }

    /** Đặt thời điểm thay đổi gần nhất của dòng tồn vào giữa trưa (giờ Việt Nam) của một ngày. */
    void lastChangedOn(long stockItemId, LocalDate date) {
        Instant instant = date.atTime(LocalTime.NOON).atZone(ZONE).toInstant();
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        jdbcTemplate.update(con -> {
            var ps = con.prepareStatement("UPDATE stock_items SET updated_at = ? WHERE id = ?");
            ps.setTimestamp(1, Timestamp.from(instant), utc);
            ps.setLong(2, stockItemId);
            return ps;
        });
    }

    void cleanUp(Long... companyIds) {
        for (Long companyId : companyIds) {
            TenantContext.runAs(companyId, () -> {
                runRepository.deleteAll();
                stockItemRepository.deleteAll();
                binLocationRepository.deleteAll();
                warehouseRepository.deleteAll();
            });
        }
    }

    static AlertProductInfo product(long id, String sku, String safety, String reorder) {
        return new AlertProductInfo(id, sku, "Sản phẩm " + sku, safety == null ? null : new BigDecimal(safety),
                reorder == null ? null : new BigDecimal(reorder), true);
    }

    static ApiResponse<PageResponse<AlertProductInfo>> page(AlertProductInfo... products) {
        return ApiResponse.ok(PageResponse.of(List.of(products), 0, 100, products.length));
    }
}
