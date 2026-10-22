package vn.wms.auth.dto;

import java.util.List;

/**
 * Kết quả đăng nhập.
 *
 * @param accessToken JWT, gửi kèm các request sau trong header Authorization dạng Bearer
 * @param tokenType   luôn là Bearer
 * @param expiresIn   thời gian sống của token (giây)
 * @param username    tên đăng nhập
 * @param roles       danh sách vai trò của người dùng
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, String username, List<String> roles) {
}
