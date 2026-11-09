package vn.wms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Payload lập mới Phiếu nhập kho (Goods Receipt Note - GRN) từ PO đã duyệt (FR11 - WMS-10.1).
 */
public record CreateGrnRequest(
        @NotNull(message = "ID đơn mua hàng (PO) không được để trống")
        @Schema(description = "ID đơn mua hàng PO được duyệt", example = "1")
        Long poId,

        @Schema(description = "Ngày nhập kho thực tế (mặc định hôm nay nếu để trống)", example = "2026-11-06")
        LocalDate receiptDate,

        @Size(max = 500, message = "Ghi chú phiếu nhập tối đa 500 ký tự")
        @Schema(description = "Ghi chú chung về lần nhập kho", example = "Nhập đợt 1 xe hàng từ NCC Hải Hà")
        String note,

        @NotEmpty(message = "Phiếu nhập kho phải có ít nhất một dòng mặt hàng")
        @Valid
        @Schema(description = "Danh sách chi tiết dòng hàng thực nhận")
        List<CreateGrnItemRequest> items
) {
}
