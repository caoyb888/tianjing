package com.tianzhu.tianjing.dashboard.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

/**
 * Sandbox 对比报告（对应 sandbox_compare_report 表）
 * 转正门禁：sandbox_precision >= prod_precision * 1.02（精度提升 ≥ 2%）
 */
@Data
@TableName("sandbox_compare_report")
public class SandboxCompareReport {
    @TableId(type = IdType.AUTO) private Long id;

    @TableField("report_id")
    private String reportId;

    @TableField("session_id")
    private String sessionId;

    @TableField("scene_id")
    private String sceneId;

    @TableField("hours_evaluated")
    private Double hoursEvaluated;

    /** DB列名 sandbox_precision */
    @TableField("sandbox_precision")
    private Double sandboxPrecision;

    @TableField("sandbox_recall")
    private Double sandboxRecall;

    /** DB列名 prod_precision */
    @TableField("prod_precision")
    private Double prodPrecision;

    @TableField("prod_recall")
    private Double prodRecall;

    /** 精度提升量（sandbox - production），转正门禁需 ≥ 0.02 */
    @TableField("precision_delta")
    private Double precisionDelta;

    @TableField("potential_gain_count")
    private Integer potentialGainCount;

    /** DB列名 sandbox_gpu_mb */
    @TableField("sandbox_gpu_mb")
    private Double sandboxGpuMb;

    /** DB列名 prod_gpu_mb */
    @TableField("prod_gpu_mb")
    private Double prodGpuMb;

    /** DB列名 sandbox_p99_ms */
    @TableField("sandbox_p99_ms")
    private Double sandboxP99Ms;

    /** DB列名 prod_p99_ms */
    @TableField("prod_p99_ms")
    private Double prodP99Ms;

    @TableField("gate_passed")
    private Boolean gatePassed;

    /** 转正门禁详情 JSON */
    @TableField("gate_detail_json")
    private String gateDetailJson;

    @TableField("promote_status")
    private String promoteStatus;

    @TableField("applied_at")
    private OffsetDateTime appliedAt;

    @TableField("approved_by")
    private String approvedBy;

    @TableField("approved_at")
    private OffsetDateTime approvedAt;

    @TableField("generated_at")
    private OffsetDateTime generatedAt;

    /** sandbox_compare_report 表无 created_at / created_by 列 */
    @TableField(exist = false)
    private OffsetDateTime createdAt;

    @TableField(exist = false)
    private String createdBy;
}
