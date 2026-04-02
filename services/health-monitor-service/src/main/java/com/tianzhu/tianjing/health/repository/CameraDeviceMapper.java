package com.tianzhu.tianjing.health.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * health-monitor-service 对 camera_device 的只读视图
 * 与 device-manage-service 共享同一 tianjing_prod 库，只做查询
 */
@Mapper
public interface CameraDeviceMapper extends BaseMapper<com.tianzhu.tianjing.health.domain.CameraDeviceView> {

    /** 获取摄像头列表，JOIN scene_config 取 factory_code */
    @Select("""
            SELECT d.device_code, d.scene_id, d.health_status, d.health_score,
                   s.factory_code
            FROM camera_device d
            LEFT JOIN scene_config s ON d.scene_id = s.scene_id
            WHERE d.is_deleted = false
            ORDER BY d.health_status, d.device_code
            LIMIT #{size} OFFSET #{offset}
            """)
    List<Map<String, Object>> selectCameraList(int size, int offset);

    @Select("""
            SELECT COUNT(*) FROM camera_device WHERE is_deleted = false
            """)
    int countTotal();

    @Select("""
            SELECT COUNT(*) FROM camera_device WHERE is_deleted = false AND health_status = #{status}
            """)
    int countByStatus(String status);

    @Select("""
            SELECT AVG(health_score) FROM camera_device WHERE is_deleted = false AND health_score IS NOT NULL
            """)
    Double avgHealthScore();

    /** 最近 N 条健康历史记录（camera_health_record 分区表） */
    @Select("""
            SELECT check_at, health_score, fault_type, fault_desc
            FROM camera_health_record
            WHERE device_code = #{deviceCode}
            ORDER BY check_at DESC
            LIMIT 50
            """)
    List<Map<String, Object>> selectHealthHistory(String deviceCode);
}
