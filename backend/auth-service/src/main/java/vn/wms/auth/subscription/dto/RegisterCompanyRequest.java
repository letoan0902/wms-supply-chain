package vn.wms.auth.subscription.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterCompanyRequest {

    @NotBlank(message = "Tên doanh nghiệp không được để trống")
    @Size(min = 2, max = 150, message = "Tên doanh nghiệp phải từ 2 đến 150 ký tự")
    private String companyName;

    @Size(max = 30, message = "Mã số thuế tối đa 30 ký tự")
    private String taxCode;

    @Size(max = 30, message = "Số điện thoại tối đa 30 ký tự")
    private String phone;

    // Các quy tắc dưới đây giống RegisterRequest (tạo tài khoản) và độ dài cột của bảng users
    @NotBlank(message = "Họ tên người đại diện không được để trống")
    @Size(max = 100, message = "Họ và tên tối đa 100 ký tự")
    private String fullName;

    @NotBlank(message = "Tên đăng nhập không được để trống")
    @Size(min = 3, max = 50, message = "Tên đăng nhập từ 3 đến 50 ký tự")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Tên đăng nhập chỉ chứa chữ cái, chữ số, dấu chấm, gạch dưới hoặc gạch ngang")
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, max = 72, message = "Mật khẩu từ 6 đến 72 ký tự")
    private String password;
}
