package vn.wms.order.dto.report;

import java.math.BigDecimal;

/**
 * Lát cắt dữ liệu cho từng mặt hàng trên biểu đồ Donut (Recharts).
 *
 * @param productId   ID sản phẩm
 * @param sku         Mã SKU hàng hóa
 * @param name        Tên sản phẩm
 * @param uom         Đơn vị tính (Chai, Thùng, Túi...)
 * @param quantity    Tổng sản lượng xuất bán trong kỳ
 * @param percentage  Tỷ lệ phần trăm đóng góp trên tổng xuất (%)
 * @param color       Mã màu Hex gán riêng để vẽ biểu đồ Recharts
 */
public record TrendingItemSliceDto(
        Long productId,
        String sku,
        String name,
        String uom,
        BigDecimal quantity,
        double percentage,
        String color
) {}
