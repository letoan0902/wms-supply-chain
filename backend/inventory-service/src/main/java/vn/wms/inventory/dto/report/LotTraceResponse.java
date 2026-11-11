package vn.wms.inventory.dto.report;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Kết quả truy vết một số lô của một sản phẩm (WMS-17.2): lô từ đâu tới (phiếu nhập, đơn mua, nhà cung cấp), đang ở
 * đâu (vị trí, số lượng, khóa, hạn dùng), đã đi đâu (điều chuyển, xuất bán theo đơn bán), kèm chuỗi sự kiện theo thời
 * gian và cây để giao diện vẽ.
 */
@Schema(description = "Truy vết nguồn gốc theo số lô")
public record LotTraceResponse(
        String lotNo,
        Long productId,
        String skuCode,
        String productName,
        String uom,
        @Schema(description = "Ngày sản xuất theo dòng tồn hoặc phiếu nhập, null nếu không có")
        LocalDate mfgDate,
        @Schema(description = "Hạn sử dụng theo dòng tồn hoặc phiếu nhập, null nếu không có")
        LocalDate expDate,
        @Schema(description = "Các kho được xem; null là mọi kho của doanh nghiệp")
        List<Long> warehouseIds,
        Summary summary,
        @Schema(description = "Lô từ đâu tới: phiếu nhập GRN (kèm đơn mua, nhà cung cấp) hoặc số dư mở sổ kho")
        List<Flow> origins,
        @Schema(description = "Lô đang ở đâu: các dòng tồn còn số lượng")
        List<Location> currentLocations,
        @Schema(description = "Lô đã đi đâu: điều chuyển, xuất bán, xuất khác, điều chỉnh giảm")
        List<Flow> outflows,
        @Schema(description = "Mọi biến động của lô theo thứ tự thời gian, có tồn lũy kế của lô")
        List<TimelineEvent> timeline,
        @Schema(description = "Cây truy vết: gốc là lô, con là nguồn gốc, vị trí hiện tại và nơi đã đi")
        TraceNode tree,
        @Schema(description = "Các giới hạn của dữ liệu cần hiển thị cho người xem")
        List<String> notes,
        Instant ledgerStartedAt,
        Instant generatedAt
) {

    /** Tổng hợp số lượng của lô trong phạm vi kho của người xem. */
    public record Summary(
            @Schema(description = "Tổng nhập theo phiếu nhập GRN") BigDecimal totalReceived,
            @Schema(description = "Số dư mở sổ kho (tồn có sẵn trước khi có sổ kho, không rõ chứng từ gốc)") BigDecimal openingBalance,
            @Schema(description = "Tổng điều chỉnh tăng") BigDecimal totalAdjustedIn,
            @Schema(description = "Tổng xuất bán") BigDecimal totalSalesDispatched,
            @Schema(description = "Tổng xuất điều chuyển") BigDecimal totalTransferredOut,
            @Schema(description = "Tổng xuất khác và điều chỉnh giảm") BigDecimal totalOtherOut,
            @Schema(description = "Tồn của lô theo sổ kho = tổng nhập - tổng xuất") BigDecimal ledgerBalance,
            @Schema(description = "Tồn thực tế hiện tại của lô (stock_items)") BigDecimal currentOnHand,
            @Schema(description = "Đang giữ chỗ cho đơn bán") BigDecimal currentReserved
    ) {
    }

    /** Một chứng từ nhập vào hoặc xuất ra của lô (gộp các dòng sổ cùng chứng từ, cùng kho). */
    public record Flow(
            @Schema(description = "RECEIPT, OPENING_BALANCE, ADJUSTMENT_IN, SALES_DISPATCH, TRANSFER_OUT, ISSUE_OTHER, ADJUSTMENT_OUT")
            String movementType,
            String movementTypeLabel,
            Instant occurredAt,
            String documentType,
            Long documentId,
            String documentNumber,
            @Schema(description = "PURCHASE_ORDER với phiếu nhập") String referenceType,
            Long referenceId,
            String referenceNumber,
            @Schema(description = "ID nhà cung cấp của phiếu nhập (tham chiếu order-service)") Long supplierId,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            @Schema(description = "Các vị trí liên quan") List<String> binLocationCodes,
            BigDecimal quantity,
            String performedBy,
            @Schema(description = "Chi tiết phiếu điều chuyển, chỉ có với TRANSFER_OUT") TransferInfo transfer
    ) {
    }

    /** Phiếu điều chuyển mà lô đã đi theo. */
    public record TransferInfo(
            Long transferId,
            String transferNumber,
            String status,
            Long destinationWarehouseId,
            String destinationWarehouseCode,
            String destinationWarehouseName,
            @Schema(description = "Số lượng của lô này đã xuất theo phiếu") BigDecimal lotQuantityShipped,
            @Schema(description = "Số lượng thực nhận của cả dòng sản phẩm trên phiếu (có thể gồm nhiều lô)") BigDecimal itemReceivedQuantity,
            @Schema(description = "Số lượng thiếu, hỏng của cả dòng sản phẩm trên phiếu") BigDecimal itemDamagedQuantity,
            String destinationBinLocationCode,
            Instant receivedAt,
            String receivedBy,
            @Schema(description = "false: kho đích không giữ số lô nên không truy tiếp được tại kho đích") boolean lotTrackedAtDestination
    ) {
    }

    /** Một dòng tồn hiện tại của lô. */
    public record Location(
            Long stockItemId,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            Long binLocationId,
            String binLocationCode,
            BigDecimal quantity,
            BigDecimal reservedQuantity,
            BigDecimal availableQuantity,
            boolean locked,
            String lockedReason,
            LocalDate expDate,
            boolean expired,
            Long daysUntilExpiry
    ) {
    }

    /** Một biến động của lô trong chuỗi thời gian. */
    public record TimelineEvent(
            Long movementId,
            Instant occurredAt,
            String movementType,
            String movementTypeLabel,
            String documentType,
            Long documentId,
            String documentNumber,
            String referenceType,
            String referenceNumber,
            Long supplierId,
            Long warehouseId,
            String warehouseCode,
            String warehouseName,
            String binLocationCode,
            Long counterpartWarehouseId,
            BigDecimal quantityIn,
            BigDecimal quantityOut,
            @Schema(description = "Tồn lũy kế của lô (trong phạm vi kho) sau sự kiện") BigDecimal lotBalanceAfter,
            String performedBy
    ) {
    }

    /**
     * Nút của cây truy vết. {@code direction}: ROOT (lô), UPSTREAM (nguồn gốc), CURRENT (đang ở), DOWNSTREAM (đã đi).
     * {@code nodeType}: LOT, GOODS_RECEIPT, PURCHASE_ORDER, SUPPLIER, OPENING_BALANCE, ADJUSTMENT_IN, LOCATION,
     * TRANSFER, TRANSFER_DESTINATION, SALES_ORDER, OTHER_ISSUE, ADJUSTMENT_OUT.
     */
    public record TraceNode(
            String key,
            String nodeType,
            String direction,
            String title,
            BigDecimal quantity,
            Instant occurredAt,
            String documentType,
            Long documentId,
            String documentNumber,
            Long warehouseId,
            String warehouseName,
            String binLocationCode,
            List<TraceNode> children
    ) {
    }
}
