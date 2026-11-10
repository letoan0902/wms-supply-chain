package vn.wms.auth.dto.auth;

import vn.wms.auth.company.dto.CompanySummary;
import vn.wms.auth.subscription.dto.SubscriptionSummary;

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
 * @param company      doanh nghiệp của người dùng: id, code, name
 * @param subscription thông tin gói dịch vụ hiện tại: plan, status, expiresAt
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        String username,
        List<String> roles,
        CompanySummary company,
        SubscriptionSummary subscription) {

    public LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, String username, List<String> roles, CompanySummary company) {
        this(accessToken, refreshToken, tokenType, expiresIn, username, roles, company, null);
    }

    public LoginResponse(String accessToken, String refreshToken, String tokenType, long expiresIn, String username, List<String> roles) {
        this(accessToken, refreshToken, tokenType, expiresIn, username, roles, null, null);
    }

    public LoginResponse(String accessToken, String tokenType, long expiresIn, String username, List<String> roles) {
        this(accessToken, null, tokenType, expiresIn, username, roles, null, null);
    }
}
