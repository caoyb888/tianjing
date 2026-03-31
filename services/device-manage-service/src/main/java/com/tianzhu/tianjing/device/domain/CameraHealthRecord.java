package com.tianzhu.tianjing.device.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
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

    private String deviceCode;
    private String sceneId;
    private OffsetDateTime checkAt;

    /** 拉普拉斯方差：<100=模糊，<50=严重模糊 */
    private BigDecimal laplacianVar;

    /** 平均亮度：<30=黑屏，>220=过曝 */
    private Short avgBrightness;

    private BigDecimal opticalFlowDx;
    private BigDecimal opticalFlowDy;

    /** 健康评分 0-100 */
    private Short healthScore;

    /** 故障类型：BLURRY / DARK / OVEREXPOSED / SHIFTED / OFFLINE */
    private String faultType;

    private String faultDesc;

    /** 已采取动作：WORK_ORDER / PAUSE_INFER / RECALIBRATE */
    private String actionTaken;

    private String workOrderId;
}
