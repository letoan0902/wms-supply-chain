package vn.wms.inventory.dto;

import vn.wms.common.api.PageResponse;

import java.math.BigDecimal;

/**
 * Báo cáo chênh lệch kiểm kê: tổng hợp toàn đợt và trang dòng chênh lệch theo bộ lọc.
 * Giá trị tiền của chênh lệch không tính ở đây vì inventory-service chưa lưu giá vốn sản phẩm.
 */
public record StocktakeVarianceReportResponse(Summary summary, PageResponse<StocktakeVarianceLineResponse> lines) {

    /**
     * @param totalLines            tổng số dòng của đợt
     * @param countedLines          số dòng đã có số đếm
     * @param linesWithVariance     số dòng có chênh lệch khác 0
     * @param increaseLines         số dòng thừa (chênh lệch dương)
     * @param decreaseLines         số dòng thiếu (chênh lệch âm)
     * @param totalIncreaseQuantity tổng số lượng thừa
     * @param totalDecreaseQuantity tổng số lượng thiếu (số dương)
     * @param netVarianceQuantity   chênh lệch ròng = thừa - thiếu
     * @param reservedConflictLines số dòng có số đếm nhỏ hơn giữ chỗ
     */
    public record Summary(long totalLines, long countedLines, long linesWithVariance, long increaseLines,
                          long decreaseLines, BigDecimal totalIncreaseQuantity, BigDecimal totalDecreaseQuantity,
                          BigDecimal netVarianceQuantity, long reservedConflictLines) {
    }
}
