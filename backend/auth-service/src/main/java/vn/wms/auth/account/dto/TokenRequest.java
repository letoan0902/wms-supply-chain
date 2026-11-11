package vn.wms.auth.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body của {@code POST /api/auth/companies/activate}: mã lấy từ tham số {@code token} của liên kết. */
public record TokenRequest(
        @NotBlank(message = "Mã kích hoạt không được để trống")
        @Size(max = 128, message = "Mã kích hoạt không hợp lệ")
        String token
) {
    @Override
    public String toString() {
        return "TokenRequest[token=***]";
    }
}
