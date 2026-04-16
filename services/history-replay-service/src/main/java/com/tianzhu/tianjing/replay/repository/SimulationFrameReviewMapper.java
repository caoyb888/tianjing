package com.tianzhu.tianjing.replay.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.replay.domain.entity.SimulationFrameReviewEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 帧级标注审核记录 Mapper
 * 规范：标注审核工具开发计划 V1.0
 */
@Mapper
public interface SimulationFrameReviewMapper extends BaseMapper<SimulationFrameReviewEntity> {

    /**
     * 根据任务ID查询所有审核记录（按帧序号排序）
     */
    @Select("SELECT * FROM simulation_frame_review " +
            "WHERE task_id = #{taskId} AND is_deleted = false " +
            "ORDER BY frame_index")
    List<SimulationFrameReviewEntity> selectByTaskId(@Param("taskId") String taskId);

    /**
     * 根据任务ID和帧ID查询单条记录
     */
    @Select("SELECT * FROM simulation_frame_review " +
            "WHERE task_id = #{taskId} AND frame_id = #{frameId} AND is_deleted = false")
    SimulationFrameReviewEntity selectByTaskIdAndFrameId(@Param("taskId") String taskId,
                                                          @Param("frameId") String frameId);

    /**
     * 统计指定任务的各状态数量
     */
    @Select("SELECT review_status, COUNT(*) as cnt FROM simulation_frame_review " +
            "WHERE task_id = #{taskId} AND is_deleted = false " +
            "GROUP BY review_status")
    List<StatusCount> countByTaskIdGroupByStatus(@Param("taskId") String taskId);

    /**
     * 统计指定任务指定状态的记录数
     */
    @Select("SELECT COUNT(*) FROM simulation_frame_review " +
            "WHERE task_id = #{taskId} AND review_status = #{status} AND is_deleted = false")
    Long countByTaskIdAndStatus(@Param("taskId") String taskId, @Param("status") String status);

    /**
     * 状态统计内部类
     */
    class StatusCount {
        private String reviewStatus;
        private Long cnt;

        public String getReviewStatus() { return reviewStatus; }
        public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
        public Long getCnt() { return cnt; }
        public void setCnt(Long cnt) { this.cnt = cnt; }
    }
}
