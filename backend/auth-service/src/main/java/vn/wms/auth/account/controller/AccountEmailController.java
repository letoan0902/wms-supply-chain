package vn.wms.auth.account.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.wms.auth.account.dto.ActivationResponse;
import vn.wms.auth.account.dto.EmailRequest;
import vn.wms.auth.account.dto.ResetPasswordRequest;
import vn.wms.auth.account.dto.TokenRequest;
import vn.wms.auth.account.service.AccountMailRequestService;
import vn.wms.auth.account.service.CompanyActivationService;
import vn.wms.auth.account.service.PasswordResetService;
import vn.wms.common.api.ApiResponse;

/**
 * API công khai của WMS-23.6: kích hoạt doanh nghiệp qua email và đặt lại mật khẩu bằng liên kết dùng một lần.
 * Hai API nhận email luôn trả {@code 200} với cùng nội dung, không cho biết email có tồn tại hay không.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Account Email", description = "Kích hoạt doanh nghiệp qua email, quên và đặt lại mật khẩu")
public class AccountEmailController {

    static final String RESEND_MESSAGE = "Nếu email thuộc một doanh nghiệp đang chờ kích hoạt, hệ thống sẽ gửi lại "
            + "liên kết kích hoạt trong ít phút. Vui lòng kiểm tra hộp thư, kể cả mục thư rác.";
    static final String FORGOT_MESSAGE = "Nếu email thuộc một tài khoản đang hoạt động, hệ thống sẽ gửi liên kết đặt "
            + "lại mật khẩu trong ít phút. Liên kết có hiệu lực 30 phút.";

    private final CompanyActivationService activationService;
    private final PasswordResetService passwordResetService;
    private final AccountMailRequestService mailRequestService;

    @PostMapping("/api/auth/companies/activate")
    @Operation(summary = "Kích hoạt doanh nghiệp bằng mã trong liên kết email (sai, đã dùng, quá 24 giờ: TOKEN_INVALID)")
    public ApiResponse<ActivationResponse> activate(@Valid @RequestBody TokenRequest request) {
        return ApiResponse.ok("Kích hoạt doanh nghiệp thành công. Bạn có thể đăng nhập ngay.",
                activationService.activate(request.token()));
    }

    @PostMapping("/api/auth/companies/resend-activation")
    @Operation(summary = "Gửi lại email kích hoạt; luôn trả 200, mỗi email tối đa một lần mỗi phút")
    public ApiResponse<Void> resendActivation(@Valid @RequestBody EmailRequest request) {
        mailRequestService.requestActivationResend(request.email());
        return ApiResponse.ok(RESEND_MESSAGE, null);
    }

    @PostMapping("/api/auth/password/forgot")
    @Operation(summary = "Quên mật khẩu: gửi liên kết đặt lại (hiệu lực 30 phút); luôn trả 200")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody EmailRequest request) {
        mailRequestService.requestPasswordReset(request.email());
        return ApiResponse.ok(FORGOT_MESSAGE, null);
    }

    @PostMapping("/api/auth/password/reset")
    @Operation(summary = "Đặt mật khẩu mới bằng mã trong liên kết; vô hiệu mọi refresh token của tài khoản")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.token(), request.newPassword());
        return ApiResponse.ok("Đặt lại mật khẩu thành công. Vui lòng đăng nhập bằng mật khẩu mới.", null);
    }
}
