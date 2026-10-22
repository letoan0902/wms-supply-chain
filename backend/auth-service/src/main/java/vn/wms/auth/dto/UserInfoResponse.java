package vn.wms.auth.dto;

import java.util.List;

/** Thông tin người dùng hiện tại, lấy từ JWT trong request. */
public record UserInfoResponse(String username, List<String> roles) {
}
