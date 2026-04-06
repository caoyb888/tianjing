package com.tianzhu.tianjing.replay.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.replay.domain.SimulationVideo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SimulationVideoMapper extends BaseMapper<SimulationVideo> {

    @Select("SELECT * FROM simulation_video WHERE task_id = #{taskId} ORDER BY sort_order ASC")
    List<SimulationVideo> selectByTaskId(String taskId);

    @Select("SELECT COUNT(*) FROM simulation_video WHERE task_id = #{taskId}")
    int countByTaskId(String taskId);
}
