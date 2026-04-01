package com.tianzhu.tianjing.stream.service;

import com.tianzhu.tianjing.stream.domain.StreamChannel;
import com.tianzhu.tianjing.stream.dto.FrameMessage;
import com.tianzhu.tianjing.stream.dto.StreamChannelRequest;
import com.tianzhu.tianjing.stream.kafka.KafkaFramePublisher;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

/**
 * 视频流接入服务
 *
 * 职责：
 * 1. 管理摄像头 RTSP 推流通道（CRUD + start/stop）
 * 2. 每个活跃通道在独立虚拟线程中按帧间隔循环发布帧消息到 Kafka
 *
 * 注意：生产环境中实际 RTSP 帧抓取由 Native Sidecar（FFmpeg/OpenCV 进程）完成，
 *       本服务负责帧元数据管理和 Kafka 发布。
 * 规范：CLAUDE.md §5.1（虚拟线程）、§8.1（Kafka Topic）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamIngestService {

    // 通道注册表（sceneId → StreamChannel）
    private final ConcurrentHashMap<String, StreamChannel> channels = new ConcurrentHashMap<>();
    // 活跃流任务（sceneId → Future）
    private final ConcurrentHashMap<String, Future<?>> activeTasks = new ConcurrentHashMap<>();

    private final KafkaFramePublisher framePublisher;

    // 虚拟线程执行器（CLAUDE.md §5.1）
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    // ── 通道管理 ────────────────────────────────────────────

    public StreamChannel addChannel(StreamChannelRequest req) {
        StreamChannel channel = StreamChannel.builder()
                .sceneId(req.getSceneId())
                .deviceCode(req.getDeviceCode())
                .rtspUrl(req.getRtspUrl())
                .frameIntervalMs(req.getFrameIntervalMs())
                .imageWidth(req.getImageWidth())
                .imageHeight(req.getImageHeight())
                .roi(StreamChannel.Roi.builder()
                        .x(req.getRoiX()).y(req.getRoiY())
                        .w(req.getRoiW()).h(req.getRoiH())
                        .build())
                .active(false)
                .build();
        channels.put(req.getSceneId(), channel);
        log.info("注册视频流通道 scene_id={} device_code={} rtsp_url={}",
                req.getSceneId(), req.getDeviceCode(), req.getRtspUrl());
        return channel;
    }

    public List<StreamChannel> listChannels() {
        return new ArrayList<>(channels.values());
    }

    public Optional<StreamChannel> getChannel(String sceneId) {
        return Optional.ofNullable(channels.get(sceneId));
    }

    public void removeChannel(String sceneId) {
        stopStream(sceneId);
        channels.remove(sceneId);
        log.info("移除视频流通道 scene_id={}", sceneId);
    }

    // ── 流启动/停止 ──────────────────────────────────────────

    public void startStream(String sceneId) {
        StreamChannel channel = channels.get(sceneId);
        if (channel == null) throw new IllegalArgumentException("通道不存在: " + sceneId);
        if (activeTasks.containsKey(sceneId)) {
            log.warn("通道已在运行中 scene_id={}", sceneId);
            return;
        }

        channel.setActive(true);
        Future<?> task = executor.submit(() -> runStreamLoop(channel));
        activeTasks.put(sceneId, task);
        log.info("启动视频流 scene_id={} frame_interval_ms={}", sceneId, channel.getFrameIntervalMs());
    }

    public void stopStream(String sceneId) {
        Future<?> task = activeTasks.remove(sceneId);
        if (task != null) {
            task.cancel(true);
            log.info("停止视频流 scene_id={}", sceneId);
        }
        StreamChannel channel = channels.get(sceneId);
        if (channel != null) channel.setActive(false);
    }

    // ── 内部循环（每个通道一个虚拟线程） ─────────────────────

    private void runStreamLoop(StreamChannel channel) {
        long frameSeq = 0;
        while (!Thread.currentThread().isInterrupted()) {
            try {
                long now = System.currentTimeMillis();
                String frameId = String.format("%s_%015d_%06d",
                        channel.getSceneId().toLowerCase().replace("-", "_"), now, frameSeq++);

                // 构建 MinIO 帧图像 URL（规范：CLAUDE.md §7.3 命名规范）
                String date = java.time.LocalDate.now().toString();
                String imageUrl = String.format("minio://tianjing-frames-prod/%s/%s/%d_%s.jpg",
                        channel.getDeviceCode(), date, now, frameId);

                FrameMessage msg = FrameMessage.builder()
                        .frameId(frameId)
                        .sceneId(channel.getSceneId())
                        .timestampMs(now)
                        .isSandbox(false)
                        .imageUrl(imageUrl)
                        .imageWidth(channel.getImageWidth())
                        .imageHeight(channel.getImageHeight())
                        .roi(FrameMessage.Roi.builder()
                                .x(channel.getRoi().getX())
                                .y(channel.getRoi().getY())
                                .w(channel.getRoi().getW())
                                .h(channel.getRoi().getH())
                                .build())
                        .build();

                framePublisher.publish(msg);

                Thread.sleep(channel.getFrameIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("帧发布异常 scene_id={}", channel.getSceneId(), e);
            }
        }
        log.info("视频流循环结束 scene_id={}", channel.getSceneId());
    }

    @PreDestroy
    public void shutdown() {
        activeTasks.keySet().forEach(this::stopStream);
        executor.shutdownNow();
    }
}
