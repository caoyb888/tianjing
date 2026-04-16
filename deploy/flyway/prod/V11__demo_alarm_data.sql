-- ============================================================
-- Flyway 迁移脚本 V11
-- 数据库：tianjing_prod（生产库）
-- 说明：演示用告警数据 — 覆盖近 7 天（CURRENT_DATE 当天及前 6 天，滚动写法不过期）
--       共 6 个场景、3 种级别、约 93 条告警记录
--       同时修复 alarm_record 缺少 created_at 列的 Entity 字段不匹配问题
--       参考：CLAUDE.md §15 P2 规则 11（Flyway Migration Entity 同步规范）
-- 编制：测试负责人 · 2026-04-08
-- ============================================================

-- ============================================================
-- PART 0：补充 alarm_record 缺失的 created_at 列
-- Entity 中 @TableField(value = "created_at", fill = FieldFill.INSERT)
-- DashboardService 也依赖此列过滤当日告警，表中原先仅有 alarm_at
-- ============================================================
ALTER TABLE alarm_record ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
UPDATE alarm_record SET created_at = alarm_at WHERE created_at IS NULL;
ALTER TABLE alarm_record ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE alarm_record ALTER COLUMN created_at SET DEFAULT NOW();

-- ============================================================
-- PART 1：演示用告警记录（DO 块批量生成，7 天 × 多场景）
-- 场景覆盖：6 个生产场景，跨 5 个厂部
-- 级别分布：CRITICAL ≈ 20%，WARNING ≈ 45%，INFO ≈ 35%
-- 幂等处理：alarm_record 是分区表，不支持 ON CONFLICT，
--           改为插入前先删除 seq ≥ 1001 的旧演示数据（V11 专属序号段）
-- ============================================================
DELETE FROM alarm_record
WHERE alarm_id ~ '^ALM-[0-9]{8}-[0-9]{6}$'
  AND SUBSTRING(alarm_id FROM 14)::INTEGER >= 1001;
DO $$
DECLARE
    -- 场景变量（独立声明，避免 PL/pgSQL 2D 数组切片限制）
    scene_id_val      TEXT;
    factory_code_val  TEXT;
    plugin_id_val     TEXT;
    model_ver_val     TEXT;
    anomaly_a_val     TEXT;  -- 异常类型 A
    anomaly_b_val     TEXT;  -- 异常类型 B

    lv        TEXT;
    conf      DECIMAL(5,4);
    conf_frm  SMALLINT;
    anomaly   TEXT;
    push_st   TEXT;
    alarm_ts  TIMESTAMPTZ;
    seq       INT := 1001;
    date_idx  INT;
    scene_idx INT;
    hour_off  INT;
    min_off   INT;

    -- 每天每场景产生告警数 [date_idx 1..7][scene_idx 1..6]
    -- date_idx=1 → CURRENT_DATE-6（6天前），date_idx=7 → CURRENT_DATE（今天）
    count_matrix INT[][] := ARRAY[
        ARRAY[1, 2, 1, 2, 2, 2],   -- 第1天（今天-6）：10 条
        ARRAY[2, 1, 2, 2, 2, 2],   -- 第2天（今天-5）：11 条
        ARRAY[2, 2, 2, 2, 2, 2],   -- 第3天（今天-4）：12 条
        ARRAY[2, 2, 2, 3, 2, 2],   -- 第4天（今天-3）：13 条
        ARRAY[3, 2, 3, 3, 2, 2],   -- 第5天（今天-2）：15 条
        ARRAY[3, 4, 3, 3, 3, 2],   -- 第6天（今天-1）：18 条
        ARRAY[2, 3, 2, 3, 2, 2]    -- 第7天（今天）  ：14 条
    ];

    -- 告警级别轮转（7元素循环，约 2/7 CRITICAL，3/7 WARNING，2/7 INFO）
    levels TEXT[] := ARRAY['CRITICAL', 'WARNING', 'WARNING', 'WARNING', 'INFO', 'INFO', 'INFO'];

    n   INT;
    r   INT;
    day_base TIMESTAMPTZ;
BEGIN
    FOR date_idx IN 1..7 LOOP
        -- 使用 CURRENT_DATE 滚动偏移：date_idx=1 → 今天-6，date_idx=7 → 今天
        -- DATE_TRUNC 截到 UTC 日零点，避免 +08 凌晨时间转换后落入前一天分区
        day_base := DATE_TRUNC('day', NOW() AT TIME ZONE 'UTC') - ((7 - date_idx) * INTERVAL '1 day');

        FOR scene_idx IN 1..6 LOOP
            -- 按 scene_idx 设置场景参数（替代 2D 数组切片）
            CASE scene_idx
                WHEN 1 THEN
                    scene_id_val     := 'SCENE-PELLET-001';
                    factory_code_val := 'PELLET';
                    plugin_id_val    := 'HEAD-SIDEPLATE-V1';
                    model_ver_val    := 'MV-SIDEPLATE-20260322-001';
                    anomaly_a_val    := '侧板跑偏';
                    anomaly_b_val    := '侧板变形';
                WHEN 2 THEN
                    scene_id_val     := 'SCENE-SINTER-001';
                    factory_code_val := 'SINTER';
                    plugin_id_val    := 'HEAD-SINTER-GRATE-V1';
                    model_ver_val    := 'MV-WALLBAR-20260322-001';
                    anomaly_a_val    := '壁条脱落';
                    anomaly_b_val    := '壁条缺损';
                WHEN 3 THEN
                    scene_id_val     := 'SCENE-SINTER-002';
                    factory_code_val := 'SINTER';
                    plugin_id_val    := 'ATOM-DETECT-YOLO-V1';
                    model_ver_val    := 'MV-WALLBAR-20260322-001';
                    anomaly_a_val    := '篦条断裂';
                    anomaly_b_val    := '篦条缺损';
                WHEN 4 THEN
                    scene_id_val     := 'SCENE-STEEL-001';
                    factory_code_val := 'STEEL';
                    plugin_id_val    := 'HEAD-BILLET-CRACK-V1';
                    model_ver_val    := 'MV-BILLET-CRACK-20260320-001';
                    anomaly_a_val    := '铸坯纵裂';
                    anomaly_b_val    := '铸坯横裂';
                WHEN 5 THEN
                    scene_id_val     := 'SCENE-SECTION-001';
                    factory_code_val := 'SECTION';
                    plugin_id_val    := 'HEAD-STEEL-SURFACE-V1';
                    model_ver_val    := 'MV-STEEL-SURFACE-20260323-001';
                    anomaly_a_val    := '钢材划痕';
                    anomaly_b_val    := '折叠缺陷';
                WHEN 6 THEN
                    scene_id_val     := 'SCENE-STRIP-001';
                    factory_code_val := 'STRIP';
                    plugin_id_val    := 'HEAD-STRIP-SHEAR-V1';
                    model_ver_val    := 'MV-BILLET-CRACK-20260320-001';
                    anomaly_a_val    := '飞剪时序异常';
                    anomaly_b_val    := '带钢板形不良';
            END CASE;

            n := count_matrix[date_idx][scene_idx];

            FOR r IN 1..n LOOP
                -- 时间偏移：06:00-21:59 UTC（对应北京时间 14:00-05:59+1），保证在 UTC 当天分区内
                hour_off := 6 + ((seq * 13 + r * 7 + scene_idx * 3) % 16);
                min_off  := (seq * 17 + r * 11 + scene_idx * 5) % 60;
                alarm_ts := day_base
                    + (hour_off * INTERVAL '1 hour')
                    + (min_off  * INTERVAL '1 minute');

                -- 告警级别（7 元素循环）
                lv := levels[((seq + scene_idx) % 7) + 1];

                -- 置信度：CRITICAL≥0.90，WARNING 0.80-0.89，INFO 0.62-0.79
                conf := CASE lv
                    WHEN 'CRITICAL' THEN LEAST(0.9900, 0.9000 + (((seq * 31 + scene_idx * 7) % 10) * 0.0100))::DECIMAL(5,4)
                    WHEN 'WARNING'  THEN LEAST(0.8990, 0.8000 + (((seq * 23 + scene_idx * 3) % 10) * 0.0090))::DECIMAL(5,4)
                    ELSE                 LEAST(0.7900, 0.6200 + (((seq * 19 + scene_idx * 5) % 18) * 0.0090))::DECIMAL(5,4)
                END;

                -- 确认帧数
                conf_frm := CASE lv WHEN 'CRITICAL' THEN 3 WHEN 'WARNING' THEN 2 ELSE 1 END;

                -- 异常类型（奇偶交替）
                anomaly := CASE WHEN (seq % 2 = 0) THEN anomaly_a_val ELSE anomaly_b_val END;

                -- 推送状态（date_idx=7 即今天保留 PENDING，历史设为 SENT）
                push_st := CASE WHEN date_idx = 7 THEN 'PENDING' ELSE 'SENT' END;

                INSERT INTO alarm_record (
                    alarm_id, scene_id, factory_code, alarm_level, anomaly_type,
                    confidence, bbox_json, image_url, frame_id,
                    plugin_id, model_version_id,
                    is_sandbox, confirm_frames, push_status,
                    push_channels_json, alarm_at, created_at
                ) VALUES (
                    'ALM-' || TO_CHAR(alarm_ts, 'YYYYMMDD') || '-' || LPAD(seq::TEXT, 6, '0'),
                    scene_id_val,
                    factory_code_val,
                    lv,
                    anomaly,
                    conf,
                    ('{"x1":' || (100 + (seq * 37 % 800))::TEXT
                     || ',"y1":' || (50  + (seq * 29 % 400))::TEXT
                     || ',"x2":' || (300 + (seq * 37 % 800))::TEXT
                     || ',"y2":' || (200 + (seq * 29 % 400))::TEXT || '}')::JSONB,
                    'minio://tianjing-frames-prod/' || LOWER(factory_code_val)
                        || '/' || scene_id_val || '/' || TO_CHAR(alarm_ts, 'YYYY-MM')
                        || '/' || EXTRACT(EPOCH FROM alarm_ts)::BIGINT::TEXT
                        || '_f' || LPAD(seq::TEXT, 5, '0') || '.jpg',
                    'f' || LPAD(seq::TEXT, 5, '0'),
                    plugin_id_val,
                    model_ver_val,
                    FALSE,
                    conf_frm,
                    push_st,
                    CASE lv
                        WHEN 'CRITICAL' THEN '["MQTT","WECOM","SCADA"]'::JSONB
                        WHEN 'WARNING'  THEN '["MQTT","WECOM"]'::JSONB
                        ELSE                 '["DB"]'::JSONB
                    END,
                    alarm_ts,
                    alarm_ts
                );

                seq := seq + 1;
            END LOOP;
        END LOOP;
    END LOOP;

    RAISE NOTICE '演示告警数据插入完成，共 % 条', seq - 1;
END;
$$;
