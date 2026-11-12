package vn.wms.inventory.dto;

import vn.wms.inventory.entity.StocktakeLine;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Dòng kiểm kê cho màn hình đếm mù (BR-12): KHÔNG có tồn hệ thống, giữ chỗ hay chênh lệch.
 *
 * @param lineId           ID dòng kiểm kê
 * @param binLocationId    ID vị trí
 * @param binLocationCode  mã vị trí
 * @param zone             khu
 * @param productId        ID sản phẩm (tên, SKU lấy từ product-service)
 * @param lotNo            số lô (null nếu hàng không theo lô)
 * @param counted          đã có số đếm hay chưa
 * @param countedQuantity  số đã đếm (null nếu chưa đếm)
 * @param countTimes       số lần ghi số đếm
 * @param lastCountedBy    người ghi lần cuối
 * @param lastCountedAt    thời điểm ghi lần cuối
 * @param unexpected       hàng phát sinh (không có trong ảnh chụp tồn)
 * @param recountRequested dòng được yêu cầu đếm lại
 */
public record StocktakeCountLineResponse(
        Long lineId,
        Long binLocationId,
        String binLocationCode,
        String zone,
        Long productId,
        String lotNo,
        boolean counted,
        BigDecimal countedQuantity,
        int countTimes,
        String lastCountedBy,
        Instant lastCountedAt,
        boolean unexpected,
        boolean recountRequested
) {
    public static StocktakeCountLineResponse from(StocktakeLine line) {
        return new StocktakeCountLineResponse(
                line.getId(),
                line.getBinLocation().getId(),
                line.getBinLocation().getCode(),
                line.getBinLocation().getZone(),
                line.getProductId(),
                line.getLotNo(),
                line.isCounted(),
                line.getCountedQuantity(),
                line.getCountTimes(),
                line.getLastCountedBy(),
                line.getLastCountedAt(),
                line.isUnexpected(),
                line.isRecountRequested()
        );
    }
}
