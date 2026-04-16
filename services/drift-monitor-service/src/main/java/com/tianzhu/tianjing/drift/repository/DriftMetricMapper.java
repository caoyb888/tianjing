package com.tianzhu.tianjing.drift.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.drift.domain.DriftMetric;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.time.LocalDate;
import java.util.List;
@Mapper
public interface DriftMetricMapper extends BaseMapper<DriftMetric> {
    @Select("SELECT * FROM drift_metric WHERE scene_id = #{sceneId} AND metric_date >= #{fromDate} ORDER BY metric_date ASC")
    List<DriftMetric> selectBySceneAndDateRange(String sceneId, LocalDate fromDate);
}
