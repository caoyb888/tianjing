package com.tianzhu.tianjing.drift.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.drift.domain.SysOperationLog;
import com.tianzhu.tianjing.drift.domain.SysRole;
import com.tianzhu.tianjing.drift.domain.SysUserView;
import com.tianzhu.tianjing.drift.repository.SysOperationLogMapper;
import com.tianzhu.tianjing.drift.repository.SysRoleMapper;
import com.tianzhu.tianjing.drift.repository.SysUserViewMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 系统管理接口（用户管理、角色列表、审计日志）
 * 规范：API 接口规范 V3.1 §6.14
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final SysUserViewMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysOperationLogMapper operationLogMapper;

    // ──────────────────────────────────────────
    // 用户管理
    // ──────────────────────────────────────────

    /**
     * GET /system/users — 分页查询用户列表（含角色）
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<SysUserView>> listUsers(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String role,
            @RequestParam(required = false)    String keyword) {

        Page<SysUserView> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysUserView> wrapper = new LambdaQueryWrapper<SysUserView>()
                .eq(SysUserView::getIsDeleted, false)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(SysUserView::getUsername, keyword)
                        .or().like(SysUserView::getDisplayName, keyword))
                .orderByAsc(SysUserView::getId);

        var result = userMapper.selectPage(pageParam, wrapper);

        // 填充每个用户的角色列表
        List<SysUserView> items = result.getRecords().stream().peek(u -> {
            List<String> roles = userMapper.selectRolesByUserId(u.getUserId());
            u.setRoles(roles);
        }).filter(u ->
            role == null || role.isBlank() || (u.getRoles() != null && u.getRoles().contains(role))
        ).toList();

        long total = (role == null || role.isBlank()) ? result.getTotal() : items.size();
        return ApiResponse.page(PageResult.of(total, page, size, items));
    }

    /**
     * GET /system/roles — 查询全部角色定义
     */
    @GetMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<SysRole>> listRoles() {
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getIsDeleted, false)
                        .orderByAsc(SysRole::getId));
        return ApiResponse.ok(roles);
    }

    /**
     * POST /system/users — 新建用户
     */
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<SysUserView> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @AuthenticationPrincipal TianjingUserDetails operator) {

        // 校验用户名唯一
        long count = userMapper.selectCount(new LambdaQueryWrapper<SysUserView>()
                .eq(SysUserView::getUsername, request.username())
                .eq(SysUserView::getIsDeleted, false));
        if (count > 0) {
            throw BusinessException.of(ErrorCode.PARAM_MISSING, "用户名已存在");
        }

        SysUserView user = new SysUserView();
        user.setUserId("USR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        user.setUsername(request.username());
        user.setDisplayName(request.displayName());
        user.setEmail(request.email());
        user.setPhone(request.phone());
        user.setDeptCode(request.deptCode());
        user.setStatus("ACTIVE");

        // 密码直接写入 password_hash 列（需要暴露该列）
        // 由于 SysUserView 不含 passwordHash 字段，通过 native SQL 插入
        userMapper.insertUserWithPassword(
                user.getUserId(), user.getUsername(), user.getDisplayName(),
                new BCryptPasswordEncoder().encode(request.password()),
                user.getEmail(), user.getPhone(), user.getDeptCode(),
                operator.getUsername());

        // 分配角色
        if (request.roles() != null) {
            for (String roleCode : request.roles()) {
                userMapper.insertUserRole(user.getUserId(), roleCode, operator.getUsername());
            }
        }

        // 查询新建的用户记录返回
        SysUserView created = userMapper.selectOne(new LambdaQueryWrapper<SysUserView>()
                .eq(SysUserView::getUserId, user.getUserId()));
        created.setRoles(request.roles() != null ? request.roles() : List.of());

        // 写操作审计日志
        writeAuditLog(operator.getUsername(), "USER_CREATE", "SYS_USER", user.getUserId(),
                null, "{\"username\":\"" + user.getUsername() + "\"}");

        log.info("新建用户 userId={} username={} roles={} operator={}",
                user.getUserId(), user.getUsername(), request.roles(), operator.getUsername());
        return ApiResponse.ok(created);
    }

    /**
     * DELETE /system/users/{user_id} — 删除用户（逻辑删除）
     */
    @DeleteMapping("/users/{user_id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deleteUser(
            @PathVariable("user_id") String userId,
            @AuthenticationPrincipal TianjingUserDetails operator) {

        SysUserView user = userMapper.selectOne(new LambdaQueryWrapper<SysUserView>()
                .eq(SysUserView::getUserId, userId)
                .eq(SysUserView::getIsDeleted, false));
        if (user == null) throw BusinessException.notFound(ErrorCode.USER_NOT_FOUND);

        // 禁止删除自己
        if (user.getUsername().equals(operator.getUsername())) {
            throw BusinessException.of(ErrorCode.PARAM_MISSING, "不能删除当前登录账号");
        }

        userMapper.softDeleteByUserId(userId, operator.getUsername());

        writeAuditLog(operator.getUsername(), "USER_DELETE", "SYS_USER", userId,
                "{\"username\":\"" + user.getUsername() + "\"}", null);

        log.info("删除用户 userId={} username={} operator={}", userId, user.getUsername(), operator.getUsername());
        return ApiResponse.ok();
    }

    /**
     * PUT /system/users/{user_id}/roles — 更新用户角色
     */
    @PutMapping("/users/{user_id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateUserRoles(
            @PathVariable("user_id") String userId,
            @RequestBody UpdateRolesRequest request,
            @AuthenticationPrincipal TianjingUserDetails operator) {

        SysUserView user = userMapper.selectOne(new LambdaQueryWrapper<SysUserView>()
                .eq(SysUserView::getUserId, userId)
                .eq(SysUserView::getIsDeleted, false));
        if (user == null) throw BusinessException.notFound(ErrorCode.USER_NOT_FOUND);

        List<String> oldRoles = userMapper.selectRolesByUserId(userId);
        userMapper.deleteUserRoles(userId);
        for (String roleCode : request.roles()) {
            userMapper.insertUserRole(userId, roleCode, operator.getUsername());
        }

        writeAuditLog(operator.getUsername(), "USER_ROLE_UPDATE", "SYS_USER", userId,
                "{\"roles\":" + oldRoles + "}", "{\"roles\":" + request.roles() + "}");

        log.info("更新用户角色 userId={} roles={} operator={}", userId, request.roles(), operator.getUsername());
        return ApiResponse.ok();
    }

    // ──────────────────────────────────────────
    // 审计日志
    // ──────────────────────────────────────────

    /**
     * GET /system/operation-logs — 操作审计日志
     */
    @GetMapping("/operation-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<SysOperationLog>> listOperationLogs(
            @RequestParam(defaultValue = "1")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String operator,
            @RequestParam(required = false)    String action,
            @RequestParam(required = false)    String startTime,
            @RequestParam(required = false)    String endTime) {

        Page<SysOperationLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .eq(operator != null, SysOperationLog::getOperator, operator)
                .eq(action != null, SysOperationLog::getOperation, action)
                .ge(startTime != null, SysOperationLog::getOperatedAt, startTime)
                .le(endTime != null, SysOperationLog::getOperatedAt, endTime)
                .orderByDesc(SysOperationLog::getOperatedAt);
        var result = operationLogMapper.selectPage(pageParam, wrapper);
        return ApiResponse.page(PageResult.of(result.getTotal(), page, size, result.getRecords()));
    }

    // ──────────────────────────────────────────
    // 私有方法
    // ──────────────────────────────────────────

    private void writeAuditLog(String op, String operation, String targetType,
                                String targetId, String before, String after) {
        try {
            SysOperationLog log = new SysOperationLog();
            log.setLogId("LOG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
            log.setOperator(op);
            log.setOperation(operation);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setBeforeJson(before);
            log.setAfterJson(after);
            log.setRemark("SUCCESS");
            log.setOperatedAt(OffsetDateTime.now());
            operationLogMapper.insert(log);
        } catch (Exception e) {
            // 审计日志失败不影响主流程
        }
    }

    // ──────────────────────────────────────────
    // 请求体 DTO
    // ──────────────────────────────────────────

    public record CreateUserRequest(
            @NotBlank @JsonProperty("username")     String username,
            @NotBlank @JsonProperty("display_name") String displayName,
            @NotBlank @Size(min = 6)
            @JsonProperty("password")               String password,
            @JsonProperty("email")                  String email,
            @JsonProperty("phone")                  String phone,
            @JsonProperty("dept_code")              String deptCode,
            @JsonProperty("roles")                  List<String> roles
    ) {}

    public record UpdateRolesRequest(
            @JsonProperty("roles") List<String> roles
    ) {}
}
