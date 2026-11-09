package vn.wms.auth.dto.users;

import vn.wms.auth.company.dto.CompanySummary;

import java.util.List;

/** Thông tin người dùng hiện tại, lấy từ JWT trong request; company đọc lại từ cơ sở dữ liệu. */
public record UserInfoResponse(String username, List<String> roles, CompanySummary company) {

    public UserInfoResponse(String username, List<String> roles) {
        this(username, roles, null);
    }
}
