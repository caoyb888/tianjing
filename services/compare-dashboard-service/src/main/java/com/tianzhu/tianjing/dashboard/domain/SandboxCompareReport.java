package com.tianzhu.tianjing.dashboard.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * Sandbox 对比报告（对应 sandbox_compare_report 表）
 */
@Data
@TableName(value = "sandbox_compare_report", schema = "tianjing_sandbox")
public class SandboxCompareReport {
    @TableId(type = IdType.AUTO) private Long id;
    private String reportId;
    private String sessionId;
    private String sceneId;
    private Double productionPrecision;
    private Double sandboxPrecision;
    /** 精度提升量（sandbox - production），转正门禁需 ≥ 0.02 */
    private Double precisionDelta;
    private Double productionGpuMemoryGb;
    private Double sandboxGpuMemoryGb;
    private Double productionP99LatencyMs;
    private Double sandboxP99LatencyMs;
    /** 转正门禁详情 JSON */
    private String gateDetailJson;
    @TableField(fill = FieldFill.INSERT) private OffsetDateTime createdAt;
    private String createdBy;
}
