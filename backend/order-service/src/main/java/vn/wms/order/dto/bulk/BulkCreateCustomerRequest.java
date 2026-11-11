package vn.wms.order.dto.bulk;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import vn.wms.order.dto.CreateCustomerRequest;

import java.util.List;

/**
 * Body của {@code POST /api/orders/customers/bulk} (WMS-22.4b). Mỗi dòng có đúng các trường của
 * {@link CreateCustomerRequest} và được kiểm tra riêng như khi tạo từng khách hàng.
 */
public record BulkCreateCustomerRequest(
        @NotEmpty(message = "Danh sách khách hàng cần nhập không được để trống")
        @Size(max = BulkLimits.MAX_ROWS, message = "Mỗi lần nhập tối đa " + BulkLimits.MAX_ROWS + " dòng")
        @Schema(description = "Các dòng cần nhập, tối đa 500 dòng")
        List<CreateCustomerRequest> items
) {
}
