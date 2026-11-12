package vn.wms.inventory.alert;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.wms.common.messaging.event.StockAlertKind;
import vn.wms.common.messaging.event.StockAlertSeverity;
import vn.wms.inventory.entity.BinLocation;
import vn.wms.inventory.entity.StockItem;
import vn.wms.inventory.entity.Warehouse;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tính các cảnh báo tồn kho của doanh nghiệp hiện tại từ bảng {@code stock_items} (nguồn tồn mà trừ tồn, giữ chỗ và
 * ATP đang dùng; {@code inventory_lots} chỉ là bản sao theo lô nên không cộng thêm để tránh đếm hai lần).
 *
 * <ul>
 *   <li>EXPIRED: dòng còn hàng ({@code quantity > 0}) có hạn dùng nhỏ hơn hoặc bằng hôm nay (cùng quy ước với khóa lô
 *       hết hạn: hết hạn ngay trong ngày ghi trên lô). Mức CRITICAL.</li>
 *   <li>NEAR_EXPIRY: dòng còn hàng có hạn dùng sau hôm nay và không quá hôm nay cộng {@code near-expiry-days}
 *       (mặc định 30, theo tài liệu yêu cầu). Mức WARNING.</li>
 *   <li>DEAD_STOCK: mặt hàng còn tồn trong kho, lần biến động gần nhất (xem {@link LastMovementReader}) cách hôm nay
 *       từ {@code dead-stock-days} ngày trở lên (mặc định 90). Mức INFO.</li>
 *   <li>LOW_STOCK: tồn dùng được của mặt hàng tại kho (bỏ lô khóa và lô hết hạn, giống ATP) nhỏ hơn hoặc bằng mức tồn
 *       an toàn thì CRITICAL, nếu không mà nhỏ hơn hoặc bằng điểm đặt hàng lại thì WARNING. Ngưỡng bằng 0 nghĩa là chưa
 *       cấu hình và bị bỏ qua. Chỉ xét các cặp (kho, sản phẩm) đã từng có dòng tồn trong kho đó, và chỉ khi đọc được
 *       danh mục sản phẩm.</li>
 * </ul>
 */
@Component
public class StockAlertDetector {

    private static final DateTimeFormatter DATE_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<Long> NO_WAREHOUSE = List.of(-1L);

    /** Thứ tự hiển thị: khẩn cấp trước, rồi theo loại, hạn gần trước, tồn lâu hơn trước, cuối cùng theo SKU. */
    public static final Comparator<StockAlertResponse> ORDER = Comparator
            .comparingInt((StockAlertResponse a) -> a.severity().rank())
            .thenComparingInt(a -> kindRank(a.kind()))
            .thenComparing(a -> a.daysLeft() == null ? Long.MAX_VALUE : a.daysLeft())
            .thenComparing(a -> a.daysWithoutMovement() == null ? 0L : -a.daysWithoutMovement())
            .thenComparing(a -> a.quantity() == null ? BigDecimal.ZERO : a.quantity())
            .thenComparing(a -> a.skuCode() == null ? "" : a.skuCode())
            .thenComparing(StockAlertResponse::id);

    private final StockAlertQueryRepository queryRepository;
    private final LastMovementReader lastMovementReader;
    private final StockAlertProperties properties;

    public StockAlertDetector(StockAlertQueryRepository queryRepository, LastMovementReader lastMovementReader,
                              StockAlertProperties properties) {
        this.queryRepository = queryRepository;
        this.lastMovementReader = lastMovementReader;
        this.properties = properties;
    }

    /**
     * Phải gọi khi TenantContext đã có doanh nghiệp (request HTTP hoặc bên trong {@code TenantContext.runAs}).
     *
     * @param today          ngày tính hạn dùng
     * @param warehouseScope các kho được xét; rỗng là mọi kho của doanh nghiệp
     * @param products       danh mục sản phẩm (không có thì bỏ qua LOW_STOCK)
     * @return mọi cảnh báo, đã sắp xếp theo {@link #ORDER}
     */
    @Transactional(readOnly = true)
    public List<StockAlertResponse> detect(LocalDate today, Set<Long> warehouseScope, ProductLookup products) {
        boolean all = warehouseScope == null || warehouseScope.isEmpty();
        List<StockItem> rows = queryRepository.findStockRows(all, all ? NO_WAREHOUSE : warehouseScope);
        Map<WarehouseProductKey, Instant> lastMovements = lastMovementReader.lastMovements(rows);
        ZoneId zone = properties.zoneId();

        List<StockAlertResponse> alerts = new ArrayList<>();
        Map<WarehouseProductKey, Position> positions = new LinkedHashMap<>();
        for (StockItem row : rows) {
            BinLocation bin = row.getBinLocation();
            Warehouse warehouse = bin.getWarehouse();
            BigDecimal quantity = nz(row.getQuantity());
            WarehouseProductKey key = new WarehouseProductKey(warehouse.getId(), row.getProductId());
            positions.computeIfAbsent(key, k -> new Position(warehouse)).add(row, today);

            if (quantity.signum() > 0 && row.getExpDate() != null) {
                long daysLeft = ChronoUnit.DAYS.between(today, row.getExpDate());
                if (daysLeft <= 0) {
                    alerts.add(lotAlert(StockAlertKind.EXPIRED, StockAlertSeverity.CRITICAL, row, daysLeft, products));
                } else if (daysLeft <= properties.nearExpiryDays()) {
                    alerts.add(lotAlert(StockAlertKind.NEAR_EXPIRY, StockAlertSeverity.WARNING, row, daysLeft,
                            products));
                }
            }
        }

        for (Map.Entry<WarehouseProductKey, Position> entry : positions.entrySet()) {
            WarehouseProductKey key = entry.getKey();
            Position position = entry.getValue();
            AlertProductInfo product = products.get(key.productId());

            Instant lastMovement = lastMovements.get(key);
            if (position.onHand.signum() > 0 && lastMovement != null) {
                long idleDays = ChronoUnit.DAYS.between(lastMovement.atZone(zone).toLocalDate(), today);
                if (idleDays >= properties.deadStockDays()) {
                    alerts.add(deadStockAlert(key, position, product, lastMovement, idleDays));
                }
            }

            if (products.available() && product != null && !Boolean.FALSE.equals(product.active())) {
                BigDecimal safety = nz(product.safetyStock());
                BigDecimal reorder = nz(product.reorderPoint());
                if (safety.signum() > 0 && position.usable.compareTo(safety) <= 0) {
                    alerts.add(lowStockAlert(key, position, product, StockAlertSeverity.CRITICAL));
                } else if (reorder.signum() > 0 && position.usable.compareTo(reorder) <= 0) {
                    alerts.add(lowStockAlert(key, position, product, StockAlertSeverity.WARNING));
                }
            }
        }

        alerts.sort(ORDER);
        return alerts;
    }

    private StockAlertResponse lotAlert(StockAlertKind kind, StockAlertSeverity severity, StockItem row,
                                        long daysLeft, ProductLookup products) {
        BinLocation bin = row.getBinLocation();
        Warehouse warehouse = bin.getWarehouse();
        AlertProductInfo product = products.get(row.getProductId());
        String sku = product != null ? product.sku() : null;
        String name = product != null ? product.name() : null;
        String lotLabel = row.getLotNo() != null && !row.getLotNo().isBlank()
                ? "Lô hàng " + row.getLotNo() : "Lô hàng không số lô";
        String message = kind == StockAlertKind.EXPIRED
                ? String.format("%s của sản phẩm %s tại %s đã hết hạn sử dụng từ ngày %s (còn %s), không được phép xuất bán!",
                        lotLabel, productLabel(sku, row.getProductId()), warehouse.getName(),
                        row.getExpDate().format(DATE_VN), format(row.getQuantity()))
                : String.format("%s của sản phẩm %s sắp hết hạn vào ngày %s (còn %d ngày). Ưu tiên xuất trước (FEFO)!",
                        lotLabel, productLabel(sku, row.getProductId()), row.getExpDate().format(DATE_VN), daysLeft);
        return new StockAlertResponse(kind.name() + ":" + row.getId(), kind, severity, warehouse.getId(),
                warehouse.getCode(), warehouse.getName(), row.getProductId(), sku, name, row.getId(), row.getLotNo(),
                bin.getId(), bin.getCode(), nz(row.getQuantity()), row.getAvailableQuantity(), row.getExpDate(),
                daysLeft, null, null, null, null, message);
    }

    private StockAlertResponse deadStockAlert(WarehouseProductKey key, Position position, AlertProductInfo product,
                                              Instant lastMovement, long idleDays) {
        String sku = product != null ? product.sku() : null;
        String message = String.format("Mặt hàng %s tại %s không phát sinh nhập xuất trong %d ngày (còn %s).",
                productLabel(sku, key.productId()), position.warehouse.getName(), idleDays, format(position.onHand));
        return new StockAlertResponse(StockAlertKind.DEAD_STOCK.name() + ":" + key.warehouseId() + ":" + key.productId(),
                StockAlertKind.DEAD_STOCK, StockAlertSeverity.INFO, key.warehouseId(), position.warehouse.getCode(),
                position.warehouse.getName(), key.productId(), sku, product != null ? product.name() : null,
                null, null, null, null, position.onHand, position.available(), null, null, lastMovement, idleDays,
                null, null, message);
    }

    private StockAlertResponse lowStockAlert(WarehouseProductKey key, Position position, AlertProductInfo product,
                                             StockAlertSeverity severity) {
        String sku = productLabel(product.sku(), key.productId());
        String message = severity == StockAlertSeverity.CRITICAL
                ? String.format("Khẩn cấp: Mặt hàng %s tại %s đã chạm ngưỡng an toàn tối thiểu (Còn: %s, mức an toàn: %s)!",
                        sku, position.warehouse.getName(), format(position.usable), format(product.safetyStock()))
                : String.format("Cảnh báo: Mặt hàng %s tại %s sắp chạm ngưỡng tối thiểu (Còn: %s, điểm đặt hàng lại: %s). "
                                + "Đề xuất tạo đơn mua PO!",
                        sku, position.warehouse.getName(), format(position.usable), format(product.reorderPoint()));
        return new StockAlertResponse(StockAlertKind.LOW_STOCK.name() + ":" + key.warehouseId() + ":" + key.productId(),
                StockAlertKind.LOW_STOCK, severity, key.warehouseId(), position.warehouse.getCode(),
                position.warehouse.getName(), key.productId(), product.sku(), product.name(), null, null, null, null,
                position.usable, position.available(), null, null, null, null, nz(product.safetyStock()),
                nz(product.reorderPoint()), message);
    }

    static int kindRank(StockAlertKind kind) {
        return switch (kind) {
            case EXPIRED -> 0;
            case LOW_STOCK -> 1;
            case NEAR_EXPIRY -> 2;
            case DEAD_STOCK -> 3;
        };
    }

    private static String productLabel(String sku, Long productId) {
        return sku != null && !sku.isBlank() ? sku : "#" + productId;
    }

    static String format(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Tồn gộp của một mặt hàng trong một kho. */
    private static final class Position {
        private final Warehouse warehouse;
        /** Tổng tồn thực tế mọi dòng. */
        private BigDecimal onHand = BigDecimal.ZERO;
        /** Tồn dùng được: bỏ dòng bị khóa và dòng đã hết hạn. */
        private BigDecimal usable = BigDecimal.ZERO;
        /** Phần đã giữ chỗ trên các dòng dùng được. */
        private BigDecimal usableReserved = BigDecimal.ZERO;

        private Position(Warehouse warehouse) {
            this.warehouse = warehouse;
        }

        private void add(StockItem row, LocalDate today) {
            BigDecimal quantity = nz(row.getQuantity());
            onHand = onHand.add(quantity);
            if (!row.isLocked() && !row.isExpired(today)) {
                usable = usable.add(quantity);
                usableReserved = usableReserved.add(nz(row.getReservedQuantity()));
            }
        }

        private BigDecimal available() {
            return usable.subtract(usableReserved).max(BigDecimal.ZERO);
        }
    }
}
