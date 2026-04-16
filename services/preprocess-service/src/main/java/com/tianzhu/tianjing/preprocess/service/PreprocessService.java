package com.tianzhu.tianjing.preprocess.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tianzhu.tianjing.preprocess.dto.FrameMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 图像预处理服务
 *
 * 职责：
 * 1. 接收帧抽取后的帧消息，写入预处理算法参数（去雾、增强、ROI 裁剪配置）
 * 2. 将携带预处理元数据的帧消息发布至 tianjing.frame.preprocessed Topic
 *    （route-dispatch-service 消费后路由至对应推理服务）
 *
 * 注意：实际图像增强操作（如去雾 CLAHE、亮度归一化）由 Python 推理服务在
 *       消费 tianjing.frame.preprocessed 时执行；Java 层仅负责配置下发。
 * 规范：CLAUDE.md §4.2、§6.1（算法插件接口 InferParams.extra）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreprocessService {

    private static final String TOPIC_PREPROCESSED = "tianjing.frame.preprocessed";

    /** 默认去雾强度，可通过 Nacos 动态下发（0.0 = 关闭，1.0 = 最强） */
    @Value("${tianjing.preprocess.dehaze-strength:0.6}")
    private double dehazeStrength;

    /** 是否启用自适应对比度增强（CLAHE） */
    @Value("${tianjing.preprocess.enable-clahe:true}")
    private boolean enableClahe;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 预处理流程：填充 preprocess_meta 后转发下游
     */
    public void process(FrameMessage frame) {
        // 填充预处理元数据（Python 推理服务读取 preprocess_meta 执行对应算法）
        Map<String, Object> meta = frame.getPreprocessMeta();
        meta.put("dehaze_strength", dehazeStrength);
        meta.put("enable_clahe",    enableClahe);
        meta.put("roi_crop",        buildRoiMeta(frame));
        meta.put("preprocess_version", "1.0");

        publish(frame);
    }

    private Map<String, Object> buildRoiMeta(FrameMessage frame) {
        if (frame.getRoi() == null) return Map.of();
        FrameMessage.Roi roi = frame.getRoi();
        return Map.of("x", roi.getX(), "y", roi.getY(), "w", roi.getW(), "h", roi.getH());
    }

    private void publish(FrameMessage frame) {
        try {
            String payload = objectMapper.writeValueAsString(frame);
            kafkaTemplate.send(TOPIC_PREPROCESSED, frame.getSceneId(), payload);
            log.debug("预处理完成 scene_id={} frame_id={} is_sandbox={}",
                    frame.getSceneId(), frame.getFrameId(), frame.isSandbox());
        } catch (Exception e) {
            log.error("预处理帧发布失败 scene_id={} frame_id={}", frame.getSceneId(), frame.getFrameId(), e);
        }
    }
}
