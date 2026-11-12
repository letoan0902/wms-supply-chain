package vn.wms.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/**
 * Các dòng người dùng đã sửa và chọn để nhập ({@code POST /api/ai/extractions/{id}/import}).
 *
 * @param rows từ 1 đến 200 dòng, theo thứ tự muốn nhập
 */
public record ImportRequest(
        @NotEmpty(message = "Chưa chọn dòng nào để nhập")
        @Size(max = 200, message = "Mỗi lần nhập tối đa 200 dòng")
        List<@Valid @NotNull(message = "Dòng không được rỗng") Row> rows) {

    /**
     * @param row    số thứ tự dòng trong kết quả trích xuất (chỉ để giao diện đối chiếu, không gửi đi)
     * @param values giá trị theo tên trường như trong kết quả trích xuất; trường lạ bị bỏ qua
     */
    public record Row(
            Integer row,
            @NotNull(message = "Thiếu values của dòng")
            Map<String, Object> values) {
    }
}
