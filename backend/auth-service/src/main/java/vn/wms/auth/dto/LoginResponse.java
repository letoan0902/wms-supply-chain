package vn.wms.auth.dto;

import java.util.List;

/**
 * Kết quả đăng nhập.
 *
 * @param accessToken  JWT, gửi kèm các request sau trong header Authorization dạng Bearer
 * @param tokenType    luôn là Bearer
 * @param expiresIn    thời gian sống của access token (giây)
 * @param username     tên đăng nhập
 * @param roles        danh sách vai trò của người dùng
 * @param refreshToken Refresh Token dùng để làm mới Access Token
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String username,
        List<String> roles) {
    // Overload cho code cũ nếu chỉ truyền 5 tham số không có refreshToken
    public LoginResponse(String accessToken, String tokenType, long expiresIn, String username, List<String> roles) {
        this(accessToken, null, tokenType, expiresIn, username, roles);
    }
}
