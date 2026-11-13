package vn.wms.inventory.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Yêu cầu đếm lại một số dòng trước khi duyệt (WMS-14.2): số đếm của các dòng bị xóa, đợt quay lại COUNTING.
 */
public record StocktakeRecountRequest(
        @NotEmpty(message = "Vui lòng chọn ít nhất một dòng cần đếm lại")
        @Size(max = 1000, message = "Tối đa 1000 dòng mỗi lần yêu cầu đếm lại")
        List<Long> lineIds
) {
}
