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
     * TDengine tag 名：factory_code（非 factory）；BOOL tag 过滤用 0/1
     */
    private long queryTodayInferences(String factory, String sceneId) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT COUNT(*) FROM infer_result " +
                    "WHERE ts >= TODAY() AND is_sandbox = 0"
            );

            if (factory != null && !factory.isEmpty()) {
                sql.append(" AND factory_code = '").append(escapeSql(factory)).append("'");
            }
            if (sceneId != null && !sceneId.isEmpty()) {
                sql.append(" AND scene_id = '").append(escapeSql(sceneId)).append("'");
            }

            // TDengine COUNT(*) 在无匹配数据时返回空结果集而非0行，用 query 替代 queryForObject
            var rows = tdengineJdbcTemplate.queryForList(sql.toString(), Long.class);
            return rows.isEmpty() ? 0L : (rows.get(0) != null ? rows.get(0) : 0L);
        } catch (Exception e) {
            log.warn("TDengine 查询今日推理量失败: {}", e.getMessage());
            return 0L;
        }
    }

    /**
     * 查询平均推理延迟（TDengine，毫秒）
     * TDengine AVG 在无数据时返回空结果集，用 queryForList 安全处理
     */
    private double queryAvgInferenceLatency(String factory, String sceneId) {
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT AVG(infer_ms) FROM infer_result " +
                    "WHERE ts >= TODAY() AND is_sandbox = 0"
            );

            if (factory != null && !factory.isEmpty()) {
                sql.append(" AND factory_code = '").append(escapeSql(factory)).append("'");
            }
            if (sceneId != null && !sceneId.isEmpty()) {
                sql.append(" AND scene_id = '").append(escapeSql(sceneId)).append("'");
            }

            var rows = tdengineJdbcTemplate.queryForList(sql.toString(), Double.class);
            return rows.isEmpty() ? 0.0 : (rows.get(0) != null ? rows.get(0) : 0.0);
        } catch (Exception e) {
            log.warn("TDengine 查询平均延迟失败: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * 查询单日推理统计（TDengine）
     * 修复：'date' + 1d 语法在 TDengine JDBC 中不生效，改为 Java 计算次日日期字符串
     */
    private TrendPointDTO queryDailyStats(String dateStr, String factory, String sceneId) {
        try {
            // 次日日期（用于闭区间上界），避免 TDengine 不支持 + 1d 字面量运算
            String nextDateStr = java.time.LocalDate.parse(dateStr)
                    .plusDays(1).toString();

            StringBuilder countSql = new StringBuilder(
                    "SELECT COUNT(*) FROM infer_result " +
                    "WHERE ts >= '" + dateStr + "' AND ts < '" + nextDateStr + "' AND is_sandbox = 0"
            );

            // infer_result 无 is_anomaly 列，使用 anomaly_count > 0 替代
            StringBuilder alarmSql = new StringBuilder(
                    "SELECT COUNT(*) FROM infer_result " +
                    "WHERE ts >= '" + dateStr + "' AND ts < '" + nextDateStr + "' AND is_sandbox = 0 AND anomaly_count > 0"
            );

            StringBuilder latencySql = new StringBuilder(
                    "SELECT AVG(infer_ms) FROM infer_result " +
                    "WHERE ts >= '" + dateStr + "' AND ts < '" + nextDateStr + "' AND is_sandbox = 0"
            );

            if (factory != null && !factory.isEmpty()) {
                String f = escapeSql(factory);
                countSql.append(" AND factory_code = '").append(f).append("'");
                alarmSql.append(" AND factory_code = '").append(f).append("'");
                latencySql.append(" AND factory_code = '").append(f).append("'");
            }
            if (sceneId != null && !sceneId.isEmpty()) {
                String s = escapeSql(sceneId);
                countSql.append(" AND scene_id = '").append(s).append("'");
                alarmSql.append(" AND scene_id = '").append(s).append("'");
                latencySql.append(" AND scene_id = '").append(s).append("'");
            }

            // TDengine 聚合查询无数据时返回空结果集，用 queryForList 安全处理
            var cntRows = tdengineJdbcTemplate.queryForList(countSql.toString(), Long.class);
            var almRows = tdengineJdbcTemplate.queryForList(alarmSql.toString(), Long.class);
            var latRows = tdengineJdbcTemplate.queryForList(latencySql.toString(), Double.class);

            long count    = cntRows.isEmpty() ? 0L   : (cntRows.get(0) != null ? cntRows.get(0) : 0L);
            long alarms   = almRows.isEmpty() ? 0L   : (almRows.get(0) != null ? almRows.get(0) : 0L);
            double avgLat = latRows.isEmpty() ? 0.0  : (latRows.get(0) != null ? latRows.get(0) : 0.0);

            return new TrendPointDTO(dateStr, count, alarms, avgLat);
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
