package com.tianzhu.tianjing.algomodel.controller;

import com.tianzhu.tianjing.algomodel.domain.ModelVersion;
import com.tianzhu.tianjing.algomodel.dto.ModelRegisterRequest;
import com.tianzhu.tianjing.algomodel.service.ModelVersionService;
import com.tianzhu.tianjing.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 模型版本内部注册接口（无 JWT）
 *
 * 路径 /internal/** 在 CommonSecurityConfig 中 permitAll，
 * 由训练容器（train_yolo_cpu.py）在训练完成后自动调用，
 * 无需人工介入即可创建 STAGING 状态的模型版本记录。
 *
 * 规范：CLAUDE.md §10.3；与 /internal/training/jobs/{id}/callback 模式一致。
 */
@Slf4j
@RestController
@RequestMapping("/internal/models")
@RequiredArgsConstructor
public class InternalModelController {

    /** 训练系统自动注册时使用的虚拟操作人标识 */
    private static final String TRAINING_SYSTEM_OPERATOR = "training-system";

    private final ModelVersionService modelVersionService;

    /**
     * POST /internal/models/register
     * 训练容器训练完成后，自动注册模型版本为 STAGING 状态。
     * 后续流程：人工在前端提交审核（promote）→ MODEL_REVIEWER 审核（approve）→ PRODUCTION 上线
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ModelVersion> registerFromTraining(
            @Valid @RequestBody ModelRegisterRequest request) {

        log.info("训练系统自动注册模型版本 plugin_id={} training_job_id={}",
                request.pluginId(), request.trainingJobId());

        ModelVersion mv = modelVersionService.registerVersion(request, TRAINING_SYSTEM_OPERATOR);
        return ApiResponse.ok(mv);
    }
}
