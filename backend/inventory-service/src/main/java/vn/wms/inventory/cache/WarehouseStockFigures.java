package vn.wms.inventory.cache;

import java.math.BigDecimal;

/**
 * Các số tồn kho tổng hợp của một kho (hoặc mọi kho) dùng cho KPI: tồn khả dụng (tổng quantity - reserved của dòng
 * chưa khóa, chưa hết hạn), tổng tồn thực tế và số ô đang chứa hàng. Số ô của kho (mẫu số tỷ lệ lấp đầy) không nằm
 * ở đây vì nó đổi theo danh mục ô kệ chứ không theo tồn, nên luôn đọc thẳng cơ sở dữ liệu.
 */
public record WarehouseStockFigures(BigDecimal availableQuantity, BigDecimal totalQuantity, long occupiedLocations) {

    public WarehouseStockFigures {
        availableQuantity = availableQuantity != null ? availableQuantity : BigDecimal.ZERO;
        totalQuantity = totalQuantity != null ? totalQuantity : BigDecimal.ZERO;
    }
}
