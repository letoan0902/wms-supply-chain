package vn.wms.common.bulk;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.util.List;

/**
 * Body của API nhập hàng loạt: {@code { "items": [ ... ] }}, từ 1 đến {@value #MAX_ITEMS} dòng.
 *
 * <p>Ràng buộc chỉ đặt trên kích thước danh sách, không đặt {@code @Valid} lên từng phần tử: dòng sai dữ liệu
 * không làm hỏng cả request mà nhận kết quả {@link BulkRowStatus#FAILED} riêng. Controller nhận body với
 * {@code @RequestBody(required = false)} rồi gọi {@link #requireItems(BulkRequest)} để body trống cũng trả
 * {@code 400 VALIDATION_ERROR} như danh sách rỗng.
 *
 * @param <T> kiểu của một dòng
 */
public record BulkRequest<T>(
        @NotNull(message = "Danh sách items không được để trống")
        @Size(min = 1, max = BulkRequest.MAX_ITEMS, message = "Danh sách items phải có từ 1 đến 500 dòng")
        List<T> items
) {

    /** Số dòng tối đa của một lần nhập. */
    public static final int MAX_ITEMS = 500;

    /**
     * Kiểm tra body có từ 1 đến {@value #MAX_ITEMS} dòng và trả danh sách dòng.
     *
     * @throws ApiException {@link ErrorCode#VALIDATION_ERROR} khi body trống, thiếu {@code items}, danh sách rỗng
     *                      hoặc quá {@value #MAX_ITEMS} dòng
     */
    public static <T> List<T> requireItems(BulkRequest<T> request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Danh sách items không được để trống");
        }
        if (request.items().size() > MAX_ITEMS) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    "Mỗi lần nhập tối đa " + MAX_ITEMS + " dòng, yêu cầu có " + request.items().size() + " dòng");
        }
        return request.items();
    }
}
