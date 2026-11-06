package vn.wms.auth.dto.auth;

import java.util.List;

/**
 * Kết quả đăng nhập.
 *
 * @param accessToken  JWT, gửi kèm các request sau trong header Authorization dạng Bearer
 * @param refreshToken Refresh Token dùng để làm mới Access Token
 * @param tokenType    luôn là Bearer
 * @param expiresIn    thời gian sống của access token (giây)
 * @param username     tên đăng nhập
 * @param roles        danh sách vai trò của người dùng
 * @param companyId    ID doanh nghiệp trực thuộc (null nếu là System Admin)
 * @param companyCode  Mã doanh nghiệp (null nếu là System Admin)
 * @param companyName  Tên doanh nghiệp (null nếu là System Admin)
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String username,
        List<String> roles,
        Long companyId,
        String companyCode,
        String companyName) {

    public LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, String username, List<String> roles) {
        this(accessToken, refreshToken, tokenType, expiresIn, username, roles, null, null, null);
    }

    public LoginResponse(String accessToken, String tokenType, long expiresIn, String username, List<String> roles) {
        this(accessToken, null, tokenType, expiresIn, username, roles, null, null, null);
    }
}
