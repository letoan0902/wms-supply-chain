package vn.wms.inventory.alert;

import org.springframework.stereotype.Component;
import vn.wms.inventory.entity.StockItem;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Nguồn duy nhất của "lần biến động gần nhất" cho cảnh báo hàng tồn lâu (DEAD_STOCK).
 *
 * <p>Hiện chưa có sổ cái biến động kho, nên dùng dữ liệu tốt nhất đang có: thời điểm thay đổi gần nhất
 * ({@code updated_at}) của các dòng {@code stock_items} của mặt hàng trong kho. Cột này đổi khi nhập (phiếu nhập GRN,
 * điều chuyển đến), xuất (trừ tồn, điều chuyển đi) và cả khi giữ chỗ hay khóa lô, nên là xấp xỉ hơi "dễ dãi": hàng
 * vừa được giữ chỗ cho đơn bán cũng được coi là có biến động. Khi sổ cái biến động (task báo cáo) có mặt, chỉ cần đổi
 * phần thân của {@link #lastMovements(Collection)} sang đọc thời điểm nhập hoặc xuất cuối cùng theo (kho, sản phẩm).
 */
@Component
public class LastMovementReader {

    /**
     * @param stockRows các dòng tồn của phạm vi đang xét (đã nạp kho qua ô kệ)
     * @return thời điểm biến động gần nhất theo (kho, sản phẩm)
     */
    public Map<WarehouseProductKey, Instant> lastMovements(Collection<StockItem> stockRows) {
        Map<WarehouseProductKey, Instant> result = new HashMap<>();
        for (StockItem row : stockRows) {
            Instant changedAt = row.getUpdatedAt() != null ? row.getUpdatedAt() : row.getCreatedAt();
            if (changedAt == null || row.getBinLocation() == null || row.getBinLocation().getWarehouse() == null) {
                continue;
            }
            WarehouseProductKey key = new WarehouseProductKey(row.getBinLocation().getWarehouse().getId(),
                    row.getProductId());
            result.merge(key, changedAt, (a, b) -> a.isAfter(b) ? a : b);
        }
        return result;
    }
}
