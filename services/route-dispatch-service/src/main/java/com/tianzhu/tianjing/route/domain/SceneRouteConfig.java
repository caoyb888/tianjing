package com.tianzhu.tianjing.route.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

/**
 * 场景路由配置（从 Redis 反序列化）
 *
 * Redis key：tianjing:scene:active:{scene_id}
 * 由 scene-config-service 在 enable/disable 时写入（规范：CLAUDE.md §5.1）
 *
 * 注意：scene-config-service 写入的 JSON 使用 "status":"active" 字符串，
 * 此处通过 @JsonSetter("status") 将其映射到 active 布尔字段。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SceneRouteConfig {

    /** 场景 ID，如 SCENE-SINTER-005 */
    private String sceneId;

    /**
     * 当前激活模型版本 ID（可能是版本 ID 如 MV-xxx，也可能直接是 plugin_id）。
     * 路由时优先使用 algorithmConfig.pluginId，此字段用于向下兼容。
     */
    private String activeModelVersionId;

    /** 关联的设备/摄像头编码 */
    private String boundDeviceCode;

    /** 帧抽取间隔（毫秒），用于路由层过滤 */
    private Integer frameIntervalMs;

    /** true = 当前场景已激活，接受帧路由 */
    private boolean active;

    /**
     * 兼容 scene-config-service 写入的 "status":"active" 字符串格式。
     * Jackson 反序列化时将 "status" 字段路由到此 setter，转换为 active 布尔值。
     */
    @JsonSetter("status")
    public void setStatus(String status) {
        if ("active".equalsIgnoreCase(status)) {
            this.active = true;
        }
    }

    /** 算法插件配置（路由时读取 plugin_id 字段决定推理路由目标） */
    private AlgorithmConfig algorithmConfig;

    /** ROI 配置（透传至推理服务） */
    private RoiConfig roi;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AlgorithmConfig {
        /** 推理插件 ID，如 LOCAL-GPU-YOLO-V1 */
        private String plugin_id;
        /** 推理置信度阈值，透传至推理服务（低代码编排器 inf 节点 conf_threshold 参数） */
        private Double conf_threshold;
    }

    @Data
    public static class RoiConfig {
        private Integer x;
        private Integer y;
        private Integer w;
        private Integer h;
    }
}
