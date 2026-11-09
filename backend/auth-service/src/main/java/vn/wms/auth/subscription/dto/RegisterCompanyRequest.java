package vn.wms.auth.subscription.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

    private String taxCode;

    private String phone;

    @NotBlank(message = "Họ tên người đại diện không được để trống")
    private String fullName;

    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 6, message = "Mật khẩu phải từ 6 ký tự trở lên")
    private String password;
}
