package com.tianzhu.tianjing.algomodel.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tianzhu.tianjing.algomodel.domain.AlgorithmPlugin;
import com.tianzhu.tianjing.algomodel.dto.AlgorithmPluginDetail;
import com.tianzhu.tianjing.algomodel.repository.AlgorithmPluginMapper;
import com.tianzhu.tianjing.common.exception.BusinessException;
import com.tianzhu.tianjing.common.response.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 算法插件管理服务单元测试
 *
 * 覆盖：CRUD 操作、插件类型过滤、状态过滤、插件不存在异常
 * 规范：CLAUDE.md §12.1（Service 层覆盖率 ≥ 80%）
 * 测试计划：天柱天镜_测试推进计划 §3.1 M1 修复期高优先级
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlgorithmPluginService — CRUD + 过滤查询")
class AlarmRuleServiceTest {

    @Mock
    private AlgorithmPluginMapper pluginMapper;

    @InjectMocks
    private AlgorithmPluginService algorithmPluginService;

    private AlgorithmPlugin detectPlugin;
    private AlgorithmPlugin segmentPlugin;

    @BeforeEach
    void setUp() {
        detectPlugin = new AlgorithmPlugin();
        detectPlugin.setPluginId("ATOM-DETECT-YOLO-V1");
        detectPlugin.setPluginName("通用目标检测引擎");
        detectPlugin.setPluginType("detection");
        detectPlugin.setVersion("1.2.0");
        detectPlugin.setStatus("ACTIVE");
        detectPlugin.setCreatedBy("admin");

        segmentPlugin = new AlgorithmPlugin();
        segmentPlugin.setPluginId("ATOM-SEGMENT-SAM-V1");
        segmentPlugin.setPluginName("通用语义分割算法");
        segmentPlugin.setPluginType("segmentation");
        segmentPlugin.setVersion("1.0.0");
        segmentPlugin.setStatus("ACTIVE");
        segmentPlugin.setCreatedBy("admin");
    }

    // ── 分页查询 ─────────────────────────────────────────────

    @Test
    @DisplayName("listPlugins：无过滤条件，返回全量分页结果")
    void listPlugins_noFilter_returnsAllPlugins() {
        Page<AlgorithmPlugin> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(detectPlugin, segmentPlugin));
        mockPage.setTotal(2);
        when(pluginMapper.selectPage(any(), any())).thenReturn(mockPage);

        PageResult<AlgorithmPluginDetail> result = algorithmPluginService.listPlugins(1, 10, null, null);

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getItems()).hasSize(2);
        verify(pluginMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listPlugins：按 pluginType=detection 过滤，selectPage 被正确调用")
    void listPlugins_withTypeFilter_callsSelectPage() {
        Page<AlgorithmPlugin> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(detectPlugin));
        mockPage.setTotal(1);
        when(pluginMapper.selectPage(any(), any())).thenReturn(mockPage);

        PageResult<AlgorithmPluginDetail> result = algorithmPluginService.listPlugins(1, 10, "detection", null);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).pluginType()).isEqualTo("detection");
    }

    @Test
    @DisplayName("listPlugins：按 status=ACTIVE 过滤，selectPage 被调用")
    void listPlugins_withStatusFilter_callsSelectPage() {
        Page<AlgorithmPlugin> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(detectPlugin, segmentPlugin));
        mockPage.setTotal(2);
        when(pluginMapper.selectPage(any(), any())).thenReturn(mockPage);

        PageResult<AlgorithmPluginDetail> result = algorithmPluginService.listPlugins(1, 10, null, "ACTIVE");

        assertThat(result.getTotal()).isEqualTo(2);
        verify(pluginMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listPlugins：空结果集，返回 total=0 的 PageResult")
    void listPlugins_emptyResult_returnsEmptyPageResult() {
        Page<AlgorithmPlugin> emptyPage = new Page<>(1, 10);
        emptyPage.setRecords(List.of());
        emptyPage.setTotal(0);
        when(pluginMapper.selectPage(any(), any())).thenReturn(emptyPage);

        PageResult<AlgorithmPluginDetail> result = algorithmPluginService.listPlugins(1, 10, "measurement", null);

        assertThat(result.getTotal()).isEqualTo(0);
        assertThat(result.getItems()).isEmpty();
    }

    // ── 单插件查询 ────────────────────────────────────────────

    @Test
    @DisplayName("getPlugin：插件存在，返回正确实体")
    void getPlugin_found_returnsPlugin() {
        when(pluginMapper.selectOne(any())).thenReturn(detectPlugin);

        AlgorithmPluginDetail result = algorithmPluginService.getPlugin("ATOM-DETECT-YOLO-V1");

        assertThat(result.pluginId()).isEqualTo("ATOM-DETECT-YOLO-V1");
        assertThat(result.pluginType()).isEqualTo("detection");
    }

    @Test
    @DisplayName("getPlugin：插件不存在 → 抛 BusinessException（资源未找到）")
    void getPlugin_notFound_throwsBusinessException() {
        when(pluginMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> algorithmPluginService.getPlugin("ATOM-NONEXIST-V1"))
                .isInstanceOf(BusinessException.class);
        verify(pluginMapper).selectOne(any());
    }

    // ── 注册插件 ─────────────────────────────────────────────

    @Test
    @DisplayName("registerPlugin：注册后状态设为 ACTIVE，createdBy 正确赋值")
    void registerPlugin_setsStatusActiveAndCreatedBy() {
        AlgorithmPlugin newPlugin = new AlgorithmPlugin();
        newPlugin.setPluginId("ATOM-CLASSIFY-RESNET-V1");
        newPlugin.setPluginName("通用图像分类器");
        newPlugin.setPluginType("classification");

        AlgorithmPlugin result = algorithmPluginService.registerPlugin(newPlugin, "alice");

        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getCreatedBy()).isEqualTo("alice");
        verify(pluginMapper).insert(newPlugin);
    }

    @Test
    @DisplayName("registerPlugin：mapper.insert 被调用一次")
    void registerPlugin_callsInsertOnce() {
        AlgorithmPlugin newPlugin = new AlgorithmPlugin();
        newPlugin.setPluginId("ATOM-MEASURE-SUBPIXEL-V1");
        newPlugin.setPluginType("measurement");

        algorithmPluginService.registerPlugin(newPlugin, "bob");

        ArgumentCaptor<AlgorithmPlugin> captor = ArgumentCaptor.forClass(AlgorithmPlugin.class);
        verify(pluginMapper, times(1)).insert(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("bob");
    }
}
