package vn.wms.auth.dto;

/**
 * Phản hồi sau khi làm mới Access Token thành công.
 *
 * @param accessToken  chuỗi Access Token JWT mới
 * @param tokenType    luôn là Bearer
 * @param expiresIn    thời gian sống của access token (giây)
 * @param refreshToken Refresh Token (mới được xoay vòng hoặc hiện tại)
 */
public record RefreshTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken
) {
}
