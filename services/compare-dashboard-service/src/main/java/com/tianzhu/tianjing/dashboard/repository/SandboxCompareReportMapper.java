package com.tianzhu.tianjing.dashboard.repository;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.dashboard.domain.SandboxCompareReport;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import java.util.Optional;
@Mapper
public interface SandboxCompareReportMapper extends BaseMapper<SandboxCompareReport> {
    @Select("SELECT * FROM sandbox_compare_report WHERE session_id = #{sessionId} ORDER BY created_at DESC LIMIT 1")
    Optional<SandboxCompareReport> selectLatestBySession(String sessionId);
}
