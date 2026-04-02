package com.tianzhu.tianjing.drift.repository;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.drift.domain.Dataset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** dataset 表位于 tianjing_train 库，通过 @DS 路由到 train 数据源 */
@DS("train")
@Mapper
public interface DatasetMapper extends BaseMapper<Dataset> {

    @Select("SELECT version_tag FROM dataset_version WHERE dataset_code = #{datasetCode} AND is_deleted = false ORDER BY created_at DESC")
    List<String> selectVersionTags(String datasetCode);
}
