package vn.wms.auth.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

@Getter
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final Long companyId;
    private final String plan;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id, String username, Collection<? extends GrantedAuthority> authorities) {
        this(id, username, null, null, authorities);
    }

    public UserPrincipal(Long id, String username, Long companyId, Collection<? extends GrantedAuthority> authorities) {
        this(id, username, companyId, null, authorities);
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
