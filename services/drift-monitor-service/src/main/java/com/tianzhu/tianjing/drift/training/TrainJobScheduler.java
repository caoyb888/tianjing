package com.tianzhu.tianjing.drift.training;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tianzhu.tianjing.drift.domain.TrainJob;
import com.tianzhu.tianjing.drift.repository.TrainJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 训练作业超时扫描器
 *
 * 防止训练容器异常崩溃后，job 状态永远停在 RUNNING 或 PENDING。
 * 超过 2 小时未回调的作业自动标记为 FAILED。
 *
 * docker run 和未来的 K8s Job 方案均适用此扫描逻辑。
 * @EnableScheduling 已在 DriftMonitorServiceApplication 开启。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrainJobScheduler {

    /** 超过此时间（小时）无回调的 RUNNING/PENDING 作业视为僵死 */
    private static final int STALE_HOURS = 2;

    private final TrainJobMapper trainJobMapper;

    /**
     * 每 30 秒扫描一次僵死的训练作业。
     * fixedDelay 保证上次扫描结束后再等 30 秒，避免并发扫描。
     */
    @Scheduled(fixedDelay = 30_000)
    public void scanStaleJobs() {
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(STALE_HOURS);

        List<TrainJob> staleJobs = trainJobMapper.selectList(
                new LambdaQueryWrapper<TrainJob>()
                        .in(TrainJob::getStatus, "RUNNING", "PENDING")
                        .lt(TrainJob::getCreatedAt, cutoff));

        if (staleJobs.isEmpty()) return;

        for (TrainJob job : staleJobs) {
            job.setStatus("FAILED");
            job.setFinishedAt(OffsetDateTime.now());
            job.setErrorMsg("训练超时：超过 " + STALE_HOURS + " 小时未回调，系统自动标记为失败");
            trainJobMapper.updateById(job);
            log.warn("训练作业超时自动置为失败 job_id={} created_at={}",
                    job.getJobId(), job.getCreatedAt());
        }
    }
}
