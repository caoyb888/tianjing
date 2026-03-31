package com.tianzhu.tianjing.common.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 当前登录用户信息载体，存放于 SecurityContext
 */
@Getter
public class TianjingUserDetails implements UserDetails {

    private final String username;
    private final Long userId;
    private final List<String> roles;
    private final Collection<? extends GrantedAuthority> authorities;

    public TianjingUserDetails(String username,
                                Long userId,
                                List<String> roles,
                                Collection<? extends GrantedAuthority> authorities) {
        this.username = username;
        this.userId = userId;
        this.roles = roles;
        this.authorities = authorities;
    }

    @Override public String getPassword() { return null; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }

    /** 获取当前认证用户的 userId（Controller 层使用） */
    public static TianjingUserDetails current() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof TianjingUserDetails ud) {
            return ud;
        }
        return null;
    }

    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
