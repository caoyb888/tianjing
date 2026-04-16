package com.tianzhu.tianjing.device.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 摄像头健康检测记录（对应 camera_health_record 分区表）
 * 规范：CLAUDE.md §4.2（health-monitor-service 写入，device-manage-service 查询）
 */
@Data
@TableName("camera_health_record")
public class CameraHealthRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("device_code")
    private String deviceCode;

    @TableField("scene_id")
    private String sceneId;

    @TableField("check_at")
    private OffsetDateTime checkAt;

    /** 拉普拉斯方差：<100=模糊，<50=严重模糊 */
    @TableField("laplacian_var")
    private BigDecimal laplacianVar;

    /** 平均亮度：<30=黑屏，>220=过曝 */
    @TableField("avg_brightness")
    private Short avgBrightness;

    @TableField("optical_flow_dx")
    private BigDecimal opticalFlowDx;

    @TableField("optical_flow_dy")
    private BigDecimal opticalFlowDy;

    /** 健康评分 0-100 */
    @TableField("health_score")
    private Short healthScore;

    /** 故障类型：BLURRY / DARK / OVEREXPOSED / SHIFTED / OFFLINE */
    @TableField("fault_type")
    private String faultType;

    @TableField("fault_desc")
    private String faultDesc;

    /** 已采取动作：WORK_ORDER / PAUSE_INFER / RECALIBRATE */
    @TableField("action_taken")
    private String actionTaken;

    @TableField("work_order_id")
    private String workOrderId;
}
