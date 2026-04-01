package com.tianzhu.tianjing.stream.controller;

import com.tianzhu.tianjing.common.response.ApiResponse;
import com.tianzhu.tianjing.stream.domain.StreamChannel;
import com.tianzhu.tianjing.stream.dto.StreamChannelRequest;
import com.tianzhu.tianjing.stream.service.StreamIngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 视频流通道管理接口
 * 规范：API 接口规范 V3.1 §REST URL 规范
 */
@RestController
@RequestMapping("/api/v1/streams")
@RequiredArgsConstructor
public class StreamChannelController {

    private final StreamIngestService streamIngestService;

    @GetMapping
    public ApiResponse<List<StreamChannel>> listChannels() {
        return ApiResponse.ok(streamIngestService.listChannels());
    }

    @GetMapping("/{sceneId}")
    public ApiResponse<StreamChannel> getChannel(@PathVariable String sceneId) {
        return streamIngestService.getChannel(sceneId)
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.notFound("通道不存在: " + sceneId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SCENE_EDITOR')")
    public ApiResponse<StreamChannel> addChannel(@Valid @RequestBody StreamChannelRequest request) {
        return ApiResponse.ok(streamIngestService.addChannel(request));
    }

    @DeleteMapping("/{sceneId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCENE_EDITOR')")
    public ApiResponse<Void> removeChannel(@PathVariable String sceneId) {
        streamIngestService.removeChannel(sceneId);
        return ApiResponse.ok();
    }

    /** 启动推流（通道开始向 Kafka tianjing.frame.production 发布帧消息） */
    @PostMapping("/{sceneId}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCENE_EDITOR')")
    public ApiResponse<Void> startStream(@PathVariable String sceneId) {
        streamIngestService.startStream(sceneId);
        return ApiResponse.ok();
    }

    /** 停止推流 */
    @PostMapping("/{sceneId}/stop")
    @PreAuthorize("hasAnyRole('ADMIN', 'SCENE_EDITOR')")
    public ApiResponse<Void> stopStream(@PathVariable String sceneId) {
        streamIngestService.stopStream(sceneId);
        return ApiResponse.ok();
    }
}
