-- V7: algorithm_plugin 新增算法简要描述与适合业务维度两列
-- 对应 docs/工业视觉 AI 推理平台算法选型推荐.md

ALTER TABLE algorithm_plugin
    ADD COLUMN IF NOT EXISTS description      VARCHAR(256) NULL,
    ADD COLUMN IF NOT EXISTS business_dimension VARCHAR(32)  NULL;

COMMENT ON COLUMN algorithm_plugin.description       IS '算法简要描述（原理、特点、适用条件）';
COMMENT ON COLUMN algorithm_plugin.business_dimension IS '适合的业务维度：设备状态监测 / 工艺参数监控 / 质量检测 / 通用';

-- ── 更新现有 14 条记录 ───────────────────────────────────────────────────────

UPDATE algorithm_plugin SET
    description       = '云端推理代理，透明转发推理请求至后端云API或本地ONNX模型，无需修改平台代码即可切换后端',
    business_dimension = '通用'
WHERE plugin_id = 'CLOUD-PROXY-V1';

UPDATE algorithm_plugin SET
    description       = '基于YOLOv8n的通用目标检测原子算法，支持多类别实时目标定位与边界框回归',
    business_dimension = '通用'
WHERE plugin_id = 'ATOM-DETECT-YOLO-V1';

UPDATE algorithm_plugin SET
    description       = 'Segment Anything Model通用语义分割，零样本泛化能力强，适合复杂轮廓提取与少样本微调',
    business_dimension = '通用'
WHERE plugin_id = 'ATOM-SEGMENT-SAM-V1';

UPDATE algorithm_plugin SET
    description       = 'ResNet基础图像分类器，轻量稳定，适合多状态离散分类场景，可在边缘端部署',
    business_dimension = '通用'
WHERE plugin_id = 'ATOM-CLASSIFY-RESNET-V1';

UPDATE algorithm_plugin SET
    description       = '亚像素Canny边缘检测与几何尺寸测量，结合相机标定满足毫米级精度要求',
    business_dimension = '质量检测'
WHERE plugin_id = 'ATOM-MEASURE-SUBPIXEL-V1';

UPDATE algorithm_plugin SET
    description       = '基于暗通道先验(DCP)的图像去雾增强，改善烟气/粉尘遮挡场景的推理前置处理效果',
    business_dimension = '工艺参数监控'
WHERE plugin_id = 'ATOM-ENHANCE-DEHAZE-V1';

UPDATE algorithm_plugin SET
    description       = 'RT-DETR端到端高精度检测结合亚像素边缘提取，计算侧板偏移量满足10mm精度要求',
    business_dimension = '设备状态监测'
WHERE plugin_id = 'HEAD-SIDEPLATE-V1';

UPDATE algorithm_plugin SET
    description       = 'YOLOv9运动目标检测，适用于135部台车连续运动中篦条缺损的快速抓拍与定位',
    business_dimension = '设备状态监测'
WHERE plugin_id = 'HEAD-GRATE-BAR-V1';

UPDATE algorithm_plugin SET
    description       = 'YOLOv9多任务头多角度相机协同，检测翼缘弯曲、表面裂纹等成品缺陷，降低误报率',
    business_dimension = '质量检测'
WHERE plugin_id = 'HEAD-STEEL-SURFACE-V1';

UPDATE algorithm_plugin SET
    description       = 'RT-DETR检测铸坯裂纹/夹渣/气泡等多形态表面缺陷，具备良好的多尺度鲁棒性',
    business_dimension = '质量检测'
WHERE plugin_id = 'HEAD-BILLET-CRACK-V1';

UPDATE algorithm_plugin SET
    description       = 'YOLOv9-Seg实例分割精准提取皮带物料轮廓面积，结合相机标定估算料面高度与堵料趋势',
    business_dimension = '工艺参数监控'
WHERE plugin_id = 'HEAD-MATERIAL-LEVEL-V1';

UPDATE algorithm_plugin SET
    description       = 'EfficientNetV2轻量分类网络，判别分料器开/关/半开三种运行状态，推理极快适合边缘部署',
    business_dimension = '设备状态监测'
WHERE plugin_id = 'HEAD-FEEDER-STATE-V1';

UPDATE algorithm_plugin SET
    description       = 'CSRNet密度估计网络将渣粒画面映射为密度图，量化渣粒浓度以指导溅渣枪位控制',
    business_dimension = '工艺参数监控'
WHERE plugin_id = 'HEAD-SLAG-DENSITY-V1';

UPDATE algorithm_plugin SET
    description       = '线结构光3D点云算法结合亚像素2D边缘拟合，满足高精度钢材截面尺寸在线测量',
    business_dimension = '质量检测'
WHERE plugin_id = 'HEAD-DIMENSION-V1';

-- ── 新增 7 条尚无对应记录的场景算法 ──────────────────────────────────────────

INSERT INTO algorithm_plugin
    (plugin_id, plugin_name, plugin_type, is_atom, version, backbone,
     infer_backend, status, description, business_dimension, metadata_json, ui_schema_json,
     created_by, updated_by)
VALUES
-- 场景4：烧结 看火系统 — 点火料面（点火强度）
(
    'HEAD-SINTER-IGNITION-V1',
    '烧结点火料面检测头',
    'ENHANCEMENT',
    false,
    '1.0.0',
    'Retinex',
    'LOCAL_GPU',
    'REGISTERED',
    'Retinex算法先抑制强光/光晕干扰，再利用分割模型提取有效燃烧面积，量化点火强度指标',
    '工艺参数监控',
    '{
      "supported_scenes": ["烧结机点火强度监测","看火系统点火料面识别"],
      "hardware_requirements": {"min_gpu_vram_gb": 4, "supports_tensorrt": true, "supports_onnx": true},
      "accuracy_metrics": {"inference_ms_gpu": 25, "inference_ms_cpu": 180}
    }',
    '{}',
    'system',
    'system'
),
-- 场景5：烧结机 — 壁条脱落/堵塞/翘起
(
    'HEAD-SINTER-GRATE-V1',
    '烧结壁条异常检测头',
    'DETECTION',
    false,
    '1.0.0',
    'YOLOv9',
    'LOCAL_GPU',
    'REGISTERED',
    'YOLOv9多尺度检测结合连续帧确认逻辑，有效抵抗粉尘干扰，快速检出脱落或翘起的细小壁条目标',
    '设备状态监测',
    '{
      "supported_scenes": ["烧结机壁条脱落检测","烧结机壁条堵塞检测","烧结机壁条翘起检测"],
      "hardware_requirements": {"min_gpu_vram_gb": 4, "supports_tensorrt": true, "supports_onnx": true},
      "accuracy_metrics": {"map50": 0.88, "map50_95": 0.72, "inference_ms_gpu": 20, "inference_ms_cpu": 220}
    }',
    '{}',
    'system',
    'system'
),
-- 场景6：烧结 二配室 — 白灰/燃料仓粒度
(
    'HEAD-SINTER-PARTICLE-V1',
    '物料粒度分割检测头',
    'SEGMENTATION',
    false,
    '1.0.0',
    'SAM',
    'LOCAL_GPU',
    'REGISTERED',
    'SAM大模型微调后对堆叠白灰和燃料进行高精度单体分割，计算粒径分布，零样本泛化能力极强',
    '工艺参数监控',
    '{
      "supported_scenes": ["二配室白灰粒度检测","燃料仓燃料粒度检测"],
      "hardware_requirements": {"min_gpu_vram_gb": 8, "supports_tensorrt": false, "supports_onnx": true},
      "accuracy_metrics": {"inference_ms_gpu": 45, "inference_ms_cpu": 400}
    }',
    '{}',
    'system',
    'system'
),
-- 场景9：炼钢 吹氩站 — 底吹处理
(
    'HEAD-STEEL-ARGON-V1',
    '转炉底吹处理检测头',
    'ENHANCEMENT',
    false,
    '1.0.0',
    'DCP+DeepLab',
    'LOCAL_GPU',
    'REGISTERED',
    '暗通道先验(DCP)或深度学习去雾算法去除强烟气遮挡，再用分割网络识别钢水裸露面积以调整氩气流量',
    '工艺参数监控',
    '{
      "supported_scenes": ["转炉底吹处理监测","吹氩站钢水面积识别"],
      "hardware_requirements": {"min_gpu_vram_gb": 6, "supports_tensorrt": true, "supports_onnx": true},
      "accuracy_metrics": {"inference_ms_gpu": 30, "inference_ms_cpu": 250}
    }',
    '{}',
    'system',
    'system'
),
-- 场景11：型钢 加热炉 — 铸坯质量
(
    'HEAD-SECTION-BILLET-V1',
    '型钢铸坯质量检测头',
    'DETECTION',
    false,
    '1.0.0',
    'YOLOv9',
    'LOCAL_GPU',
    'REGISTERED',
    'YOLOv9增强FPN多尺度特征融合，与炼钢铸坯场景共享主干网络，重点抵抗氧化铁皮带来的纹理干扰',
    '质量检测',
    '{
      "supported_scenes": ["型钢加热炉铸坯质量检测","铸坯表面氧化缺陷识别"],
      "hardware_requirements": {"min_gpu_vram_gb": 4, "supports_tensorrt": true, "supports_onnx": true},
      "accuracy_metrics": {"map50": 0.87, "map50_95": 0.71, "inference_ms_gpu": 22, "inference_ms_cpu": 230}
    }',
    '{}',
    'system',
    'system'
),
-- 场景15：带钢 飞剪 — 飞剪优化剪切（<10ms 极速闭环）
(
    'HEAD-STRIP-SHEAR-V1',
    '带钢飞剪关键点检测头',
    'DETECTION',
    false,
    '1.0.0',
    'RTMPose',
    'LOCAL_GPU',
    'REGISTERED',
    '轻量级关键点检测网络精准定位带钢头部形状与坐标，端到端闭环<10ms，配合热检计算切头长度',
    '工艺参数监控',
    '{
      "supported_scenes": ["带钢飞剪优化剪切","带钢头部定位与切头计算"],
      "hardware_requirements": {"min_gpu_vram_gb": 2, "supports_tensorrt": true, "supports_onnx": true},
      "accuracy_metrics": {"inference_ms_gpu": 8, "inference_ms_cpu": 45}
    }',
    '{}',
    'system',
    'system'
),
-- 场景16：带钢 卷取 — 端面质检
(
    'HEAD-STRIP-COIL-V1',
    '带钢卷取端面质检头',
    'CLASSIFICATION',
    false,
    '1.0.0',
    'PaDiM',
    'ONNX_CPU',
    'REGISTERED',
    'ResNet50/PaDiM无监督异常检测，对端面折叠、塔形等全局性特征提供比目标检测更稳定的判别效果',
    '质量检测',
    '{
      "supported_scenes": ["带钢卷取端面质检","卷取端面折叠检测","塔形缺陷检测"],
      "hardware_requirements": {"min_gpu_vram_gb": 2, "supports_tensorrt": false, "supports_onnx": true},
      "accuracy_metrics": {"inference_ms_gpu": 15, "inference_ms_cpu": 80}
    }',
    '{}',
    'system',
    'system'
);
