package com.tianzhu.tianjing.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record LoginResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("token_type") String tokenType,
        UserInfo user
) {
    public record UserInfo(
            @JsonProperty("user_id") String userId,
            String username,
            @JsonProperty("display_name") String displayName,
            List<String> roles
    ) {}

    public static LoginResponse of(String token, long expiresIn, com.tianzhu.tianjing.auth.domain.SysUser user) {
        return new LoginResponse(
                token,
                expiresIn,
                "Bearer",
                new UserInfo(user.getUserId(), user.getUsername(), user.getDisplayName(), user.getRoles())
        );
    }
}
