package com.tianzhu.tianjing.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tianzhu.tianjing.auth.domain.SysUser;

import java.util.List;

/**
 * GET /auth/me 响应体
 * 与前端 UserInfo 接口对齐（types/index.ts）
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserInfoResponse(
        @JsonProperty("user_id")    String userId,
        String username,
        @JsonProperty("display_name") String displayName,
        List<String> roles,
        String email
) {
    public static UserInfoResponse of(SysUser user, List<String> roles) {
        return new UserInfoResponse(
                user.getUserId(),
                user.getUsername(),
                user.getDisplayName(),
                roles,
                user.getEmail()
        );
    }
}
