package com.tianzhu.tianjing.scene.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.scene.domain.SceneConfigHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface SceneConfigHistoryMapper extends BaseMapper<SceneConfigHistory> {

    @Select("SELECT MAX(history_version) FROM scene_config_history WHERE scene_id = #{sceneId}")
    Integer selectMaxVersion(String sceneId);

    @Select("""
        SELECT * FROM scene_config_history
        WHERE scene_id = #{sceneId}
          AND history_version = #{version}
        """)
    Optional<SceneConfigHistory> selectByVersion(String sceneId, int version);
}
