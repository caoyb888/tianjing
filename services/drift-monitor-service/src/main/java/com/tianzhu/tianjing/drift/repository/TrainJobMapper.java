package com.tianzhu.tianjing.drift.repository;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.drift.domain.TrainJob;
import org.apache.ibatis.annotations.Mapper;

/** train_job 表位于 tianjing_train 库，通过 @DS 路由到 train 数据源 */
@DS("train")
@Mapper
public interface TrainJobMapper extends BaseMapper<TrainJob> {}
