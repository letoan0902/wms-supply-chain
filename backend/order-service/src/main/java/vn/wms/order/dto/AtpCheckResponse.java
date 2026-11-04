package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * Kết quả kiểm tra tồn khả dụng ATP trước khi tạo đơn (FR15 - WMS-11.1).
 */
public record AtpCheckResponse(
        @Schema(description = "ID kho hàng đã kiểm tra")
        Long warehouseId,

        @Schema(description = "Có đủ tồn khả dụng cho toàn bộ các dòng hàng hay không")
        boolean canFulfillAll,

        @Schema(description = "Chi tiết tình trạng tồn từng dòng mặt hàng")
        List<ItemResult> items
) {
    public record ItemResult(
            @Schema(description = "ID sản phẩm")
            Long productId,

            @Schema(description = "Mã SKU")
            String skuCode,

            @Schema(description = "Tên sản phẩm")
            String productName,

            @Schema(description = "Số lượng đặt bán")
            BigDecimal requestedQuantity,

            @Schema(description = "Tồn thực tế trong kho (OnHand)")
            BigDecimal onHandQuantity,

            @Schema(description = "Số lượng đã giữ chỗ cho đơn khác (Reserved)")
            BigDecimal reservedQuantity,

            @Schema(description = "Tồn khả dụng (ATP = OnHand - Reserved)")
            BigDecimal atpQuantity,

            @Schema(description = "Đủ tồn khả dụng cho dòng này hay không (requestedQuantity <= atpQuantity)")
            boolean isSufficient,

            @Schema(description = "Số lượng thiếu hụt nếu không đủ tồn")
            BigDecimal shortageQuantity
    ) {
    }
}
