package com.tianzhu.tianjing.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tianzhu.tianjing.auth.domain.SysRole;
import com.tianzhu.tianjing.auth.domain.SysUser;
import com.tianzhu.tianjing.auth.repository.SysRoleMapper;
import com.tianzhu.tianjing.auth.repository.SysUserMapper;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统管理接口（用户列表、角色列表）
 * 规范：API 接口规范 V3.1 §6.x
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;

    // ──────────────────────────────────────────
    // 用户列表
    // ──────────────────────────────────────────

    /**
     * GET /api/v1/system/users — 分页查询用户列表（含角色）
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<UserItem>> listUsers(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String role,
            @RequestParam(required = false)    String keyword) {

        Page<SysUser> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getIsDeleted, false)
                .like(keyword != null && !keyword.isBlank(), SysUser::getUsername, keyword)
                .orderByAsc(SysUser::getId);

        var result = userMapper.selectPage(pageParam, wrapper);

        List<UserItem> items = result.getRecords().stream().map(u -> {
            List<String> roles = userMapper.selectRolesByUserId(u.getId());
            // 若指定了 role 过滤，跳过不匹配的用户
            if (role != null && !role.isBlank() && !roles.contains(role)) return null;
            return new UserItem(
                    u.getUserId(), u.getUsername(), u.getDisplayName(),
                    u.getEmail(), u.getPhone(), u.getDeptCode(),
                    u.getStatus(), roles, u.getLastLoginAt() != null ? u.getLastLoginAt().toString() : null
            );
        }).filter(item -> item != null).toList();

        long total = role != null && !role.isBlank() ? items.size() : result.getTotal();
        return ApiResponse.page(PageResult.of(total, page, size, items));
    }

    // ──────────────────────────────────────────
    // 角色列表
    // ──────────────────────────────────────────

    /**
     * GET /api/v1/system/roles — 查询所有角色定义
     */
    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<RoleItem>> listRoles() {
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getIsDeleted, false)
                        .orderByAsc(SysRole::getId));
        List<RoleItem> items = roles.stream()
                .map(r -> new RoleItem(r.getRoleCode(), r.getRoleName(), r.getDescription()))
                .toList();
        return ApiResponse.ok(items);
    }

    // ──────────────────────────────────────────
    // 内部 DTO（不复用 UserInfoResponse，避免暴露敏感字段）
    // ──────────────────────────────────────────

    public record UserItem(
            @JsonProperty("user_id")      String userId,
            String username,
            @JsonProperty("display_name") String displayName,
            String email,
            String phone,
            @JsonProperty("dept_code")    String deptCode,
            String status,
            List<String> roles,
            @JsonProperty("last_login_at") String lastLoginAt
    ) {}

    public record RoleItem(
            @JsonProperty("role_code") String roleCode,
            @JsonProperty("role_name") String roleName,
            String description
    ) {}
}
