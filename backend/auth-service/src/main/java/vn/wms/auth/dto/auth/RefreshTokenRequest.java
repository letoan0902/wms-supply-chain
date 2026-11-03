package vn.wms.auth.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Yêu cầu làm mới Access Token.
 *
 * @param refreshToken chuỗi Refresh Token hợp lệ
 */
public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token không được để trống")
        String refreshToken
) {
}
