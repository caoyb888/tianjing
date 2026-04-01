package com.tianzhu.tianjing.stream.domain;

import lombok.Builder;
import lombok.Data;

/**
 * 视频流通道——每个活跃场景对应一个摄像头推流通道
 */
@Data
@Builder
public class StreamChannel {
    /** 所属场景ID（唯一标识） */
    private String sceneId;
    /** 设备编号 */
    private String deviceCode;
    /** RTSP 流地址 */
    private String rtspUrl;
    /** 帧间隔（毫秒）：40ms = 25fps */
    private int frameIntervalMs;
    /** 图像宽度（像素） */
    private int imageWidth;
    /** 图像高度（像素） */
    private int imageHeight;
    /** 感兴趣区域 */
    private Roi roi;
    /** 是否激活 */
    private boolean active;

    @Data
    @Builder
    public static class Roi {
        private int x;
        private int y;
        private int w;
        private int h;
    }
}
