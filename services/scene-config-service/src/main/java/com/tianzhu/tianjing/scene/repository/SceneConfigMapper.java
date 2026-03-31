package com.tianzhu.tianjing.scene.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianzhu.tianjing.scene.domain.SceneConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SceneConfigMapper extends BaseMapper<SceneConfig> {

    @Select("""
        SELECT s.* FROM scene_config s
        WHERE s.is_deleted = 0
          AND (#{factoryCode} IS NULL OR s.factory_code = #{factoryCode})
          AND (#{category} IS NULL OR s.category = #{category})
          AND (#{status} IS NULL OR s.status = #{status})
          AND (#{priority} IS NULL OR s.priority = #{priority})
          AND (#{keyword} IS NULL OR s.scene_name LIKE CONCAT('%', #{keyword}, '%'))
        ORDER BY s.created_at DESC
        """)
    IPage<SceneConfig> selectPageWithFilters(
            Page<SceneConfig> page,
            @Param("factoryCode") String factoryCode,
            @Param("category") String category,
            @Param("status") String status,
            @Param("priority") String priority,
            @Param("keyword") String keyword
    );
}
