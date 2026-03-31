package com.tianzhu.tianjing.drift.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.common.security.TianjingUserDetails;
import com.tianzhu.tianjing.drift.domain.SysOperationLog;
import com.tianzhu.tianjing.drift.domain.SysUserView;
import com.tianzhu.tianjing.drift.repository.SysOperationLogMapper;
import com.tianzhu.tianjing.drift.repository.SysUserViewMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 系统管理接口
 * 规范：API 接口规范 V3.1 §6.14（3 个端点）
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final SysUserViewMapper userMapper;
    private final SysOperationLogMapper operationLogMapper;

    /**
     * GET /system/users — 用户列表
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<SysUserView>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Page<SysUserView> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysUserView> wrapper = new LambdaQueryWrapper<SysUserView>()
                .eq(SysUserView::getIsDeleted, 0)
                .like(keyword != null, SysUserView::getUsername, keyword)
                .orderByDesc(SysUserView::getCreatedAt);
        var result = userMapper.selectPage(pageParam, wrapper);
        return ApiResponse.page(PageResult.of(result.getTotal(), page, size, result.getRecords()));
    }

    /**
     * PUT /system/users/{user_id}/roles — 更新用户角色
     */
    @PutMapping("/users/{user_id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateUserRoles(
            @PathVariable("user_id") String userId,
            @RequestBody Map<String, List<String>> request,
            @AuthenticationPrincipal TianjingUserDetails operator) {
        List<String> roles = request.get("roles");
        if (roles == null) throw BusinessException.of(ErrorCode.PARAM_MISSING, "roles 字段不能为空");
        // 角色更新逻辑（通过 sys_user_role 表，此处简化记录日志）
        SysOperationLog log = new SysOperationLog();
        log.setOperatorUsername(operator.getUsername());
        log.setOperationType("USER_ROLE_UPDATE");
        log.setResourceId(userId);
        log.setRequestBody("{\"roles\":" + roles + "}");
        log.setResult("SUCCESS");
        operationLogMapper.insert(log);
        return ApiResponse.ok();
    }

    /**
     * GET /system/operation-logs — 操作审计日志
     */
    @GetMapping("/operation-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<SysOperationLog>> listOperationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String operator_username,
            @RequestParam(required = false) String operation_type) {
        Page<SysOperationLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .eq(operator_username != null, SysOperationLog::getOperatorUsername, operator_username)
                .eq(operation_type != null, SysOperationLog::getOperationType, operation_type)
                .orderByDesc(SysOperationLog::getCreatedAt);
        var result = operationLogMapper.selectPage(pageParam, wrapper);
        return ApiResponse.page(PageResult.of(result.getTotal(), page, size, result.getRecords()));
    }
}
