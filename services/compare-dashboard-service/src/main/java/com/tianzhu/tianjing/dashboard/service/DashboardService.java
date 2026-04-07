package com.tianzhu.tianjing.dashboard.service;

import com.tianzhu.tianjing.dashboard.dto.FactorySummaryDTO;
import com.tianzhu.tianjing.dashboard.dto.OverviewStatsDTO;
import com.tianzhu.tianjing.dashboard.dto.TrendPointDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据看板业务服务
 * 规范：实时告警大屏开发优化计划.md Phase 1
 * 负责聚合 PostgreSQL（配置/告警）和 TDengine（时序/推理）数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    @Qualifier("prodJdbcTemplate")
    private final JdbcTemplate prodJdbcTemplate;

    @Qualifier("tdengineJdbcTemplate")
    private final JdbcTemplate tdengineJdbcTemplate;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ============================== 厂部中文名映射 ==============================
    private static final String getFactoryName(String factoryCode) {
        return switch (factoryCode) {
            case "PELLET" -> "球团厂";
            case "SINTER" -> "烧结厂";
            case "STEEL" -> "炼钢厂";
            case "SECTION" -> "型钢厂";
            case "STRIP" -> "带钢厂";
            default -> factoryCode;
        };
    }

    // ============================== Overview 概览统计 ==============================

    /**
     * 获取平台概览统计（支持厂部和场景筛选）
     *
     * @param factory 厂部编码（可选）PELLET/SINTER/STEEL/SECTION/STRIP
     * @param sceneId 场景ID（可选）
     * @return 概览统计数据
     */
    public OverviewStatsDTO getOverview(String factory, String sceneId) {
        // 构建 WHERE 条件
        StringBuilder whereClause = new StringBuilder(" WHERE is_deleted = false");
        StringBuilder alarmWhere = new StringBuilder(" WHERE is_sandbox = false AND created_at >= CURRENT_DATE");

        if (factory != null && !factory.isEmpty()) {
            whereClause.append(" AND factory_code = '").append(escapeSql(factory)).append("'");
            alarmWhere.append(" AND factory_code = '").append(escapeSql(factory)).append("'");
        }
        if (sceneId != null && !sceneId.isEmpty()) {
            whereClause.append(" AND scene_id = '").append(escapeSql(sceneId)).append("'");
            alarmWhere.append(" AND scene_id = '").append(escapeSql(sceneId)).append("'");
        }

        // 查询活跃场景数
        int activeScenes = queryCount("SELECT COUNT(*) FROM scene_config" + whereClause + " AND status = 'ACTIVE'");

        // 查询在线设备数
        int onlineDevices = queryCount(
                "SELECT COUNT(*) FROM camera_device cd " +
                "JOIN scene_config sc ON cd.scene_id = sc.scene_id " +
                "WHERE cd.health_status = 'HEALTHY'" +
                (factory != null ? " AND sc.factory_code = '" + escapeSql(factory) + "'" : "") +
                (sceneId != null ? " AND sc.scene_id = '" + escapeSql(sceneId) + "'" : "")
        );

        // 查询今日告警数
        int todayAlarms = queryCount("SELECT COUNT(*) FROM alarm_record" + alarmWhere);

        // 查询今日推理量（从 TDengine）
        long todayInferences = queryTodayInferences(factory, sceneId);

        // 告警级别分布
        int criticalAlarms = queryCount(alarmWhere + " AND alarm_level = 'CRITICAL'");
        int warningAlarms = queryCount(alarmWhere + " AND alarm_level = 'WARNING'");
        int infoAlarms = queryCount(alarmWhere + " AND alarm_level = 'INFO'");

        // 总场景数
        int totalScenes = queryCount("SELECT COUNT(*) FROM scene_config" + whereClause);

        // 平均推理延迟（从 TDengine）
        double avgLatency = queryAvgInferenceLatency(factory, sceneId);

        return new OverviewStatsDTO(
                activeScenes,
                onlineDevices,
                todayAlarms,
                todayInferences,
                criticalAlarms,
                warningAlarms,
                infoAlarms,
                totalScenes,
                avgLatency,
                0, // sandbox_sessions_running
                OffsetDateTime.now()
        );
    }

    /**
     * 获取各厂部汇总统计（热力柱图）
     * 返回5个厂部的今日告警、活跃场景、在线设备、今日推理量
     */
    public List<FactorySummaryDTO> getFactorySummary() {
        List<FactorySummaryDTO> result = new ArrayList<>();
        String[] factories = {"PELLET", "SINTER", "STEEL", "SECTION", "STRIP"};

        for (String factory : factories) {
            // 今日告警数
            int todayAlarms = queryCount(
                    "SELECT COUNT(*) FROM alarm_record " +
                    "WHERE is_sandbox = false AND created_at >= CURRENT_DATE " +
                    "AND factory_code = '" + escapeSql(factory) + "'"
            );

            // 活跃场景数
            int activeScenes = queryCount(
                    "SELECT COUNT(*) FROM scene_config " +
                    "WHERE is_deleted = false AND status = 'ACTIVE' " +
                    "AND factory_code = '" + escapeSql(factory) + "'"
            );

            // 在线设备数
            int onlineDevices = queryCount(
                    "SELECT COUNT(*) FROM camera_device cd " +
                    "JOIN scene_config sc ON cd.scene_id = sc.scene_id " +
                    "WHERE cd.health_status = 'HEALTHY' " +
                    "AND sc.factory_code = '" + escapeSql(factory) + "'"
            );

            // 今日推理量（TDengine）
            long todayInferences = queryTodayInferences(factory, null);

            result.add(new FactorySummaryDTO(
                    factory,
                    getFactoryName(factory),
                    todayAlarms,
                    activeScenes,
                    onlineDevices,
                    todayInferences
            ));
        }

        // 按今日告警数降序排序
        result.sort((a, b) -> b.todayAlarms().compareTo(a.todayAlarms()));
        return result;
    }

    /**
     * 获取推理趋势（近N天）
     *
     * @param days    天数（默认7天）
     * @param factory 厂部编码（可选）
     * @param sceneId 场景ID（可选）
     * @return 趋势数据点列表
     */
    public List<TrendPointDTO> getInferenceTrend(int days, String factory, String sceneId) {
        List<TrendPointDTO> trend = new ArrayList<>();

        for (int i = days - 1; i >= 0; i--) {
            OffsetDateTime day = OffsetDateTime.now().minusDays(i);
            String dateStr = day.format(DATE_FORMATTER);

            // 从 TDengine 查询该日的推理统计
            // 注意：TDengine 表结构需包含 factory 和 scene_id 标签
            TrendPointDTO point = queryDailyStats(dateStr, factory, sceneId);
            trend.add(point);
        }

        return trend;
    }

    // ============================== 私有查询方法 ==============================

    /**
     * 查询今日推理量（TDengine）
     * 表结构：infer_result(ts, confidence, class_name, ..., tags: scene_id, factory, is_sandbox)
     */
    private long queryTodayInferences(String factory, String sceneId) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT COUNT(*) FROM infer_result " +
                    "WHERE ts >= TODAY() AND is_sandbox = false"
            );

            if (factory != null && !factory.isEmpty()) {
                sql.append(" AND factory = '").append(escapeSql(factory)).append("'");
            }
            if (sceneId != null && !sceneId.isEmpty()) {
                sql.append(" AND scene_id = '").append(escapeSql(sceneId)).append("'");
            }

            Long count = tdengineJdbcTemplate.queryForObject(sql.toString(), Long.class);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("TDengine 查询今日推理量失败: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 查询平均推理延迟（TDengine，毫秒）
     */
    private double queryAvgInferenceLatency(String factory, String sceneId) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT AVG(infer_ms) FROM infer_result " +
                    "WHERE ts >= TODAY() AND is_sandbox = false"
            );

            if (factory != null && !factory.isEmpty()) {
                sql.append(" AND factory = '").append(escapeSql(factory)).append("'");
            }
            if (sceneId != null && !sceneId.isEmpty()) {
                sql.append(" AND scene_id = '").append(escapeSql(sceneId)).append("'");
            }

            Double avg = tdengineJdbcTemplate.queryForObject(sql.toString(), Double.class);
            return avg != null ? avg : 0.0;
        } catch (Exception e) {
            log.warn("TDengine 查询平均延迟失败: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 查询单日推理统计（TDengine）
     */
    private TrendPointDTO queryDailyStats(String dateStr, String factory, String sceneId) {
        try {
            // 查询该日总推理次数
            StringBuilder countSql = new StringBuilder(
                    "SELECT COUNT(*) FROM infer_result " +
                    "WHERE ts >= '" + dateStr + "' AND ts < '" + dateStr + "' + 1d " +
                    "AND is_sandbox = false"
            );

            // 查询该日异常数（is_anomaly = true）
            StringBuilder alarmSql = new StringBuilder(
                    "SELECT COUNT(*) FROM infer_result " +
                    "WHERE ts >= '" + dateStr + "' AND ts < '" + dateStr + "' + 1d " +
                    "AND is_sandbox = false AND is_anomaly = true"
            );

            // 查询平均延迟
            StringBuilder latencySql = new StringBuilder(
                    "SELECT AVG(infer_ms) FROM infer_result " +
                    "WHERE ts >= '" + dateStr + "' AND ts < '" + dateStr + "' + 1d " +
                    "AND is_sandbox = false"
            );

            if (factory != null && !factory.isEmpty()) {
                countSql.append(" AND factory = '").append(escapeSql(factory)).append("'");
                alarmSql.append(" AND factory = '").append(escapeSql(factory)).append("'");
                latencySql.append(" AND factory = '").append(escapeSql(factory)).append("'");
            }
            if (sceneId != null && !sceneId.isEmpty()) {
                countSql.append(" AND scene_id = '").append(escapeSql(sceneId)).append("'");
                alarmSql.append(" AND scene_id = '").append(escapeSql(sceneId)).append("'");
                latencySql.append(" AND scene_id = '").append(escapeSql(sceneId)).append("'");
            }

            Long count = tdengineJdbcTemplate.queryForObject(countSql.toString(), Long.class);
            Long alarms = tdengineJdbcTemplate.queryForObject(alarmSql.toString(), Long.class);
            Double avgLatency = tdengineJdbcTemplate.queryForObject(latencySql.toString(), Double.class);

            return new TrendPointDTO(
                    dateStr,
                    count != null ? count : 0L,
                    alarms != null ? alarms : 0L,
                    avgLatency != null ? avgLatency : 0.0
            );
        } catch (Exception e) {
            log.warn("TDengine 查询每日统计失败 date={}: {}", dateStr, e.getMessage());
            return new TrendPointDTO(dateStr, 0L, 0L, 0.0);
        }
    }

    /**
     * 安全查询 PostgreSQL COUNT
     */
    private int queryCount(String sql) {
        try {
            Integer result = prodJdbcTemplate.queryForObject(sql, Integer.class);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.warn("Dashboard 统计查询失败 sql={} error={}", sql, e.getMessage());
            return 0;
        }
    }

    /**
     * 简单的 SQL 转义（防止基础注入）
     * 生产环境建议使用参数化查询
     */
    private String escapeSql(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("'", "''").replace(";", "").replace("--", "");
    }
}
