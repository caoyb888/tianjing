package com.tianzhu.tianjing.stream.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增/更新视频流通道请求
 */
@Data
public class StreamChannelRequest {
    @NotBlank
    private String sceneId;
    @NotBlank
    private String deviceCode;
    @NotBlank
    private String rtspUrl;
    /** 帧间隔（毫秒），默认 40ms = 25fps */
    @Min(20)
    private int frameIntervalMs = 40;
    private int imageWidth  = 1920;
    private int imageHeight = 1080;
    private int roiX = 0;
    private int roiY = 0;
    private int roiW = 1920;
    private int roiH = 1080;
}
