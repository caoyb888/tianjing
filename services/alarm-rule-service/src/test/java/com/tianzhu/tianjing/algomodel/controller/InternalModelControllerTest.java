package com.tianzhu.tianjing.algomodel.controller;

import com.tianzhu.tianjing.algomodel.domain.ModelVersion;
import com.tianzhu.tianjing.algomodel.dto.ModelRegisterRequest;
import com.tianzhu.tianjing.algomodel.service.ModelVersionService;
import com.tianzhu.tianjing.common.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * InternalModelController 单元测试
 *
 * 内部注册接口（T-05）无 JWT，由训练脚本在 COMPLETED 回调后调用。
 * 业务逻辑全在 ModelVersionService（已有完整测试），此处只验证：
 *   1. 调用时使用固定操作人 "training-system"（而非真实用户）
 *   2. 返回值透传 service 结果
 * 规范：CLAUDE.md §12.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InternalModelController — 训练系统自动注册模型版本")
class InternalModelControllerTest {

    @Mock
    private ModelVersionService modelVersionService;

    @InjectMocks
    private InternalModelController controller;

    @Test
    @DisplayName("registerFromTraining：使用固定操作人 training-system，透传 service 返回值")
    void registerFromTraining_usesTrainingSystemOperator_andReturnsVersion() {
        ModelRegisterRequest req = new ModelRegisterRequest(
                "ATOM-DETECT-YOLO-V1", "v1743500000",
                "mlflow-run-abc123",
                "minio://tianjing-models-staging/ATOM-DETECT-YOLO-V1/JOB-001/best.onnx",
                "JOB-001");

        ModelVersion expected = new ModelVersion();
        expected.setVersionId("MV-ABCD1234");
        expected.setPluginId("ATOM-DETECT-YOLO-V1");
        expected.setStatus("STAGING");
        expected.setCreatedBy("training-system");

        when(modelVersionService.registerVersion(req, "training-system")).thenReturn(expected);

        ApiResponse<ModelVersion> response = controller.registerFromTraining(req);

        assertThat(response.getData().getVersionId()).isEqualTo("MV-ABCD1234");
        assertThat(response.getData().getStatus()).isEqualTo("STAGING");
        // 验证使用了固定操作人，而非真实用户
        verify(modelVersionService).registerVersion(req, "training-system");
        verify(modelVersionService, never()).registerVersion(any(), argThat(op -> !op.equals("training-system")));
    }
}
