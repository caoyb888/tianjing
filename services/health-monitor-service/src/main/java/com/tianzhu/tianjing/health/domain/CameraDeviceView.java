package com.tianzhu.tianjing.health.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * health-monitor-service 对 camera_device 的只读视图（仅用于 BaseMapper 泛型占位）
 * 实际查询通过 @Select 自定义 SQL，不使用 BaseMapper 默认方法
 */
@Data
@TableName("camera_device")
public class CameraDeviceView {
    @TableId
    private Long id;
    @TableField("device_code")
    private String deviceCode;
}
