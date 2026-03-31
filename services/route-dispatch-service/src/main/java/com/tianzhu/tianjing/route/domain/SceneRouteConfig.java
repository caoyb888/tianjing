package com.tianzhu.tianjing.route.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 场景路由配置（从 Redis 反序列化）
 *
 * Redis key：tianjing:scene:active:{scene_id}
 * 由 scene-config-service 在 enable/disable 时写入（规范：CLAUDE.md §5.1）
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SceneRouteConfig {

    /** 场景 ID，如 SCENE-SINTER-005 */
    private String sceneId;

    /** 绑定的推理插件 ID */
    private String activeModelVersionId;

    /** 关联的设备/摄像头编码 */
    private String boundDeviceCode;

    /** 帧抽取间隔（毫秒），用于路由层过滤 */
    private Integer frameIntervalMs;

    /** true = 当前场景已激活，接受帧路由 */
    private boolean active;

    /** ROI 配置（透传至推理服务） */
    private RoiConfig roi;

    @Data
    public static class RoiConfig {
        private Integer x;
        private Integer y;
        private Integer w;
        private Integer h;
    }
}
