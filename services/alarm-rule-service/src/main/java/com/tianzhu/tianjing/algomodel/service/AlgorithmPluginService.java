package com.tianzhu.tianjing.algomodel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianzhu.tianjing.algomodel.domain.AlgorithmPlugin;
import com.tianzhu.tianjing.algomodel.repository.AlgorithmPluginMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.exception.ErrorCode;
import com.tianzhu.tianjing.common.response.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * 算法插件管理服务
 * 规范：API 接口规范 V3.1 §6.5（3 个接口）
 */
@Service
@RequiredArgsConstructor
public class AlgorithmPluginService {
    private final AlgorithmPluginMapper pluginMapper;

    public PageResult<AlgorithmPlugin> listPlugins(int page, int size, String pluginType, String status) {
        Page<AlgorithmPlugin> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<AlgorithmPlugin> wrapper = new LambdaQueryWrapper<AlgorithmPlugin>()
                .eq(pluginType != null, AlgorithmPlugin::getPluginType, pluginType)
                .eq(status != null, AlgorithmPlugin::getStatus, status)
                .orderByDesc(AlgorithmPlugin::getCreatedAt);
        var result = pluginMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getTotal(), page, size, result.getRecords());
    }

    public AlgorithmPlugin getPlugin(String pluginId) {
        AlgorithmPlugin plugin = pluginMapper.selectOne(new LambdaQueryWrapper<AlgorithmPlugin>()
                .eq(AlgorithmPlugin::getPluginId, pluginId));
        if (plugin == null) throw BusinessException.notFound(ErrorCode.ALGO_PLUGIN_NOT_FOUND);
        return plugin;
    }

    @Transactional
    public AlgorithmPlugin registerPlugin(AlgorithmPlugin plugin, String operator) {
        plugin.setStatus("ACTIVE");
        plugin.setCreatedBy(operator);
        pluginMapper.insert(plugin);
        return plugin;
    }
}
