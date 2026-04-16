package com.tianzhu.tianjing.algomodel.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.algomodel.domain.ModelVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.Optional;
@Mapper
public interface ModelVersionMapper extends BaseMapper<ModelVersion> {
    @Select("SELECT * FROM model_version WHERE plugin_id = #{pluginId} AND status = 'PRODUCTION' LIMIT 1")
    Optional<ModelVersion> selectCurrentProduction(String pluginId);
}
