package vn.wms.inventory.dto.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Bảng giao dịch kho gần nhất trên dashboard (WMS-16.4), lấy từ sổ kho {@code stock_movements}: mỗi dòng là một chứng
 * từ (phiếu nhập, đơn bán đã xuất, phiếu điều chuyển, phiếu điều chỉnh kiểm kê) tại một kho, gom mọi dòng sổ của
 * chứng từ đó trong khoảng thời gian.
 *
 * @param dateRange    khoảng thời gian đã chuẩn hóa
 * @param fromTime     mốc bắt đầu (UTC, đầu ngày theo giờ Việt Nam), {@code null} với ALL_TIME
 * @param limit        số dòng tối đa đã yêu cầu
 * @param rows         các chứng từ, mới nhất trước
 * @param warehouseId  kho đã yêu cầu
 * @param warehouseIds các kho thực sự được tổng hợp, {@code null} là mọi kho của doanh nghiệp
 * @param generatedAt  thời điểm lập
 */
public record RecentTransactionsResponse(
        String dateRange,
        Instant fromTime,
        int limit,
        List<Row> rows,
        Long warehouseId,
        List<Long> warehouseIds,
        Instant generatedAt
) {

    /**
     * @param documentType             loại chứng từ của sổ kho: {@code GOODS_RECEIPT}, {@code SALES_ORDER},
     *                                 {@code STOCK_TRANSFER}, {@code STOCKTAKE}, {@code OTHER}
     * @param documentId               ID chứng từ (phiếu nhập, đơn bán, phiếu điều chuyển, phiếu điều chỉnh)
     * @param documentNumber           số chứng từ
     * @param movementType             loại biến động ({@code RECEIPT}, {@code SALES_DISPATCH}, {@code TRANSFER_OUT},
     *                                 {@code TRANSFER_IN}, {@code ADJUSTMENT_IN}, {@code ADJUSTMENT_OUT}, {@code ISSUE_OTHER})
     * @param movementTypeLabel        tên tiếng Việt của loại biến động
     * @param direction                {@code IN} (cộng tồn) hoặc {@code OUT} (trừ tồn)
     * @param warehouseId              kho phát sinh
     * @param warehouseCode            mã kho, {@code null} nếu kho đã bị xóa
     * @param warehouseName            tên kho, {@code null} nếu kho đã bị xóa
     * @param counterpartWarehouseId   kho bên kia của phiếu điều chuyển
     * @param counterpartWarehouseName tên kho bên kia
     * @param quantity                 tổng số lượng nhập (với {@code IN}) hoặc xuất (với {@code OUT}) của chứng từ tại kho
     * @param productCount             số mặt hàng
     * @param lineCount                số dòng sổ kho được gom
     * @param occurredAt               thời điểm phát sinh gần nhất của chứng từ (UTC)
     * @param performedBy              người thực hiện (tên đăng nhập)
     * @param stocktakeId              ID phiên kiểm kê của phiếu điều chỉnh, để mở màn hình kiểm kê; {@code null} với loại khác
     */
    public record Row(
            String documentType,
            Long documentId,
            String documentNumber,
            String movementType,
            String movementTypeLabel,
            String direction,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            Long counterpartWarehouseId,
            String counterpartWarehouseName,
            BigDecimal quantity,
            long productCount,
            long lineCount,
            Instant occurredAt,
            String performedBy,
            Long stocktakeId
    ) {}
}
