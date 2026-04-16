package com.tianzhu.tianjing.drift.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.drift.domain.DataSyncAudit;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface DataSyncAuditMapper extends BaseMapper<DataSyncAudit> {}
