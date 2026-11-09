package vn.wms.auth.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import vn.wms.common.api.ErrorCode;
import vn.wms.common.exception.ApiException;

import java.util.Collection;

@Getter
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    /** ID doanh nghiệp của người dùng, đọc từ claim companyId của access token. */
    private final Long companyId;
    /** Gói thuê bao của doanh nghiệp. */
    private final String plan;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id, String username, Long companyId, Collection<? extends GrantedAuthority> authorities) {
        this(id, username, companyId, null, authorities);
    }

    public UserPrincipal(Long id, String username, Collection<? extends GrantedAuthority> authorities) {
        this(id, username, null, null, authorities);
    }

    /**
     * Lấy companyId của người đang đăng nhập. Token không mang doanh nghiệp (token cũ cấp trước WMS-22.2)
     * thì từ chối với COMPANY_REQUIRED, tuyệt đối không thao tác khi chưa biết doanh nghiệp.
     */
    public static Long requireCompanyId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal
                && principal.getCompanyId() != null) {
            return principal.getCompanyId();
        }
        throw new ApiException(ErrorCode.COMPANY_REQUIRED,
                "Không xác định được doanh nghiệp của tài khoản, vui lòng đăng nhập lại");
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null; // JWT không cần lưu hay trả về password
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}
