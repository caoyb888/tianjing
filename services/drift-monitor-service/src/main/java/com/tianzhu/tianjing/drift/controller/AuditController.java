package com.tianzhu.tianjing.drift.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.common.response.PageResult;
import com.tianzhu.tianjing.drift.domain.DataSyncAudit;
import com.tianzhu.tianjing.drift.repository.DataSyncAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 数据同步审计接口
 * 规范：API 接口规范 V3.1 §6.12（1 个端点）
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final DataSyncAuditMapper auditMapper;

    /**
     * GET /audit/data-sync — 数据同步审计日志（等保合规）
     */
    @GetMapping("/data-sync")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PageResult<DataSyncAudit>> listAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sync_type,
            @RequestParam(required = false) String status) {
        Page<DataSyncAudit> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<DataSyncAudit> wrapper = new LambdaQueryWrapper<DataSyncAudit>()
                .eq(status != null, DataSyncAudit::getSyncStatus, status)
                .orderByDesc(DataSyncAudit::getCreatedAt);
        var result = auditMapper.selectPage(pageParam, wrapper);
        return ApiResponse.page(PageResult.of(result.getTotal(), page, size, result.getRecords()));
    }
}
