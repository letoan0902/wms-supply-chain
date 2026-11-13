package vn.wms.auth.dto.users;

import vn.wms.auth.company.dto.CompanySummary;
import vn.wms.auth.subscription.dto.SubscriptionSummary;

import java.util.List;

/**
 * Thông tin người dùng hiện tại, lấy từ JWT trong request; company và subscription (plan, status, expiresAt)
 * đọc lại từ cơ sở dữ liệu.
 */
public record UserInfoResponse(String username, List<String> roles, CompanySummary company,
                               SubscriptionSummary subscription) {

    public UserInfoResponse(String username, List<String> roles, CompanySummary company) {
        this(username, roles, company, null);
    }

    public UserInfoResponse(String username, List<String> roles) {
        this(username, roles, null, null);
    }
}
