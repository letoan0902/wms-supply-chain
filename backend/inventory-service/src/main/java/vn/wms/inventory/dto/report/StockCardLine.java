package vn.wms.inventory.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Một dòng thẻ kho (WMS-17.1): một biến động của sản phẩm tại kho, kèm số dư lũy kế ngay sau biến động.
 * Là dòng dữ liệu phẳng để màn hình và bộ xuất Excel dùng chung.
 */
@Schema(description = "Dòng thẻ kho")
public record StockCardLine(
        @Schema(description = "ID dòng sổ kho", example = "5012")
        Long movementId,
        @Schema(description = "Thời điểm phát sinh (UTC)", example = "2026-11-05T03:15:00Z")
        Instant occurredAt,
        @Schema(description = "Loại biến động: OPENING_BALANCE, RECEIPT, TRANSFER_IN, ADJUSTMENT_IN, SALES_DISPATCH, "
                + "TRANSFER_OUT, ADJUSTMENT_OUT, ISSUE_OTHER", example = "RECEIPT")
        String movementType,
        @Schema(description = "Tên loại biến động bằng tiếng Việt", example = "Nhập kho theo phiếu nhập")
        String movementTypeLabel,
        @Schema(description = "Loại chứng từ: GOODS_RECEIPT, SALES_ORDER, STOCK_TRANSFER, OPENING_BALANCE, OTHER",
                example = "GOODS_RECEIPT")
        String documentType,
        @Schema(description = "ID chứng từ (ID phiếu nhập, ID đơn bán, ID phiếu điều chuyển)", example = "88")
        Long documentId,
        @Schema(description = "Số chứng từ", example = "GRN-20261105-0001")
        String documentNumber,
        @Schema(description = "Loại chứng từ tham chiếu (PURCHASE_ORDER với dòng nhập kho)", example = "PURCHASE_ORDER")
        String referenceType,
        @Schema(description = "ID chứng từ tham chiếu", example = "40")
        Long referenceId,
        @Schema(description = "Số chứng từ tham chiếu", example = "PO-20261101-0003")
        String referenceNumber,
        @Schema(description = "ID nhà cung cấp của phiếu nhập (tham chiếu order-service)", example = "7")
        Long partnerId,
        @Schema(description = "Kho phía bên kia của phiếu điều chuyển", example = "2")
        Long counterpartWarehouseId,
        @Schema(description = "Số lô", example = "LOT-2026-11-A")
        String lotNo,
        @Schema(description = "Hạn sử dụng của lô", example = "2027-05-01")
        LocalDate expDate,
        @Schema(description = "ID vị trí ô/kệ", example = "15")
        Long binLocationId,
        @Schema(description = "Mã vị trí ô/kệ tại thời điểm phát sinh", example = "WH01-ZA-R01-B01")
        String binLocationCode,
        @Schema(description = "Số lượng nhập", example = "50.000")
        BigDecimal quantityIn,
        @Schema(description = "Số lượng xuất", example = "0.000")
        BigDecimal quantityOut,
        @Schema(description = "Tồn lũy kế sau dòng này", example = "150.000")
        BigDecimal balanceAfter,
        @Schema(description = "Người thực hiện", example = "thukho01")
        String performedBy,
        @Schema(description = "Diễn giải / lý do", example = "Xuất kho giao hàng")
        String reason
) {
}
