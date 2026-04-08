-- ============================================================
-- TDengine 演示数据脚本
-- 数据库：tianjing_ts（时序库）
-- 说明：为实时告警大屏演示生成近 8 天推理结果时序数据（2026-04-01 ~ 2026-04-08）
--       覆盖 6 个生产场景，共 380 条推理记录，体现工作日/周末产量波动
--       每日推理量：04-01(43) 04-02(51) 04-03(58) 04-04(37)
--                   04-05(29) 04-06(65) 04-07(72) 04-08(24,进行中)
--       random.seed(42) 生成，结果确定可复现
--       执行方式：taos -h <tdengine-host> < seed_tdengine_demo.sql
--                 或通过 TDengine REST API 逐段执行
-- 编制：测试负责人 · 2026-04-08
-- ============================================================

USE tianjing_ts;

-- ============================================================
-- STEP 1：创建推理结果子表（幂等，已存在则跳过）
-- ============================================================
CREATE TABLE IF NOT EXISTS ir_scene_pellet_001  USING infer_result TAGS ('SCENE-PELLET-001',  'PELLET',  FALSE) TTL 15552000;
CREATE TABLE IF NOT EXISTS ir_scene_sinter_001  USING infer_result TAGS ('SCENE-SINTER-001',  'SINTER',  FALSE) TTL 15552000;
CREATE TABLE IF NOT EXISTS ir_scene_sinter_002  USING infer_result TAGS ('SCENE-SINTER-002',  'SINTER',  FALSE) TTL 15552000;
CREATE TABLE IF NOT EXISTS ir_scene_steel_001   USING infer_result TAGS ('SCENE-STEEL-001',   'STEEL',   FALSE) TTL 15552000;
CREATE TABLE IF NOT EXISTS ir_scene_section_001 USING infer_result TAGS ('SCENE-SECTION-001', 'SECTION', FALSE) TTL 15552000;
CREATE TABLE IF NOT EXISTS ir_scene_strip_001   USING infer_result TAGS ('SCENE-STRIP-001',   'STRIP',   FALSE) TTL 15552000;

CREATE TABLE IF NOT EXISTS alm_scene_pellet_001  USING alarm_timeseries TAGS ('SCENE-PELLET-001',  'PELLET',  FALSE) TTL 31536000;
CREATE TABLE IF NOT EXISTS alm_scene_sinter_001  USING alarm_timeseries TAGS ('SCENE-SINTER-001',  'SINTER',  FALSE) TTL 31536000;
CREATE TABLE IF NOT EXISTS alm_scene_sinter_002  USING alarm_timeseries TAGS ('SCENE-SINTER-002',  'SINTER',  FALSE) TTL 31536000;
CREATE TABLE IF NOT EXISTS alm_scene_steel_001   USING alarm_timeseries TAGS ('SCENE-STEEL-001',   'STEEL',   FALSE) TTL 31536000;
CREATE TABLE IF NOT EXISTS alm_scene_section_001 USING alarm_timeseries TAGS ('SCENE-SECTION-001', 'SECTION', FALSE) TTL 31536000;
CREATE TABLE IF NOT EXISTS alm_scene_strip_001   USING alarm_timeseries TAGS ('SCENE-STRIP-001',   'STRIP',   FALSE) TTL 31536000;

-- ============================================================
-- STEP 2：清空旧演示数据（重复执行时保证幂等）
-- ============================================================
DELETE FROM ir_scene_pellet_001  WHERE ts >= '2026-04-01' AND ts < '2026-04-09';
DELETE FROM ir_scene_sinter_001  WHERE ts >= '2026-04-01' AND ts < '2026-04-09';
DELETE FROM ir_scene_sinter_002  WHERE ts >= '2026-04-01' AND ts < '2026-04-09';
DELETE FROM ir_scene_steel_001   WHERE ts >= '2026-04-01' AND ts < '2026-04-09';
DELETE FROM ir_scene_section_001 WHERE ts >= '2026-04-01' AND ts < '2026-04-09';
DELETE FROM ir_scene_strip_001   WHERE ts >= '2026-04-01' AND ts < '2026-04-09';

-- ============================================================
-- STEP 3：推理结果数据（380 条，random.seed=42 确定性生成）
-- 告警率：工作日 40%，周末 55%（体现设备周末例检后异常偏多）
-- ============================================================

INSERT INTO ir_scene_pellet_001 VALUES
  ('2026-04-01T06:46:01.000+00:00','f_20260401_0_000',0.8592,1,17.3,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-01/f0000.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-01T08:07:47.000+00:00','f_20260401_0_001',0.8339,2,18.8,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-01/f0001.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-01T10:02:13.000+00:00','f_20260401_0_002',0.6921,0,19.6,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-01/f0002.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-01T12:51:44.000+00:00','f_20260401_0_003',0.6654,0,19.7,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-01/f0003.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-01T14:58:00.000+00:00','f_20260401_0_004',0.8455,1,18.8,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-01/f0004.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-01T16:20:13.000+00:00','f_20260401_0_005',0.8739,2,17.0,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-01/f0005.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-01T18:06:54.000+00:00','f_20260401_0_006',0.6685,0,16.7,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-01/f0006.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-01T20:33:07.000+00:00','f_20260401_0_007',0.8806,3,19.5,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-01/f0007.jpg','HEAD-SIDEPLATE-V1','v1.2.0');

INSERT INTO ir_scene_sinter_001 VALUES
  ('2026-04-01T06:53:39.000+00:00','f_20260401_1_000',0.8779,2,18.3,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0000.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-01T07:51:42.000+00:00','f_20260401_1_001',0.6703,0,17.6,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0001.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-01T09:48:06.000+00:00','f_20260401_1_002',0.6817,0,21.8,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0002.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-01T11:30:22.000+00:00','f_20260401_1_003',0.6687,0,22.4,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0003.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-01T13:48:38.000+00:00','f_20260401_1_004',0.9055,1,18.5,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0004.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-01T15:22:17.000+00:00','f_20260401_1_005',0.9224,3,20.3,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0005.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-01T17:23:53.000+00:00','f_20260401_1_006',0.829,1,21.7,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0006.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-01T19:18:25.000+00:00','f_20260401_1_007',0.6648,0,22.4,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0007.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-01T21:09:20.000+00:00','f_20260401_1_008',0.6849,0,22.1,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0008.jpg','HEAD-SINTER-GRATE-V1','v1.0.0');

INSERT INTO ir_scene_sinter_002 VALUES
  ('2026-04-01T07:01:09.000+00:00','f_20260401_2_000',0.6673,0,19.1,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0000.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-01T09:05:37.000+00:00','f_20260401_2_001',0.6908,0,18.0,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0001.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-01T12:55:08.000+00:00','f_20260401_2_002',0.6564,0,16.3,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0002.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-01T14:10:40.000+00:00','f_20260401_2_003',0.6976,0,19.3,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0003.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-01T17:16:38.000+00:00','f_20260401_2_004',0.9047,3,21.3,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0004.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-01T20:42:00.000+00:00','f_20260401_2_005',0.8383,1,20.9,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-01/f0005.jpg','ATOM-DETECT-YOLO-V1','v1.2.0');

INSERT INTO ir_scene_steel_001 VALUES
  ('2026-04-01T06:48:49.000+00:00','f_20260401_3_000',0.8378,1,21.9,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-01/f0000.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-01T08:15:46.000+00:00','f_20260401_3_001',0.8621,2,22.3,'铸坯横裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-01/f0001.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-01T09:44:58.000+00:00','f_20260401_3_002',0.6938,0,24.1,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-01/f0002.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-01T11:52:12.000+00:00','f_20260401_3_003',0.7034,0,22.5,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-01/f0003.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-01T13:56:33.000+00:00','f_20260401_3_004',0.9158,2,22.2,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-01/f0004.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-01T15:00:23.000+00:00','f_20260401_3_005',0.9531,2,21.2,'铸坯横裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-01/f0005.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-01T16:43:56.000+00:00','f_20260401_3_006',0.6555,0,23.5,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-01/f0006.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-01T19:18:48.000+00:00','f_20260401_3_007',0.6588,0,23.1,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-01/f0007.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-01T21:13:10.000+00:00','f_20260401_3_008',0.7111,0,21.8,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-01/f0008.jpg','HEAD-BILLET-CRACK-V1','v1.1.0');

INSERT INTO ir_scene_section_001 VALUES
  ('2026-04-01T06:20:34.000+00:00','f_20260401_4_000',0.9304,1,18.7,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-01/f0000.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-01T09:18:42.000+00:00','f_20260401_4_001',0.8901,1,17.6,'折叠缺陷',NULL,'minio://tianjing-frames-prod/section/2026-04-01/f0001.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-01T11:31:14.000+00:00','f_20260401_4_002',0.6515,0,17.8,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-01/f0002.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-01T14:56:00.000+00:00','f_20260401_4_003',0.6942,0,16.1,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-01/f0003.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-01T18:06:55.000+00:00','f_20260401_4_004',0.686,0,16.3,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-01/f0004.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-01T20:06:34.000+00:00','f_20260401_4_005',0.7155,0,17.9,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-01/f0005.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0');

INSERT INTO ir_scene_strip_001 VALUES
  ('2026-04-01T07:08:50.000+00:00','f_20260401_5_000',0.6785,0,6.6,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-01/f0000.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-01T11:34:22.000+00:00','f_20260401_5_001',0.6827,0,10.1,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-01/f0001.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-01T15:36:41.000+00:00','f_20260401_5_002',0.6782,0,8.0,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-01/f0002.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-01T20:04:15.000+00:00','f_20260401_5_003',0.6875,0,6.9,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-01/f0003.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0');

INSERT INTO ir_scene_pellet_001 VALUES
  ('2026-04-02T06:11:29.000+00:00','f_20260402_0_000',0.7146,0,18.9,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-02/f0000.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-02T08:41:35.000+00:00','f_20260402_0_001',0.6957,0,19.5,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-02/f0001.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-02T09:34:05.000+00:00','f_20260402_0_002',0.9558,2,17.4,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-02/f0002.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-02T11:51:13.000+00:00','f_20260402_0_003',0.9644,2,17.4,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-02/f0003.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-02T13:06:24.000+00:00','f_20260402_0_004',0.7049,0,19.0,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-02/f0004.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-02T15:20:46.000+00:00','f_20260402_0_005',0.9089,3,20.5,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-02/f0005.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-02T16:49:18.000+00:00','f_20260402_0_006',0.6541,0,20.5,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-02/f0006.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-02T18:30:20.000+00:00','f_20260402_0_007',0.6909,0,19.3,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-02/f0007.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-02T21:07:10.000+00:00','f_20260402_0_008',0.6855,0,21.2,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-02/f0008.jpg','HEAD-SIDEPLATE-V1','v1.2.0');

INSERT INTO ir_scene_sinter_001 VALUES
  ('2026-04-02T06:03:04.000+00:00','f_20260402_1_000',0.8576,1,17.9,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0000.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-02T08:13:15.000+00:00','f_20260402_1_001',0.6528,0,17.7,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0001.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-02T09:28:36.000+00:00','f_20260402_1_002',0.7154,0,18.3,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0002.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-02T10:59:15.000+00:00','f_20260402_1_003',0.6592,0,20.8,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0003.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-02T12:13:59.000+00:00','f_20260402_1_004',0.8316,1,19.7,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0004.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-02T14:08:06.000+00:00','f_20260402_1_005',0.6649,0,18.7,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0005.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-02T15:32:56.000+00:00','f_20260402_1_006',0.6671,0,18.8,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0006.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-02T16:33:34.000+00:00','f_20260402_1_007',0.9179,1,22.6,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0007.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-02T18:12:00.000+00:00','f_20260402_1_008',0.9087,1,22.3,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0008.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-02T19:10:56.000+00:00','f_20260402_1_009',0.6581,0,17.8,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0009.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-02T21:01:17.000+00:00','f_20260402_1_010',0.6647,0,19.1,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0010.jpg','HEAD-SINTER-GRATE-V1','v1.0.0');

INSERT INTO ir_scene_sinter_002 VALUES
  ('2026-04-02T06:49:54.000+00:00','f_20260402_2_000',0.6842,0,21.0,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0000.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-02T09:00:05.000+00:00','f_20260402_2_001',0.9527,1,16.2,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0001.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-02T10:24:08.000+00:00','f_20260402_2_002',0.8619,1,20.1,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0002.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-02T12:39:27.000+00:00','f_20260402_2_003',0.6578,0,21.2,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0003.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-02T14:49:09.000+00:00','f_20260402_2_004',0.7084,0,19.2,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0004.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-02T16:10:08.000+00:00','f_20260402_2_005',0.6755,0,21.1,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0005.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-02T19:09:02.000+00:00','f_20260402_2_006',0.8536,2,17.4,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0006.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-02T20:07:49.000+00:00','f_20260402_2_007',0.7112,0,21.4,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-02/f0007.jpg','ATOM-DETECT-YOLO-V1','v1.2.0');

INSERT INTO ir_scene_steel_001 VALUES
  ('2026-04-02T06:43:59.000+00:00','f_20260402_3_000',0.9583,2,24.9,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-02/f0000.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-02T08:22:56.000+00:00','f_20260402_3_001',0.6626,0,24.6,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-02/f0001.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-02T09:57:26.000+00:00','f_20260402_3_002',0.9583,1,24.0,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-02/f0002.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-02T11:03:50.000+00:00','f_20260402_3_003',0.8812,1,19.7,'铸坯横裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-02/f0003.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-02T12:51:12.000+00:00','f_20260402_3_004',0.8936,1,21.2,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-02/f0004.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-02T14:45:14.000+00:00','f_20260402_3_005',0.6962,0,21.7,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-02/f0005.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-02T15:52:04.000+00:00','f_20260402_3_006',0.8647,3,23.0,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-02/f0006.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-02T17:35:53.000+00:00','f_20260402_3_007',0.7157,0,20.1,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-02/f0007.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-02T19:43:11.000+00:00','f_20260402_3_008',0.7188,0,19.7,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-02/f0008.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-02T20:58:22.000+00:00','f_20260402_3_009',0.8702,1,22.8,'铸坯横裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-02/f0009.jpg','HEAD-BILLET-CRACK-V1','v1.1.0');

INSERT INTO ir_scene_section_001 VALUES
  ('2026-04-02T06:36:24.000+00:00','f_20260402_4_000',0.8504,2,15.0,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-02/f0000.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-02T08:31:33.000+00:00','f_20260402_4_001',0.9062,2,18.8,'折叠缺陷',NULL,'minio://tianjing-frames-prod/section/2026-04-02/f0001.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-02T10:53:42.000+00:00','f_20260402_4_002',0.6802,0,20.0,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-02/f0002.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-02T13:06:39.000+00:00','f_20260402_4_003',0.7094,0,18.8,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-02/f0003.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-02T14:21:19.000+00:00','f_20260402_4_004',0.8722,1,18.6,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-02/f0004.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-02T16:39:12.000+00:00','f_20260402_4_005',0.7158,0,18.5,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-02/f0005.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-02T19:05:39.000+00:00','f_20260402_4_006',0.6784,0,19.4,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-02/f0006.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-02T20:21:13.000+00:00','f_20260402_4_007',0.6906,0,18.4,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-02/f0007.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0');

INSERT INTO ir_scene_strip_001 VALUES
  ('2026-04-02T06:53:28.000+00:00','f_20260402_5_000',0.9018,1,10.5,'飞剪时序异常',NULL,'minio://tianjing-frames-prod/strip/2026-04-02/f0000.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-02T11:02:47.000+00:00','f_20260402_5_001',0.6559,0,8.9,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-02/f0001.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-02T13:36:21.000+00:00','f_20260402_5_002',0.7166,0,7.4,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-02/f0002.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-02T16:11:51.000+00:00','f_20260402_5_003',0.6517,0,7.4,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-02/f0003.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-02T19:42:54.000+00:00','f_20260402_5_004',0.8929,1,11.0,'飞剪时序异常',NULL,'minio://tianjing-frames-prod/strip/2026-04-02/f0004.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0');

INSERT INTO ir_scene_pellet_001 VALUES
  ('2026-04-03T06:33:45.000+00:00','f_20260403_0_000',0.8991,1,17.8,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-03/f0000.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-03T08:13:00.000+00:00','f_20260403_0_001',0.9577,2,21.4,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-03/f0001.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-03T09:56:14.000+00:00','f_20260403_0_002',0.7171,0,19.3,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-03/f0002.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-03T10:50:15.000+00:00','f_20260403_0_003',0.8394,2,17.2,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-03/f0003.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-03T12:50:33.000+00:00','f_20260403_0_004',0.9153,3,21.7,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-03/f0004.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-03T14:51:39.000+00:00','f_20260403_0_005',0.9627,1,18.8,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-03/f0005.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-03T16:28:28.000+00:00','f_20260403_0_006',0.939,2,19.1,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-03/f0006.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-03T17:26:15.000+00:00','f_20260403_0_007',0.8644,1,20.8,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-03/f0007.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-03T19:15:15.000+00:00','f_20260403_0_008',0.6554,0,18.1,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-03/f0008.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-03T20:39:20.000+00:00','f_20260403_0_009',0.8329,2,17.3,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-03/f0009.jpg','HEAD-SIDEPLATE-V1','v1.2.0');

INSERT INTO ir_scene_sinter_001 VALUES
  ('2026-04-03T06:16:09.000+00:00','f_20260403_1_000',0.8303,1,19.4,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0000.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-03T07:37:26.000+00:00','f_20260403_1_001',0.7083,0,19.3,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0001.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-03T09:01:44.000+00:00','f_20260403_1_002',0.7116,0,20.4,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0002.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-03T10:02:22.000+00:00','f_20260403_1_003',0.6773,0,22.1,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0003.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-03T11:32:34.000+00:00','f_20260403_1_004',0.9074,1,20.5,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0004.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-03T12:19:14.000+00:00','f_20260403_1_005',0.684,0,19.3,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0005.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-03T13:52:51.000+00:00','f_20260403_1_006',0.6616,0,19.8,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0006.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-03T14:42:39.000+00:00','f_20260403_1_007',0.7135,0,20.5,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0007.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-03T16:20:05.000+00:00','f_20260403_1_008',0.8417,1,19.7,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0008.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-03T17:06:24.000+00:00','f_20260403_1_009',0.6818,0,19.1,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0009.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-03T18:57:17.000+00:00','f_20260403_1_010',0.9531,1,18.6,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0010.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-03T19:35:01.000+00:00','f_20260403_1_011',0.8283,1,22.4,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0011.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-03T20:56:04.000+00:00','f_20260403_1_012',0.9243,1,21.3,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0012.jpg','HEAD-SINTER-GRATE-V1','v1.0.0');

INSERT INTO ir_scene_sinter_002 VALUES
  ('2026-04-03T07:00:12.000+00:00','f_20260403_2_000',0.9194,1,17.3,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0000.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-03T08:16:07.000+00:00','f_20260403_2_001',0.6653,0,19.8,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0001.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-03T10:22:10.000+00:00','f_20260403_2_002',0.9742,1,20.0,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0002.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-03T12:09:10.000+00:00','f_20260403_2_003',0.8373,3,16.1,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0003.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-03T13:26:43.000+00:00','f_20260403_2_004',0.88,2,21.2,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0004.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-03T15:06:37.000+00:00','f_20260403_2_005',0.9204,1,16.6,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0005.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-03T17:29:54.000+00:00','f_20260403_2_006',0.9489,1,20.4,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0006.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-03T19:02:02.000+00:00','f_20260403_2_007',0.68,0,18.0,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0007.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-03T20:45:21.000+00:00','f_20260403_2_008',0.6794,0,18.7,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-03/f0008.jpg','ATOM-DETECT-YOLO-V1','v1.2.0');

INSERT INTO ir_scene_steel_001 VALUES
  ('2026-04-03T06:20:23.000+00:00','f_20260403_3_000',0.9526,1,23.4,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-03/f0000.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-03T07:40:46.000+00:00','f_20260403_3_001',0.6955,0,22.9,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-03/f0001.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-03T09:24:49.000+00:00','f_20260403_3_002',0.6805,0,23.5,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-03/f0002.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-03T10:12:54.000+00:00','f_20260403_3_003',0.7154,0,21.0,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-03/f0003.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-03T11:41:48.000+00:00','f_20260403_3_004',0.6927,0,21.6,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-03/f0004.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-03T12:41:54.000+00:00','f_20260403_3_005',0.6841,0,21.5,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-03/f0005.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-03T14:12:17.000+00:00','f_20260403_3_006',0.9322,2,21.0,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-03/f0006.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-03T15:20:12.000+00:00','f_20260403_3_007',0.7004,0,22.2,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-03/f0007.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-03T17:16:44.000+00:00','f_20260403_3_008',0.6998,0,22.0,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-03/f0008.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-03T18:00:18.000+00:00','f_20260403_3_009',0.6984,0,21.2,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-03/f0009.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-03T19:47:30.000+00:00','f_20260403_3_010',0.6741,0,25.0,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-03/f0010.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-03T21:06:22.000+00:00','f_20260403_3_011',0.8633,1,20.9,'铸坯横裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-03/f0011.jpg','HEAD-BILLET-CRACK-V1','v1.1.0');

INSERT INTO ir_scene_section_001 VALUES
  ('2026-04-03T06:07:12.000+00:00','f_20260403_4_000',0.702,0,20.0,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-03/f0000.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-03T08:30:12.000+00:00','f_20260403_4_001',0.6839,0,18.8,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-03/f0001.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-03T10:35:33.000+00:00','f_20260403_4_002',0.7186,0,19.4,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-03/f0002.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-03T11:38:23.000+00:00','f_20260403_4_003',0.651,0,17.7,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-03/f0003.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-03T13:24:03.000+00:00','f_20260403_4_004',0.6988,0,15.5,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-03/f0004.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-03T15:48:31.000+00:00','f_20260403_4_005',0.6509,0,16.4,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-03/f0005.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-03T17:10:21.000+00:00','f_20260403_4_006',0.6536,0,20.0,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-03/f0006.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-03T18:57:52.000+00:00','f_20260403_4_007',0.6844,0,18.0,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-03/f0007.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-03T20:57:09.000+00:00','f_20260403_4_008',0.6894,0,16.5,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-03/f0008.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0');

INSERT INTO ir_scene_strip_001 VALUES
  ('2026-04-03T07:35:07.000+00:00','f_20260403_5_000',0.6791,0,9.4,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-03/f0000.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-03T09:39:49.000+00:00','f_20260403_5_001',0.6815,0,8.5,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-03/f0001.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-03T12:42:27.000+00:00','f_20260403_5_002',0.6935,0,9.5,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-03/f0002.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-03T15:11:48.000+00:00','f_20260403_5_003',0.6648,0,9.7,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-03/f0003.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-03T16:55:11.000+00:00','f_20260403_5_004',0.661,0,8.3,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-03/f0004.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-03T20:26:30.000+00:00','f_20260403_5_005',0.6662,0,10.0,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-03/f0005.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0');

INSERT INTO ir_scene_pellet_001 VALUES
  ('2026-04-04T06:57:29.000+00:00','f_20260404_0_000',0.6663,0,18.0,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-04/f0000.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-04T09:22:37.000+00:00','f_20260404_0_001',0.9694,1,18.8,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-04/f0001.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-04T11:19:41.000+00:00','f_20260404_0_002',0.6686,0,17.3,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-04/f0002.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-04T12:56:50.000+00:00','f_20260404_0_003',0.7024,0,19.6,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-04/f0003.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-04T15:32:07.000+00:00','f_20260404_0_004',0.6713,0,18.7,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-04/f0004.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-04T17:48:34.000+00:00','f_20260404_0_005',0.6556,0,16.7,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-04/f0005.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-04T20:18:20.000+00:00','f_20260404_0_006',0.8241,1,17.8,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-04/f0006.jpg','HEAD-SIDEPLATE-V1','v1.2.0');

INSERT INTO ir_scene_sinter_001 VALUES
  ('2026-04-04T06:48:55.000+00:00','f_20260404_1_000',0.7166,0,22.7,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0000.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-04T08:48:17.000+00:00','f_20260404_1_001',0.7034,0,18.2,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0001.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-04T10:37:28.000+00:00','f_20260404_1_002',0.849,2,20.4,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0002.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-04T12:45:31.000+00:00','f_20260404_1_003',0.8952,3,19.4,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0003.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-04T14:23:06.000+00:00','f_20260404_1_004',0.8728,2,21.0,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0004.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-04T16:20:25.000+00:00','f_20260404_1_005',0.908,1,19.7,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0005.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-04T18:22:20.000+00:00','f_20260404_1_006',0.7041,0,22.0,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0006.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-04T20:59:00.000+00:00','f_20260404_1_007',0.9068,1,19.5,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0007.jpg','HEAD-SINTER-GRATE-V1','v1.0.0');

INSERT INTO ir_scene_sinter_002 VALUES
  ('2026-04-04T06:18:23.000+00:00','f_20260404_2_000',0.8998,1,18.4,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0000.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-04T08:44:17.000+00:00','f_20260404_2_001',0.7149,0,18.4,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0001.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-04T12:27:07.000+00:00','f_20260404_2_002',0.6941,0,20.4,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0002.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-04T15:08:19.000+00:00','f_20260404_2_003',0.6887,0,16.5,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0003.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-04T18:15:58.000+00:00','f_20260404_2_004',0.7163,0,19.6,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0004.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-04T19:34:59.000+00:00','f_20260404_2_005',0.9014,1,17.5,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-04/f0005.jpg','ATOM-DETECT-YOLO-V1','v1.2.0');

INSERT INTO ir_scene_steel_001 VALUES
  ('2026-04-04T07:08:30.000+00:00','f_20260404_3_000',0.6886,0,21.6,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-04/f0000.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-04T09:32:32.000+00:00','f_20260404_3_001',0.8418,1,19.9,'铸坯横裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-04/f0001.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-04T11:37:54.000+00:00','f_20260404_3_002',0.7154,0,22.3,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-04/f0002.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-04T13:58:18.000+00:00','f_20260404_3_003',0.954,1,22.7,'铸坯横裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-04/f0003.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-04T16:02:55.000+00:00','f_20260404_3_004',0.6877,0,21.4,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-04/f0004.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-04T18:11:34.000+00:00','f_20260404_3_005',0.7153,0,24.3,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-04/f0005.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-04T21:03:15.000+00:00','f_20260404_3_006',0.6663,0,23.8,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-04/f0006.jpg','HEAD-BILLET-CRACK-V1','v1.1.0');

INSERT INTO ir_scene_section_001 VALUES
  ('2026-04-04T06:04:47.000+00:00','f_20260404_4_000',0.7139,0,16.9,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-04/f0000.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-04T10:14:50.000+00:00','f_20260404_4_001',0.9769,1,17.5,'折叠缺陷',NULL,'minio://tianjing-frames-prod/section/2026-04-04/f0001.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-04T11:23:32.000+00:00','f_20260404_4_002',0.8731,3,15.4,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-04/f0002.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-04T15:21:06.000+00:00','f_20260404_4_003',0.682,0,18.8,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-04/f0003.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-04T17:19:41.000+00:00','f_20260404_4_004',0.832,3,19.1,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-04/f0004.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-04T19:45:39.000+00:00','f_20260404_4_005',0.924,1,19.5,'折叠缺陷',NULL,'minio://tianjing-frames-prod/section/2026-04-04/f0005.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0');

INSERT INTO ir_scene_strip_001 VALUES
  ('2026-04-04T08:02:54.000+00:00','f_20260404_5_000',0.7168,0,9.5,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-04/f0000.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-04T12:07:31.000+00:00','f_20260404_5_001',0.8257,2,6.5,'带钢板形不良',NULL,'minio://tianjing-frames-prod/strip/2026-04-04/f0001.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-04T15:30:58.000+00:00','f_20260404_5_002',0.6659,0,6.6,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-04/f0002.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-04T20:20:48.000+00:00','f_20260404_5_003',0.9596,1,8.5,'带钢板形不良',NULL,'minio://tianjing-frames-prod/strip/2026-04-04/f0003.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0');

INSERT INTO ir_scene_pellet_001 VALUES
  ('2026-04-05T07:19:57.000+00:00','f_20260405_0_000',0.6727,0,16.8,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-05/f0000.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-05T09:53:27.000+00:00','f_20260405_0_001',0.6872,0,19.6,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-05/f0001.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-05T13:55:10.000+00:00','f_20260405_0_002',0.6927,0,18.6,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-05/f0002.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-05T16:54:31.000+00:00','f_20260405_0_003',0.8429,2,19.0,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-05/f0003.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-05T19:17:16.000+00:00','f_20260405_0_004',0.9638,1,19.1,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-05/f0004.jpg','HEAD-SIDEPLATE-V1','v1.2.0');

INSERT INTO ir_scene_sinter_001 VALUES
  ('2026-04-05T06:27:34.000+00:00','f_20260405_1_000',0.6809,0,19.1,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-05/f0000.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-05T09:36:40.000+00:00','f_20260405_1_001',0.9305,3,19.7,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-05/f0001.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-05T11:49:24.000+00:00','f_20260405_1_002',0.8371,2,19.3,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-05/f0002.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-05T14:34:18.000+00:00','f_20260405_1_003',0.8672,3,22.6,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-05/f0003.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-05T17:43:17.000+00:00','f_20260405_1_004',0.7106,0,21.5,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-05/f0004.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-05T20:53:58.000+00:00','f_20260405_1_005',0.8995,1,22.2,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-05/f0005.jpg','HEAD-SINTER-GRATE-V1','v1.0.0');

INSERT INTO ir_scene_sinter_002 VALUES
  ('2026-04-05T06:41:51.000+00:00','f_20260405_2_000',0.7061,0,17.2,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-05/f0000.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-05T10:27:16.000+00:00','f_20260405_2_001',0.9353,1,19.6,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-05/f0001.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-05T16:00:40.000+00:00','f_20260405_2_002',0.6939,0,16.2,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-05/f0002.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-05T19:53:02.000+00:00','f_20260405_2_003',0.9372,1,16.5,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-05/f0003.jpg','ATOM-DETECT-YOLO-V1','v1.2.0');

INSERT INTO ir_scene_steel_001 VALUES
  ('2026-04-05T06:28:47.000+00:00','f_20260405_3_000',0.6641,0,23.8,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-05/f0000.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-05T10:04:23.000+00:00','f_20260405_3_001',0.9662,1,24.1,'铸坯横裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-05/f0001.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-05T11:44:52.000+00:00','f_20260405_3_002',0.9748,2,21.1,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-05/f0002.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-05T15:23:51.000+00:00','f_20260405_3_003',0.7174,0,20.3,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-05/f0003.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-05T18:12:55.000+00:00','f_20260405_3_004',0.9279,1,21.7,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-05/f0004.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-05T20:41:35.000+00:00','f_20260405_3_005',0.7053,0,19.6,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-05/f0005.jpg','HEAD-BILLET-CRACK-V1','v1.1.0');

INSERT INTO ir_scene_section_001 VALUES
  ('2026-04-05T07:17:29.000+00:00','f_20260405_4_000',0.7024,0,16.2,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-05/f0000.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-05T10:54:40.000+00:00','f_20260405_4_001',0.8373,2,16.1,'折叠缺陷',NULL,'minio://tianjing-frames-prod/section/2026-04-05/f0001.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-05T14:03:56.000+00:00','f_20260405_4_002',0.8725,2,18.2,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-05/f0002.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-05T19:33:40.000+00:00','f_20260405_4_003',0.9655,2,16.5,'折叠缺陷',NULL,'minio://tianjing-frames-prod/section/2026-04-05/f0003.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0');

INSERT INTO ir_scene_strip_001 VALUES
  ('2026-04-05T07:18:08.000+00:00','f_20260405_5_000',0.6526,0,11.6,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-05/f0000.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-05T11:42:15.000+00:00','f_20260405_5_001',0.8417,2,8.6,'带钢板形不良',NULL,'minio://tianjing-frames-prod/strip/2026-04-05/f0001.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-05T18:48:47.000+00:00','f_20260405_5_002',0.9064,1,9.3,'飞剪时序异常',NULL,'minio://tianjing-frames-prod/strip/2026-04-05/f0002.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0');

INSERT INTO ir_scene_pellet_001 VALUES
  ('2026-04-06T06:34:56.000+00:00','f_20260406_0_000',0.6569,0,19.2,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-06/f0000.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-06T07:39:17.000+00:00','f_20260406_0_001',0.6759,0,18.9,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-06/f0001.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-06T09:25:54.000+00:00','f_20260406_0_002',0.7187,0,18.5,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-06/f0002.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-06T10:43:41.000+00:00','f_20260406_0_003',0.6779,0,17.5,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-06/f0003.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-06T11:25:54.000+00:00','f_20260406_0_004',0.8347,2,17.7,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-06/f0004.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-06T13:10:01.000+00:00','f_20260406_0_005',0.6733,0,21.9,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-06/f0005.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-06T14:37:13.000+00:00','f_20260406_0_006',0.7036,0,17.6,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-06/f0006.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-06T15:30:55.000+00:00','f_20260406_0_007',0.7042,0,20.8,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-06/f0007.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-06T17:08:17.000+00:00','f_20260406_0_008',0.8432,2,17.2,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-06/f0008.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-06T18:12:11.000+00:00','f_20260406_0_009',0.7107,0,17.2,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-06/f0009.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-06T19:37:50.000+00:00','f_20260406_0_010',0.6727,0,17.5,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-06/f0010.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-06T20:42:47.000+00:00','f_20260406_0_011',0.658,0,16.8,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-06/f0011.jpg','HEAD-SIDEPLATE-V1','v1.2.0');

INSERT INTO ir_scene_sinter_001 VALUES
  ('2026-04-06T06:18:23.000+00:00','f_20260406_1_000',0.8374,1,20.0,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0000.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T07:47:02.000+00:00','f_20260406_1_001',0.9654,1,20.8,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0001.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T08:29:41.000+00:00','f_20260406_1_002',0.8297,3,19.8,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0002.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T09:42:43.000+00:00','f_20260406_1_003',0.6999,0,19.6,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0003.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T11:11:20.000+00:00','f_20260406_1_004',0.8305,1,18.7,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0004.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T12:08:35.000+00:00','f_20260406_1_005',0.8809,1,20.5,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0005.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T13:03:32.000+00:00','f_20260406_1_006',0.8359,1,21.1,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0006.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T14:35:41.000+00:00','f_20260406_1_007',0.9082,2,22.0,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0007.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T15:26:56.000+00:00','f_20260406_1_008',0.6737,0,19.7,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0008.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T16:34:06.000+00:00','f_20260406_1_009',0.6719,0,18.6,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0009.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T18:04:43.000+00:00','f_20260406_1_010',0.8307,2,21.8,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0010.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T18:38:06.000+00:00','f_20260406_1_011',0.8796,1,17.9,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0011.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T19:45:35.000+00:00','f_20260406_1_012',0.9272,1,19.5,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0012.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-06T21:27:48.000+00:00','f_20260406_1_013',0.7141,0,17.5,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0013.jpg','HEAD-SINTER-GRATE-V1','v1.0.0');

INSERT INTO ir_scene_sinter_002 VALUES
  ('2026-04-06T06:16:19.000+00:00','f_20260406_2_000',0.6905,0,17.2,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0000.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-06T08:13:14.000+00:00','f_20260406_2_001',0.876,2,19.1,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0001.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-06T09:18:17.000+00:00','f_20260406_2_002',0.9492,1,20.6,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0002.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-06T11:44:52.000+00:00','f_20260406_2_003',0.928,1,19.1,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0003.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-06T12:59:42.000+00:00','f_20260406_2_004',0.8628,2,17.0,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0004.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-06T14:40:19.000+00:00','f_20260406_2_005',0.8762,2,17.0,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0005.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-06T15:44:42.000+00:00','f_20260406_2_006',0.6599,0,19.5,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0006.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-06T17:13:47.000+00:00','f_20260406_2_007',0.8802,1,18.5,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0007.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-06T18:57:19.000+00:00','f_20260406_2_008',0.9443,1,19.1,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0008.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-06T20:28:03.000+00:00','f_20260406_2_009',0.7028,0,16.3,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-06/f0009.jpg','ATOM-DETECT-YOLO-V1','v1.2.0');

INSERT INTO ir_scene_steel_001 VALUES
  ('2026-04-06T06:03:28.000+00:00','f_20260406_3_000',0.8977,1,21.9,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0000.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-06T07:25:48.000+00:00','f_20260406_3_001',0.8752,1,20.1,'铸坯横裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0001.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-06T08:57:37.000+00:00','f_20260406_3_002',0.9267,1,19.7,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0002.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-06T09:59:38.000+00:00','f_20260406_3_003',0.6643,0,24.7,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0003.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-06T11:29:48.000+00:00','f_20260406_3_004',0.673,0,19.5,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0004.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-06T12:42:11.000+00:00','f_20260406_3_005',0.6873,0,20.8,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0005.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-06T13:47:42.000+00:00','f_20260406_3_006',0.8315,2,24.2,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0006.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-06T14:38:01.000+00:00','f_20260406_3_007',0.8325,1,21.2,'铸坯横裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0007.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-06T16:09:25.000+00:00','f_20260406_3_008',0.8868,1,20.1,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0008.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-06T17:05:20.000+00:00','f_20260406_3_009',0.7163,0,22.0,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0009.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-06T18:49:23.000+00:00','f_20260406_3_010',0.7091,0,20.8,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0010.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-06T19:58:33.000+00:00','f_20260406_3_011',0.7109,0,23.6,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0011.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-06T20:55:49.000+00:00','f_20260406_3_012',0.6857,0,20.1,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-06/f0012.jpg','HEAD-BILLET-CRACK-V1','v1.1.0');

INSERT INTO ir_scene_section_001 VALUES
  ('2026-04-06T06:29:57.000+00:00','f_20260406_4_000',0.8762,1,20.1,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-06/f0000.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-06T08:13:09.000+00:00','f_20260406_4_001',0.6603,0,15.9,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-06/f0001.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-06T09:46:48.000+00:00','f_20260406_4_002',0.8321,1,20.0,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-06/f0002.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-06T11:24:29.000+00:00','f_20260406_4_003',0.9416,1,17.3,'折叠缺陷',NULL,'minio://tianjing-frames-prod/section/2026-04-06/f0003.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-06T13:17:36.000+00:00','f_20260406_4_004',0.9778,1,16.6,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-06/f0004.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-06T14:55:20.000+00:00','f_20260406_4_005',0.6548,0,17.2,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-06/f0005.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-06T15:53:17.000+00:00','f_20260406_4_006',0.8763,1,15.2,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-06/f0006.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-06T17:38:02.000+00:00','f_20260406_4_007',0.7082,0,15.2,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-06/f0007.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-06T19:37:54.000+00:00','f_20260406_4_008',0.6916,0,16.9,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-06/f0008.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-06T20:57:50.000+00:00','f_20260406_4_009',0.8266,1,19.8,'折叠缺陷',NULL,'minio://tianjing-frames-prod/section/2026-04-06/f0009.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0');

INSERT INTO ir_scene_strip_001 VALUES
  ('2026-04-06T06:54:12.000+00:00','f_20260406_5_000',0.6833,0,6.9,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-06/f0000.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-06T08:45:06.000+00:00','f_20260406_5_001',0.954,2,11.5,'带钢板形不良',NULL,'minio://tianjing-frames-prod/strip/2026-04-06/f0001.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-06T11:28:41.000+00:00','f_20260406_5_002',0.6673,0,8.5,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-06/f0002.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-06T14:42:33.000+00:00','f_20260406_5_003',0.8782,1,11.1,'带钢板形不良',NULL,'minio://tianjing-frames-prod/strip/2026-04-06/f0003.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-06T17:17:49.000+00:00','f_20260406_5_004',0.6919,0,10.4,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-06/f0004.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-06T20:22:04.000+00:00','f_20260406_5_005',0.6891,0,8.2,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-06/f0005.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0');

INSERT INTO ir_scene_pellet_001 VALUES
  ('2026-04-07T06:11:54.000+00:00','f_20260407_0_000',0.965,1,21.3,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0000.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-07T07:28:37.000+00:00','f_20260407_0_001',0.9667,1,18.2,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0001.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-07T08:56:42.000+00:00','f_20260407_0_002',0.6917,0,21.7,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0002.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-07T09:55:24.000+00:00','f_20260407_0_003',0.8726,1,17.2,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0003.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-07T11:26:47.000+00:00','f_20260407_0_004',0.966,3,17.0,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0004.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-07T12:38:32.000+00:00','f_20260407_0_005',0.6754,0,17.5,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0005.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-07T13:32:49.000+00:00','f_20260407_0_006',0.6659,0,17.3,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0006.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-07T14:40:54.000+00:00','f_20260407_0_007',0.9012,1,19.5,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0007.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-07T16:23:33.000+00:00','f_20260407_0_008',0.6736,0,20.7,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0008.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-07T17:10:24.000+00:00','f_20260407_0_009',0.6627,0,20.3,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0009.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-07T18:46:57.000+00:00','f_20260407_0_010',0.6806,0,18.8,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0010.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-07T20:02:15.000+00:00','f_20260407_0_011',0.9177,3,20.6,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0011.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-07T21:20:14.000+00:00','f_20260407_0_012',0.6717,0,20.9,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-07/f0012.jpg','HEAD-SIDEPLATE-V1','v1.2.0');

INSERT INTO ir_scene_sinter_001 VALUES
  ('2026-04-07T06:16:53.000+00:00','f_20260407_1_000',0.6975,0,20.3,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0000.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T07:15:49.000+00:00','f_20260407_1_001',0.6767,0,20.1,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0001.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T08:34:52.000+00:00','f_20260407_1_002',0.6923,0,22.0,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0002.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T09:01:30.000+00:00','f_20260407_1_003',0.9087,2,17.8,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0003.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T10:30:54.000+00:00','f_20260407_1_004',0.6559,0,18.1,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0004.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T11:16:32.000+00:00','f_20260407_1_005',0.6806,0,22.4,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0005.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T12:07:28.000+00:00','f_20260407_1_006',0.9689,2,19.5,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0006.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T13:14:23.000+00:00','f_20260407_1_007',0.7196,0,19.3,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0007.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T14:01:59.000+00:00','f_20260407_1_008',0.7,0,19.0,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0008.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T15:05:02.000+00:00','f_20260407_1_009',0.7081,0,21.0,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0009.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T16:04:45.000+00:00','f_20260407_1_010',0.6931,0,22.2,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0010.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T17:00:13.000+00:00','f_20260407_1_011',0.9078,1,21.2,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0011.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T18:18:07.000+00:00','f_20260407_1_012',0.858,1,17.9,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0012.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T19:08:40.000+00:00','f_20260407_1_013',0.8931,1,17.8,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0013.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T20:32:38.000+00:00','f_20260407_1_014',0.6942,0,20.4,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0014.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-07T21:25:18.000+00:00','f_20260407_1_015',0.693,0,18.5,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0015.jpg','HEAD-SINTER-GRATE-V1','v1.0.0');

INSERT INTO ir_scene_sinter_002 VALUES
  ('2026-04-07T06:21:42.000+00:00','f_20260407_2_000',0.9038,1,18.0,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0000.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-07T08:17:34.000+00:00','f_20260407_2_001',0.7192,0,19.0,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0001.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-07T09:14:30.000+00:00','f_20260407_2_002',0.6771,0,18.1,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0002.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-07T11:00:22.000+00:00','f_20260407_2_003',0.8752,1,20.0,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0003.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-07T12:21:49.000+00:00','f_20260407_2_004',0.703,0,16.7,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0004.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-07T13:34:21.000+00:00','f_20260407_2_005',0.848,1,20.3,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0005.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-07T15:07:44.000+00:00','f_20260407_2_006',0.6628,0,16.7,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0006.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-07T16:48:29.000+00:00','f_20260407_2_007',0.6641,0,20.4,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0007.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-07T18:24:20.000+00:00','f_20260407_2_008',0.9025,2,21.4,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0008.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-07T19:33:16.000+00:00','f_20260407_2_009',0.6953,0,17.6,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0009.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-07T21:17:03.000+00:00','f_20260407_2_010',0.8731,2,16.7,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-07/f0010.jpg','ATOM-DETECT-YOLO-V1','v1.2.0');

INSERT INTO ir_scene_steel_001 VALUES
  ('2026-04-07T06:15:56.000+00:00','f_20260407_3_000',0.6808,0,24.8,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0000.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T07:22:11.000+00:00','f_20260407_3_001',0.6848,0,24.6,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0001.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T08:38:51.000+00:00','f_20260407_3_002',0.6797,0,21.9,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0002.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T10:06:11.000+00:00','f_20260407_3_003',0.6725,0,19.9,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0003.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T11:01:19.000+00:00','f_20260407_3_004',0.7002,0,20.4,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0004.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T12:01:28.000+00:00','f_20260407_3_005',0.7109,0,21.4,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0005.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T13:32:17.000+00:00','f_20260407_3_006',0.9466,1,19.8,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0006.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T14:27:25.000+00:00','f_20260407_3_007',0.7061,0,23.2,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0007.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T15:47:09.000+00:00','f_20260407_3_008',0.9285,2,21.9,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0008.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T16:22:15.000+00:00','f_20260407_3_009',0.8786,1,21.6,'铸坯横裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0009.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T17:49:38.000+00:00','f_20260407_3_010',0.6815,0,21.5,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0010.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T18:52:04.000+00:00','f_20260407_3_011',0.6871,0,21.7,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0011.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T20:09:15.000+00:00','f_20260407_3_012',0.8241,3,20.5,'铸坯纵裂',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0012.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-07T21:12:58.000+00:00','f_20260407_3_013',0.6683,0,20.9,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-07/f0013.jpg','HEAD-BILLET-CRACK-V1','v1.1.0');

INSERT INTO ir_scene_section_001 VALUES
  ('2026-04-07T06:23:39.000+00:00','f_20260407_4_000',0.7142,0,17.5,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-07/f0000.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-07T07:33:54.000+00:00','f_20260407_4_001',0.6621,0,18.7,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-07/f0001.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-07T09:46:51.000+00:00','f_20260407_4_002',0.9553,1,16.6,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-07/f0002.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-07T10:40:04.000+00:00','f_20260407_4_003',0.6703,0,16.4,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-07/f0003.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-07T11:57:45.000+00:00','f_20260407_4_004',0.922,2,19.2,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-07/f0004.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-07T13:43:07.000+00:00','f_20260407_4_005',0.9467,3,16.1,'折叠缺陷',NULL,'minio://tianjing-frames-prod/section/2026-04-07/f0005.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-07T15:09:23.000+00:00','f_20260407_4_006',0.6758,0,19.2,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-07/f0006.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-07T16:39:39.000+00:00','f_20260407_4_007',0.8305,2,17.0,'折叠缺陷',NULL,'minio://tianjing-frames-prod/section/2026-04-07/f0007.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-07T18:15:30.000+00:00','f_20260407_4_008',0.7038,0,19.3,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-07/f0008.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-07T19:09:20.000+00:00','f_20260407_4_009',0.892,1,18.5,'折叠缺陷',NULL,'minio://tianjing-frames-prod/section/2026-04-07/f0009.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-07T20:50:56.000+00:00','f_20260407_4_010',0.9082,1,18.0,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-07/f0010.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0');

INSERT INTO ir_scene_strip_001 VALUES
  ('2026-04-07T07:03:08.000+00:00','f_20260407_5_000',0.7137,0,6.4,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-07/f0000.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-07T08:27:09.000+00:00','f_20260407_5_001',0.6613,0,11.2,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-07/f0001.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-07T10:52:33.000+00:00','f_20260407_5_002',0.9553,2,7.5,'飞剪时序异常',NULL,'minio://tianjing-frames-prod/strip/2026-04-07/f0002.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-07T13:43:35.000+00:00','f_20260407_5_003',0.6938,0,9.5,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-07/f0003.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-07T15:16:41.000+00:00','f_20260407_5_004',0.7159,0,6.9,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-07/f0004.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-07T18:19:46.000+00:00','f_20260407_5_005',0.874,2,11.2,'带钢板形不良',NULL,'minio://tianjing-frames-prod/strip/2026-04-07/f0005.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-07T19:46:55.000+00:00','f_20260407_5_006',0.6532,0,9.6,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-07/f0006.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0');

INSERT INTO ir_scene_pellet_001 VALUES
  ('2026-04-08T06:46:41.000+00:00','f_20260408_0_000',0.69,0,19.9,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-08/f0000.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-08T08:17:56.000+00:00','f_20260408_0_001',0.8663,1,21.8,'侧板变形',NULL,'minio://tianjing-frames-prod/pellet/2026-04-08/f0001.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-08T11:09:51.000+00:00','f_20260408_0_002',0.885,1,19.0,'侧板跑偏',NULL,'minio://tianjing-frames-prod/pellet/2026-04-08/f0002.jpg','HEAD-SIDEPLATE-V1','v1.2.0'),
  ('2026-04-08T13:40:10.000+00:00','f_20260408_0_003',0.6839,0,17.6,'正常',NULL,'minio://tianjing-frames-prod/pellet/2026-04-08/f0003.jpg','HEAD-SIDEPLATE-V1','v1.2.0');

INSERT INTO ir_scene_sinter_001 VALUES
  ('2026-04-08T06:39:20.000+00:00','f_20260408_1_000',0.8711,2,22.6,'壁条脱落',NULL,'minio://tianjing-frames-prod/sinter/2026-04-08/f0000.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-08T08:10:25.000+00:00','f_20260408_1_001',0.6759,0,20.3,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-08/f0001.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-08T09:56:29.000+00:00','f_20260408_1_002',0.6815,0,18.0,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-08/f0002.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-08T11:27:24.000+00:00','f_20260408_1_003',0.8869,2,22.5,'壁条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-08/f0003.jpg','HEAD-SINTER-GRATE-V1','v1.0.0'),
  ('2026-04-08T14:10:52.000+00:00','f_20260408_1_004',0.6904,0,18.9,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-08/f0004.jpg','HEAD-SINTER-GRATE-V1','v1.0.0');

INSERT INTO ir_scene_sinter_002 VALUES
  ('2026-04-08T07:01:31.000+00:00','f_20260408_2_000',0.8947,3,20.8,'篦条断裂',NULL,'minio://tianjing-frames-prod/sinter/2026-04-08/f0000.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-08T08:55:05.000+00:00','f_20260408_2_001',0.9009,2,21.3,'篦条缺损',NULL,'minio://tianjing-frames-prod/sinter/2026-04-08/f0001.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-08T10:47:22.000+00:00','f_20260408_2_002',0.6697,0,19.3,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-08/f0002.jpg','ATOM-DETECT-YOLO-V1','v1.2.0'),
  ('2026-04-08T13:53:43.000+00:00','f_20260408_2_003',0.6876,0,20.7,'正常',NULL,'minio://tianjing-frames-prod/sinter/2026-04-08/f0003.jpg','ATOM-DETECT-YOLO-V1','v1.2.0');

INSERT INTO ir_scene_steel_001 VALUES
  ('2026-04-08T06:27:56.000+00:00','f_20260408_3_000',0.6756,0,21.7,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-08/f0000.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-08T07:50:03.000+00:00','f_20260408_3_001',0.6636,0,22.5,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-08/f0001.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-08T09:40:32.000+00:00','f_20260408_3_002',0.6885,0,24.0,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-08/f0002.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-08T12:04:39.000+00:00','f_20260408_3_003',0.7183,0,21.7,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-08/f0003.jpg','HEAD-BILLET-CRACK-V1','v1.1.0'),
  ('2026-04-08T14:03:21.000+00:00','f_20260408_3_004',0.6756,0,20.3,'正常',NULL,'minio://tianjing-frames-prod/steel/2026-04-08/f0004.jpg','HEAD-BILLET-CRACK-V1','v1.1.0');

INSERT INTO ir_scene_section_001 VALUES
  ('2026-04-08T06:48:25.000+00:00','f_20260408_4_000',0.6532,0,15.6,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-08/f0000.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-08T08:41:30.000+00:00','f_20260408_4_001',0.6604,0,19.7,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-08/f0001.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-08T10:41:59.000+00:00','f_20260408_4_002',0.846,1,20.2,'钢材划痕',NULL,'minio://tianjing-frames-prod/section/2026-04-08/f0002.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0'),
  ('2026-04-08T13:44:19.000+00:00','f_20260408_4_003',0.6855,0,17.6,'正常',NULL,'minio://tianjing-frames-prod/section/2026-04-08/f0003.jpg','HEAD-STEEL-SURFACE-V1','v1.0.0');

INSERT INTO ir_scene_strip_001 VALUES
  ('2026-04-08T06:52:36.000+00:00','f_20260408_5_000',0.7071,0,8.1,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-08/f0000.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-08T10:12:26.000+00:00','f_20260408_5_001',0.7058,0,10.1,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-08/f0001.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0'),
  ('2026-04-08T13:14:01.000+00:00','f_20260408_5_002',0.6686,0,9.7,'正常',NULL,'minio://tianjing-frames-prod/strip/2026-04-08/f0002.jpg','HEAD-STRIP-SHEAR-V1','v1.0.0');
