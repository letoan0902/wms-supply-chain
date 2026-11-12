package vn.wms.inventory.dto.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Tỷ trọng tồn kho theo nhóm hàng (danh mục của product-service) cho biểu đồ donut trên dashboard (WMS-16.4).
 *
 * <p>Tồn kho chưa có giá vốn (giống thẻ "Giá trị tồn kho"), nên tỷ trọng tính theo số lượng tồn thực tế
 * ({@code basis = QUANTITY}) và mọi trường giá trị là {@code null}. Khi sau này có giá vốn, chỉ vai trò
 * {@code valueVisible = true} (ADMIN, ACCOUNTANT) mới nhận giá trị.
 *
 * @param dateRange       khoảng thời gian dashboard đã chuẩn hóa; tỷ trọng luôn là tồn tại thời điểm xem vì mọi khoảng
 *                        đều kết thúc hôm nay
 * @param asOf            thời điểm chụp tồn
 * @param basis           cơ sở tính tỷ trọng: {@code QUANTITY} (số lượng) hoặc {@code VALUE} (giá trị, chưa dùng)
 * @param valuationMethod {@code NOT_AVAILABLE}: chưa có phương pháp tính giá
 * @param valueVisible    vai trò hiện tại có được xem giá trị tồn không
 * @param basisNote       giải thích bằng lời vì sao tính theo số lượng và số lượng gồm những gì
 * @param unit            đơn vị của số lượng
 * @param totalQuantity   tổng tồn thực tế trong phạm vi
 * @param totalValue      tổng giá trị, luôn {@code null} khi chưa có giá vốn
 * @param productCount    số sản phẩm còn tồn
 * @param categoryCount   số nhóm hàng (kể cả nhóm "Chưa phân nhóm hàng") trước khi gom phần nhỏ vào "Các nhóm khác"
 * @param slices          các lát cắt, lớn trước; tối đa 8 lát cắt, phần còn lại gom vào "Các nhóm khác"
 * @param warehouseId     kho đã yêu cầu
 * @param warehouseIds    các kho thực sự được tổng hợp, {@code null} là mọi kho của doanh nghiệp
 * @param dataNote        lưu ý khi số liệu chưa đầy đủ (ví dụ danh mục sản phẩm quá lớn), {@code null} khi đầy đủ
 */
public record StockByCategoryResponse(
        String dateRange,
        Instant asOf,
        String basis,
        String valuationMethod,
        boolean valueVisible,
        String basisNote,
        String unit,
        BigDecimal totalQuantity,
        BigDecimal totalValue,
        int productCount,
        int categoryCount,
        List<Slice> slices,
        Long warehouseId,
        List<Long> warehouseIds,
        String dataNote
) {

    /** Lát cắt là một nhóm hàng. */
    public static final String KIND_CATEGORY = "CATEGORY";
    /** Lát cắt gom sản phẩm chưa gán nhóm hàng hoặc không còn trong danh mục. */
    public static final String KIND_UNCATEGORIZED = "UNCATEGORIZED";
    /** Lát cắt gom các nhóm nhỏ ngoài số lát cắt tối đa. */
    public static final String KIND_OTHERS = "OTHERS";

    /**
     * @param key          khóa ổn định cho giao diện: {@code category-{id}}, {@code uncategorized}, {@code others}
     * @param kind         {@link #KIND_CATEGORY}, {@link #KIND_UNCATEGORIZED} hoặc {@link #KIND_OTHERS}
     * @param categoryId   ID nhóm hàng, {@code null} với hai loại gom
     * @param name         tên hiển thị
     * @param quantity     tổng tồn thực tế của nhóm
     * @param value        giá trị tồn, {@code null} khi chưa có giá vốn
     * @param percentage   tỷ trọng theo {@code basis}, một chữ số thập phân
     * @param productCount số sản phẩm còn tồn trong nhóm
     */
    public record Slice(
            String key,
            String kind,
            Long categoryId,
            String name,
            BigDecimal quantity,
            BigDecimal value,
            double percentage,
            int productCount
    ) {}
}
