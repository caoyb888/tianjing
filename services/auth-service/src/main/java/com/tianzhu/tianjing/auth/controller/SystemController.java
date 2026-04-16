package com.tianzhu.tianjing.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tianzhu.tianjing.auth.domain.SysRole;
import com.tianzhu.tianjing.auth.domain.SysUser;
import com.tianzhu.tianjing.auth.repository.SysRoleMapper;
import com.tianzhu.tianjing.auth.repository.SysUserMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

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
    // 新建用户
    // ──────────────────────────────────────────

    /**
     * POST /api/v1/system/users — 新建用户
     */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserItem> createUser(
            @Valid @RequestBody CreateUserRequest req,
            @AuthenticationPrincipal TianjingUserDetails operator) {

        // 用户名唯一性校验
        boolean exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.username())
                .eq(SysUser::getIsDeleted, false)) > 0;
        if (exists) {
            throw BusinessException.of(ErrorCode.PARAM_MISSING, "用户名已存在: " + req.username());
        }

        SysUser user = new SysUser();
        user.setUserId("U-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        user.setUsername(req.username());
        user.setDisplayName(req.displayName() != null ? req.displayName() : req.username());
        user.setPasswordHash(new BCryptPasswordEncoder().encode(req.password()));
        user.setEmail(req.email());
        user.setDeptCode(req.deptCode());
        user.setStatus("ACTIVE");
        user.setIsLocked(false);
        user.setFailedLoginCount(0);
        user.setCreatedBy(operator.getUsername());
        user.setUpdatedBy(operator.getUsername());
        OffsetDateTime now = OffsetDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userMapper.insert(user);

        // 分配角色
        if (req.roles() != null && !req.roles().isEmpty()) {
            for (String roleCode : req.roles()) {
                userMapper.insertUserRole(user.getUserId(), roleCode, operator.getUsername());
            }
        }

        List<String> roles = req.roles() != null ? req.roles() : List.of();
        return ApiResponse.ok(new UserItem(
                user.getUserId(), user.getUsername(), user.getDisplayName(),
                user.getEmail(), user.getPhone(), user.getDeptCode(),
                user.getStatus(), roles, null));
    }

    // ──────────────────────────────────────────
    // 删除用户
    // ──────────────────────────────────────────

    /**
     * DELETE /api/v1/system/users/{userId} — 逻辑删除用户
     */
    @DeleteMapping("/users/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteUser(
            @PathVariable String userId,
            @AuthenticationPrincipal TianjingUserDetails operator) {

        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserId, userId)
                .eq(SysUser::getIsDeleted, false));
        if (user == null) throw BusinessException.notFound(ErrorCode.USER_NOT_FOUND);
        if ("admin".equals(user.getUsername())) {
            throw BusinessException.of(ErrorCode.PARAM_MISSING, "不允许删除内置 admin 用户");
        }
        user.setIsDeleted(true);
        user.setUpdatedBy(operator.getUsername());
        user.setUpdatedAt(OffsetDateTime.now());
        userMapper.updateById(user);
        return ApiResponse.ok();
    }

    // ──────────────────────────────────────────
    // 更新用户角色
    // ──────────────────────────────────────────

    /**
     * PUT /api/v1/system/users/{userId}/roles — 覆盖更新用户角色
     */
    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateUserRoles(
            @PathVariable String userId,
            @RequestBody UpdateRolesRequest req,
            @AuthenticationPrincipal TianjingUserDetails operator) {

        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUserId, userId)
                .eq(SysUser::getIsDeleted, false));
        if (user == null) throw BusinessException.notFound(ErrorCode.USER_NOT_FOUND);

        userMapper.deleteUserRoles(userId);
        if (req.roles() != null) {
            for (String roleCode : req.roles()) {
                userMapper.insertUserRole(userId, roleCode, operator.getUsername());
            }
        }
        return ApiResponse.ok();
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
    // 内部 DTO
    // ──────────────────────────────────────────

    public record CreateUserRequest(
            @NotBlank String username,
            @JsonProperty("display_name") String displayName,
            @NotBlank String password,
            List<String> roles,
            String email,
            @JsonProperty("dept_code") String deptCode
    ) {}

    public record UpdateRolesRequest(List<String> roles) {}

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
