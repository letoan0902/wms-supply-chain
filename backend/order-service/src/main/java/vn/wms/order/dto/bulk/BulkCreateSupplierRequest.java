package vn.wms.order.dto.bulk;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import vn.wms.order.dto.CreateSupplierRequest;

import java.util.List;

/**
 * Body của {@code POST /api/orders/suppliers/bulk} (WMS-22.4b). Mỗi dòng có đúng các trường của
 * {@link CreateSupplierRequest} và được kiểm tra riêng như khi tạo từng nhà cung cấp, nên danh sách không gắn
 * {@code @Valid} cho phần tử: dòng sai chỉ làm dòng đó FAILED chứ không làm cả yêu cầu bị từ chối.
 */
public record BulkCreateSupplierRequest(
        @NotEmpty(message = "Danh sách nhà cung cấp cần nhập không được để trống")
        @Size(max = BulkLimits.MAX_ROWS, message = "Mỗi lần nhập tối đa " + BulkLimits.MAX_ROWS + " dòng")
        @Schema(description = "Các dòng cần nhập, tối đa 500 dòng")
        List<CreateSupplierRequest> items
) {
}
