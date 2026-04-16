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
        WHERE s.is_deleted = false
          AND (#{factoryCode,jdbcType=VARCHAR} IS NULL OR s.factory_code = #{factoryCode,jdbcType=VARCHAR})
          AND (#{category,jdbcType=VARCHAR} IS NULL OR s.category = #{category,jdbcType=VARCHAR})
          AND (#{status,jdbcType=VARCHAR} IS NULL OR s.status = #{status,jdbcType=VARCHAR})
          AND (#{priority,jdbcType=VARCHAR} IS NULL OR s.priority = #{priority,jdbcType=VARCHAR})
          AND (#{keyword,jdbcType=VARCHAR} IS NULL OR s.scene_name LIKE CONCAT('%', #{keyword,jdbcType=VARCHAR}, '%'))
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
