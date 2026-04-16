package com.tianzhu.tianjing.drift.training;

import com.tianzhu.tianjing.drift.domain.TrainJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Docker 训练作业启动器（Sprint 3 测试环境方案，无需 K8s）
 *
 * 通过 ProcessBuilder 在宿主机执行 docker run，非阻塞启动训练容器。
 * 训练容器通过回调接口 POST /internal/training/jobs/{job_id}/callback 汇报进度。
 *
 * Sprint 5 GPU 正式环境升级路径：
 *   将此 Bean 替换为 KubernetesTrainJobLauncher，
 *   通过配置项 tianjing.train.launcher=k8s 切换，TrainingController 无需修改。
 */
@Slf4j
@Component
public class DockerTrainJobLauncher {

    @Value("${tianjing.train.docker-image:tianjing/yolo-trainer:cpu-latest}")
    private String trainerImage;

    @Value("${tianjing.train.platform-callback-url:http://localhost:8089}")
    private String platformCallbackUrl;

    @Value("${tianjing.train.minio-endpoint:localhost:9000}")
    private String minioEndpoint;

    @Value("${tianjing.train.minio-access-key:minioadmin}")
    private String minioAccessKey;

    @Value("${tianjing.train.minio-secret-key:minioadmin}")
    private String minioSecretKey;

    @Value("${tianjing.train.mlflow-tracking-uri:http://localhost:5000}")
    private String mlflowTrackingUri;

    /**
     * 启动训练容器（非阻塞：docker run 返回容器 ID 即返回，训练在后台继续）。
     *
     * @param job 已插入数据库的训练作业（status=PENDING）
     * @throws RuntimeException docker run 命令本身失败时抛出（调用方据此将 job 置为 FAILED）
     */
    public void launch(TrainJob job) {
        // 容器名：去除下划线，全小写，确保符合 Docker 命名规范
        String containerName = "train-" + job.getJobId().toLowerCase().replace("_", "-");

        // 容器内无法用 localhost/127.0.0.1 访问宿主机，统一替换为 host.docker.internal
        String containerMinioEndpoint = minioEndpoint
                .replace("localhost", "host.docker.internal")
                .replace("127.0.0.1", "host.docker.internal");
        String containerCallbackUrl = platformCallbackUrl
                .replace("localhost", "host.docker.internal")
                .replace("127.0.0.1", "host.docker.internal");
        String containerMlflowUri = mlflowTrackingUri
                .replace("localhost", "host.docker.internal")
                .replace("127.0.0.1", "host.docker.internal");

        List<String> cmd = new ArrayList<>(List.of(
            "docker", "run", "--rm", "-d",
            "--name", containerName,
            // Linux Docker：允许容器通过 host.docker.internal 回调宿主机服务
            "--add-host=host.docker.internal:host-gateway",
            "-e", "JOB_ID="              + job.getJobId(),
            "-e", "PLUGIN_ID="           + job.getPluginId(),
            "-e", "DATASET_VERSION_ID="  + job.getDatasetVersionId(),
            "-e", "TRAIN_CONFIG_JSON="   + job.getTrainConfigJson(),
            "-e", "MINIO_ENDPOINT="      + containerMinioEndpoint,
            "-e", "MINIO_ACCESS_KEY="    + minioAccessKey,
            "-e", "MINIO_SECRET_KEY="    + minioSecretKey,
            "-e", "MLFLOW_TRACKING_URI=" + containerMlflowUri,
            // 回调地址：训练脚本向此地址汇报 RUNNING/COMPLETED/FAILED
            "-e", "PLATFORM_CALLBACK_URL=" + containerCallbackUrl,
            trainerImage
        ));

        log.info("启动训练容器 job_id={} container={} image={}",
                job.getJobId(), containerName, trainerImage);

        try {
            Process process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();

            // 等待 docker run 命令本身退出（约 1 秒），获取容器 ID 或错误信息
            String output = new String(process.getInputStream().readAllBytes()).strip();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("训练容器启动成功 job_id={} container_id={}",
                        job.getJobId(), output.substring(0, Math.min(12, output.length())));
            } else {
                log.error("训练容器启动失败 job_id={} exit_code={} detail={}",
                        job.getJobId(), exitCode, output);
                throw new RuntimeException("docker run 失败 (exit=" + exitCode + "): " + output);
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("训练容器启动异常: " + e.getMessage(), e);
        }
    }
}
