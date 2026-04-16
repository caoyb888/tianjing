-- 离线仿真模块种子数据
-- 用途：本地开发环境测试 Phase 1 导出引擎（含 result_json 的已完成仿真任务）
-- 执行前提：已执行 V5__sim_dataset_export.sql 迁移，MinIO tianjing-sim-temp/simulation/SIM-TEST-0001/frames/ 需有测试帧图像
-- 执行方式：docker exec -i <postgres_container> psql -U postgres -d tianjing_prod -f - < scripts/seed_simulation.sql

INSERT INTO simulation_task (
    task_id, scene_id, task_name, video_file_url,
    workflow_json, algo_config_json,
    status, total_frames, matched_alarms, result_json,
    created_by, updated_by, version
) VALUES (
    'SIM-TEST-0001',
    'SCENE-SINTER-FIRE-001',
    'sinter_fire_test.mp4',
    'http://localhost:9000/tianjing-sim-temp/simulation/SCENE-SINTER-FIRE-001/test/video.mp4',
    '{}',
    '{"pluginId":"HEAD-MATERIAL-LEVEL-V1"}',
    'COMPLETED',
    3,
    2,
    '{
        "frames": [
            {
                "frameId": "frame_000001",
                "frameUrl": "http://localhost:9000/tianjing-sim-temp/simulation/SIM-TEST-0001/frames/frame_000001.jpg",
                "timestampMs": 1000,
                "detections": [
                    {"classId": 1, "className": "material_level", "confidence": 0.92,
                     "bbox": {"x1": 120, "y1": 80, "x2": 560, "y2": 320}}
                ]
            },
            {
                "frameId": "frame_000002",
                "frameUrl": "http://localhost:9000/tianjing-sim-temp/simulation/SIM-TEST-0001/frames/frame_000002.jpg",
                "timestampMs": 2000,
                "detections": [
                    {"classId": 1, "className": "material_level", "confidence": 0.88,
                     "bbox": {"x1": 100, "y1": 60, "x2": 520, "y2": 300}},
                    {"classId": 1, "className": "material_level", "confidence": 0.55,
                     "bbox": {"x1": 600, "y1": 200, "x2": 900, "y2": 400}}
                ]
            },
            {
                "frameId": "frame_000003",
                "frameUrl": "http://localhost:9000/tianjing-sim-temp/simulation/SIM-TEST-0001/frames/frame_000003.jpg",
                "timestampMs": 3000,
                "detections": []
            }
        ],
        "pluginId": "HEAD-MATERIAL-LEVEL-V1",
        "totalFrames": 3,
        "detectionFrames": 2
    }',
    'admin',
    'admin',
    0
) ON CONFLICT DO NOTHING;
