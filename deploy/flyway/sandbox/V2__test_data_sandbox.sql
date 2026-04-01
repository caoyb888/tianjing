-- ============================================================
-- Flyway 迁移脚本 V2
-- 数据库：tianjing_sandbox（Sandbox 实验室库）
-- 说明：永久测试数据集 — Sandbox 推理会话、对比报告、转正日志
--       覆盖：Sprint 3 主场景（烧结看火）+ P4 实验场景 + 铸坯裂纹候选模型验证
--       对应：测试计划 V1.0 § 6（Sandbox 实验室功能测试）、§ 11（Sandbox 拦截安全测试）
-- 编制：测试负责人 · 2026-04-02
-- ============================================================

-- ============================================================
-- PART 1：Sandbox 推理会话
--
-- 设计说明：
--   SB-SESS-SINTERFIRE-001  → 烧结看火 Sprint 3 验证会话（已转正，用于 PROMOTED 流程测试）
--   SB-SESS-BILLET-001      → 铸坯裂纹候选模型（MV-BILLET-CRACK-20260401-002）验证中，
--                             32 h < 48 h 门禁，gate_passed=FALSE，用于四眼原则 + 转正流程测试
--   SB-SESS-STEEL003-001    → 转炉溅渣渣粒密度（P4 SANDBOX_ONLY 永久实验场景）
--   SB-SESS-STEEL004-001    → 吹氩站底吹状态（P4 SANDBOX_ONLY 永久实验场景）
--   SB-SESS-SINTER004-001   → 成一皮带料面（P4 SANDBOX_ONLY 永久实验场景）
-- ============================================================

INSERT INTO sandbox_infer_session (
    session_id, scene_id,
    candidate_model_id, prod_model_id,
    mirror_fps, status,
    start_at, end_at,
    total_frames, total_anomaly_frames,
    created_by
) VALUES

-- 烧结看火：已转正会话（用于测试 PROMOTED 状态查询、历史报告回溯）
(
    'SB-SESS-SINTERFIRE-001',
    'SCENE-SINTER-FIRE-001',
    'ARFCT-MATERIAL-LEVEL-001',     -- 训练库产物 ID（跨库字符串引用）
    'N/A-NO-PREV-PROD-MODEL',       -- 初次上线，无前任生产模型
    5, 'PROMOTED',
    '2026-03-25 08:00:00+08', '2026-03-27 09:15:00+08',
    12960, 1152,                    -- 5fps × 2h×24 + 1h15m ≈ 12960 帧，异常率约 8.9%
    'test_sandbox_op'
),

-- 铸坯裂纹候选模型：验证进行中（用于测试 RUNNING 状态、门禁未通过、四眼原则）
(
    'SB-SESS-BILLET-001',
    'SCENE-STEEL-001',
    'MV-BILLET-CRACK-20260401-002', -- 生产库候选模型 ID
    'MV-BILLET-CRACK-20260320-001', -- 当前生产模型
    5, 'RUNNING',
    '2026-04-01 10:00:00+08', NULL,
    28800, 2448,                    -- 5fps × 32h = 576000s → 28800 帧，异常率约 8.5%
    'test_sandbox_op'
),

-- 转炉溅渣渣粒密度：P4 永久实验场景（SANDBOX_ONLY，无对应生产模型）
(
    'SB-SESS-STEEL003-001',
    'SCENE-STEEL-003',
    'ARFCT-SLAG-DENSITY-PROTO-001', -- 实验室原型模型，尚未申请生产
    'N/A-NO-PROD-MODEL',
    5, 'RUNNING',
    '2026-03-28 14:00:00+08', NULL,
    10800, 756,                     -- 5fps × 6h = 10800 帧，异常率约 7.0%
    'test_sandbox_op'
),

-- 吹氩站底吹状态：P4 永久实验场景
(
    'SB-SESS-STEEL004-001',
    'SCENE-STEEL-004',
    'ARFCT-ARGON-BLOW-PROTO-001',
    'N/A-NO-PROD-MODEL',
    5, 'RUNNING',
    '2026-03-29 09:00:00+08', NULL,
    14400, 1296,                    -- 5fps × 8h = 14400 帧，异常率约 9.0%
    'test_sandbox_op'
),

-- 成一皮带料面：P4 永久实验场景
(
    'SB-SESS-SINTER004-001',
    'SCENE-SINTER-004',
    'ARFCT-BELT-LEVEL-PROTO-001',
    'N/A-NO-PROD-MODEL',
    5, 'RUNNING',
    '2026-03-30 13:00:00+08', NULL,
    7200, 504,                      -- 5fps × 4h = 7200 帧，异常率约 7.0%
    'test_sandbox_op'
);


-- ============================================================
-- PART 2：Sandbox 推理结果明细样本
-- 每个会话插入少量代表性样本（真实环境会有海量记录，此处仅用于功能验证）
-- 测试场景覆盖：正常帧 / 异常帧 / 高置信度 / 低置信度
-- ============================================================

-- SB-SESS-BILLET-001 推理结果样本（铸坯裂纹）
INSERT INTO sandbox_infer_result (
    session_id, frame_id, scene_id,
    detections_json, inference_ms,
    has_anomaly, top_class_name, confidence,
    infer_at
) VALUES

-- 正常帧（无缺陷）
(
    'SB-SESS-BILLET-001', 'SB-FRM-B001-00001', 'SCENE-STEEL-001',
    '[]',
    36.2, FALSE, NULL, NULL,
    '2026-04-01 10:00:01+08'
),

-- 异常帧：纵裂纹（高置信度）
(
    'SB-SESS-BILLET-001', 'SB-FRM-B001-00128', 'SCENE-STEEL-001',
    '[{"class_id":0,"class_name":"纵裂纹","confidence":0.9210,"bbox":{"x1":342,"y1":168,"x2":856,"y2":395},"measurement":null}]',
    37.1, TRUE, '纵裂纹', 0.9210,
    '2026-04-01 10:25:36+08'
),

-- 异常帧：角裂纹（中等置信度，用于测试阈值边界）
(
    'SB-SESS-BILLET-001', 'SB-FRM-B001-00256', 'SCENE-STEEL-001',
    '[{"class_id":3,"class_name":"角裂纹","confidence":0.8340,"bbox":{"x1":120,"y1":820,"x2":310,"y2":980},"measurement":null}]',
    36.8, TRUE, '角裂纹', 0.8340,
    '2026-04-01 10:51:12+08'
),

-- 连续异常帧（用于测试 CRITICAL 告警的连续 3 帧判断逻辑）
-- 注：Sandbox 中这 3 帧应被 alarm-judge-service 拦截，绝不触发外部推送
(
    'SB-SESS-BILLET-001', 'SB-FRM-B001-00512', 'SCENE-STEEL-001',
    '[{"class_id":0,"class_name":"纵裂纹","confidence":0.9450,"bbox":{"x1":280,"y1":150,"x2":920,"y2":420},"measurement":null}]',
    35.9, TRUE, '纵裂纹', 0.9450,
    '2026-04-01 12:47:24+08'
),
(
    'SB-SESS-BILLET-001', 'SB-FRM-B001-00513', 'SCENE-STEEL-001',
    '[{"class_id":0,"class_name":"纵裂纹","confidence":0.9520,"bbox":{"x1":285,"y1":155,"x2":915,"y2":418},"measurement":null}]',
    36.1, TRUE, '纵裂纹', 0.9520,
    '2026-04-01 12:47:25+08'
),
(
    'SB-SESS-BILLET-001', 'SB-FRM-B001-00514', 'SCENE-STEEL-001',
    '[{"class_id":0,"class_name":"纵裂纹","confidence":0.9380,"bbox":{"x1":290,"y1":148,"x2":910,"y2":422},"measurement":null}]',
    36.5, TRUE, '纵裂纹', 0.9380,
    '2026-04-01 12:47:26+08'
),

-- 低置信度（低于报警阈值，不应触发告警）
(
    'SB-SESS-BILLET-001', 'SB-FRM-B001-00768', 'SCENE-STEEL-001',
    '[{"class_id":2,"class_name":"横裂纹","confidence":0.6820,"bbox":{"x1":450,"y1":300,"x2":680,"y2":450},"measurement":null}]',
    36.3, FALSE, '横裂纹', 0.6820,
    '2026-04-01 15:32:48+08'
);


-- SB-SESS-SINTERFIRE-001 推理结果样本（烧结看火料面，已转正会话）
INSERT INTO sandbox_infer_result (
    session_id, frame_id, scene_id,
    detections_json, inference_ms,
    has_anomaly, top_class_name, confidence,
    infer_at
) VALUES

-- 正常料面
(
    'SB-SESS-SINTERFIRE-001', 'SB-FRM-SF001-00001', 'SCENE-SINTER-FIRE-001',
    '[{"class_id":1,"class_name":"料面正常","confidence":0.9610,"bbox":null,"measurement":null}]',
    9.2, FALSE, '料面正常', 0.9610,
    '2026-03-25 08:00:01+08'
),

-- 料面过低（异常）
(
    'SB-SESS-SINTERFIRE-001', 'SB-FRM-SF001-00360', 'SCENE-SINTER-FIRE-001',
    '[{"class_id":0,"class_name":"料面过低","confidence":0.9150,"bbox":null,"measurement":null}]',
    9.1, TRUE, '料面过低', 0.9150,
    '2026-03-25 08:12:00+08'
),

-- 局部偏料（异常）
(
    'SB-SESS-SINTERFIRE-001', 'SB-FRM-SF001-02880', 'SCENE-SINTER-FIRE-001',
    '[{"class_id":3,"class_name":"局部偏料","confidence":0.8780,"bbox":null,"measurement":null}]',
    9.4, TRUE, '局部偏料', 0.8780,
    '2026-03-25 10:36:00+08'
);


-- ============================================================
-- PART 3：Sandbox 对比分析报告
--
-- SB-RPT-SINTERFIRE-001：已转正报告（gate_passed=TRUE，promote_status=PROMOTED）
--   → 用于测试：转正历史查询、精度对比展示、已通过门禁详情
--
-- SB-RPT-BILLET-001：进行中报告（gate_passed=FALSE，hours_gate 未通过）
--   → 用于测试：TC-SB-005 门禁验证（连续小时数不足）、转正申请前置校验
-- ============================================================

INSERT INTO sandbox_compare_report (
    report_id, session_id, scene_id,
    hours_evaluated,
    sandbox_precision, sandbox_recall,
    prod_precision, prod_recall,
    precision_delta, potential_gain_count,
    sandbox_gpu_mb, prod_gpu_mb,
    sandbox_p99_ms, prod_p99_ms,
    gate_passed, gate_detail_json,
    promote_status, applied_at, approved_by, approved_at,
    generated_at
) VALUES

-- 烧结看火：已转正报告（初次上线，无 prod 精度对比，门禁特殊通过）
(
    'SB-RPT-SINTERFIRE-001',
    'SB-SESS-SINTERFIRE-001',
    'SCENE-SINTER-FIRE-001',
    49,
    0.9310, 0.9120,
    NULL, NULL,               -- 无前任生产模型，精度 delta 为 NULL
    NULL, 0,
    4328, NULL,
    11.2, NULL,
    TRUE,
    '{"accuracy_gate":{"pass":true,"note":"初次上线，无基准对比，精度绝对值>=85%视为通过"},"resource_gate":{"pass":true,"sandbox_gpu_mb":4328,"threshold_mb":null},"latency_gate":{"pass":true,"sandbox_p99_ms":11.2,"threshold_ms":50.0},"hours_gate":{"pass":true,"evaluated_hours":49,"required_hours":48}}',
    'PROMOTED',
    '2026-03-27 09:30:00+08',
    'test_reviewer_b',
    '2026-03-27 14:20:00+08',
    '2026-03-27 09:15:00+08'
),

-- 铸坯裂纹候选模型：中期报告（32 h，门禁未通过，小时数不足）
(
    'SB-RPT-BILLET-001',
    'SB-SESS-BILLET-001',
    'SCENE-STEEL-001',
    32,
    0.9060, 0.8590,
    0.8910, 0.8420,
    0.0150, 28,               -- 精度提升 +1.5%（< 2% 门禁要求）
    4112, 4256,
    44.8, 46.2,
    FALSE,
    '{"accuracy_gate":{"pass":false,"delta":0.0150,"required_delta":0.0200,"note":"精度提升1.5%，未达到2%门禁要求"},"resource_gate":{"pass":true,"sandbox_gpu_mb":4112,"prod_gpu_mb":4256,"delta_mb":-144},"latency_gate":{"pass":true,"sandbox_p99_ms":44.8,"prod_p99_ms":46.2,"delta_ms":-1.4},"hours_gate":{"pass":false,"evaluated_hours":32,"required_hours":48,"note":"连续验证时长不足，需继续运行至少16小时"}}',
    'PENDING',
    NULL, NULL, NULL,
    '2026-04-01 18:00:00+08'
);


-- ============================================================
-- PART 4：Sandbox 转正日志
-- SB-LOG-SINTERFIRE-001：烧结看火场景转正审批通过记录（APPROVED）
-- 注：此为测试数据中唯一的 APPROVED 转正记录，用于历史回溯测试
-- ============================================================

INSERT INTO sandbox_promote_log (
    log_id, session_id, scene_id, report_id,
    promote_result, promoted_by, reject_reason,
    promoted_at
) VALUES (
    'SB-LOG-SINTERFIRE-001',
    'SB-SESS-SINTERFIRE-001',
    'SCENE-SINTER-FIRE-001',
    'SB-RPT-SINTERFIRE-001',
    'APPROVED',
    'test_reviewer_b',          -- 审批人与提交人 test_reviewer_a 不同（四眼原则）
    NULL,
    '2026-03-27 14:20:00+08'
);
