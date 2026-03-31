package com.tianzhu.tianjing.calibration.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.calibration.domain.CalibrationRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CalibrationMapper extends BaseMapper<CalibrationRecord> {}
