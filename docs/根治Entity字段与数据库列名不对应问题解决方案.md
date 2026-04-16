# 根治 Entity 字段与数据库列名不对应问题解决方案

> **编制日期**：2026-04-02
> **背景**：项目开发过程中多次出现 MyBatis-Plus Entity 字段名与 PostgreSQL 列名不匹配，
> 导致运行时 `column "xxx" does not exist` 或字段静默返回 NULL，累计影响超过 10 个服务、100+ 个字段。

---

## 一、根本原因分析

这不是偶发的粗心问题，而是**开发流程缺少单一事实来源（Single Source of Truth）**的系统性缺陷。具体有三个层面。

### 1.1 Entity 和 SQL Migration 各自独立创建

本项目中，Flyway SQL（`V1__init_tianjing_prod.sql`）和 Java Entity 是分开编写的，没有一方生成另一方。两边都用"我觉得合理的名字"命名，结果出现大量偏差：

| Flyway SQL 列名 | Entity 字段名 | MyBatis-Plus 自动推导 | 实际结果 |
|---|---|---|---|
| `version_id` | `modelVersionId` | `model_version_id` | 对不上，静默失败 |
| `pixel_p1_x` | `pt1X` | `pt1_x` | 对不上，静默失败 |
| `finished_at` | `completedAt` | `completed_at` | 对不上，静默失败 |
| `ref_distance_mm` | `refLengthMm` | `ref_length_mm` | 对不上，静默失败 |
| `train_job_id` | `trainingJobId` | `training_job_id` | 对不上，静默失败 |
| `sync_status` | `status` | `status` | 偶然对上（字段语义已变） |

MyBatis-Plus 对不上的字段**不报错、不警告，直接返回 NULL 或静默忽略**，只有运行时查询结果异常才能发现。

### 1.2 所有测试使用 Mock，永远不触发真实 SQL

项目里所有 Service 层测试均为：

```java
@ExtendWith(MockitoExtension.class)
when(mapper.selectOne(any())).thenReturn(mockEntity);
```

这意味着：
- Entity 字段写错了 → 测试仍然通过（Mock 不经过 SQL 映射层）
- `column "xxx" does not exist` 只有真实 HTTP 请求才会暴露
- 测试通过率 100% 但生产环境 500 错误频发

### 1.3 缺少"Migration 联动 Entity 检查"的流程约束

CLAUDE.md 规范了很多开发行为，但没有要求：**每次写 Flyway SQL 时必须同步验证对应 Entity 的 `@TableField` 注解**。这个检查点的缺失，让两边各自演进，逐渐偏离。

---

## 二、解决方案

按优先级由高到低排列，建议同步推进方案一和方案二。

---

### 方案一（立即执行）：补全所有 `@TableField` 显式注解

**禁止依赖 MyBatis-Plus 的 camelCase → underscore 自动推导规则**。凡 Java 字段名与 DB 列名稍有不同，必须显式标注 `@TableField`。

**反例（当前问题根源）：**

```java
// ❌ 错误：依赖自动推导，ModelVersionId → model_version_id，但 DB 列名是 version_id
private String modelVersionId;

// ❌ 错误：依赖自动推导，pt1X → pt1_x，但 DB 列名是 pixel_p1_x
private Integer pt1X;
```

**正例（正确做法）：**

```java
// ✅ 正确：DB 列名与 Java 字段名不一致，必须显式标注
@TableField("version_id")
private String versionId;

@TableField("pixel_p1_x")
private Integer pixelP1X;

// ✅ 即使名字完全吻合，也建议写出，防止重命名时遗漏
@TableField("plugin_id")
private String pluginId;

// ✅ 非 DB 字段必须标注 exist = false，否则 INSERT/SELECT 报错
@TableField(exist = false)
private List<String> roles;
```

**执行标准**：每个 Entity 类的每个字段，必须是以下四种之一：
1. `@TableId` — 主键
2. `@TableField("col_name")` — 普通列，显式写出列名
3. `@TableField(exist = false)` — 非数据库字段
4. `@TableLogic` / `@Version` — 逻辑删除 / 乐观锁专用注解

---

### 方案二（近期落地）：每个服务增加 Entity 映射集成测试

在每个服务的 `src/test` 下新建 `EntityMappingIT.java`，连接真实 PostgreSQL（通过 Testcontainers 或共享测试库），验证所有 Entity 的 SQL 映射正确。

```java
// services/scene-config-service/src/test/.../EntityMappingIT.java

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Entity 字段映射集成测试（防止列名与 DB 不对应）")
class EntityMappingIT {

    @Autowired SceneConfigMapper sceneMapper;
    @Autowired AlgorithmPluginMapper pluginMapper;

    /**
     * 触发真实 SELECT，任何列名不对应立即抛出 BadSqlGrammarException。
     * 这是最低成本的防护手段，每次 Flyway Migration 后必须通过此测试。
     */
    @Test
    @DisplayName("SceneConfig — 所有 @TableField 列名与 DB 吻合")
    void sceneConfig_allFieldsMappedCorrectly() {
        assertThatNoException().isThrownBy(() ->
            sceneMapper.selectOne(
                new LambdaQueryWrapper<SceneConfig>().last("LIMIT 1"))
        );
    }

    @Test
    @DisplayName("AlgorithmPlugin — 所有 @TableField 列名与 DB 吻合")
    void algorithmPlugin_allFieldsMappedCorrectly() {
        assertThatNoException().isThrownBy(() ->
            pluginMapper.selectOne(
                new LambdaQueryWrapper<AlgorithmPlugin>().last("LIMIT 1"))
        );
    }

    @Test
    @DisplayName("SceneConfig — INSERT + SELECT 全字段往返，验证无 NULL 异常")
    void sceneConfig_insertAndSelect_noFieldLost() {
        SceneConfig config = buildTestSceneConfig();
        sceneMapper.insert(config);

        SceneConfig loaded = sceneMapper.selectById(config.getId());
        assertThat(loaded.getSceneId()).isEqualTo(config.getSceneId());
        assertThat(loaded.getAlarmConfigJson()).isEqualTo(config.getAlarmConfigJson());
        // ... 验证所有关键字段不丢失
    }
}
```

**CI 配置**（`.github/workflows/`）：

```yaml
# 集成测试在每次 PR 合并到 develop 时触发
- name: Run Entity Mapping Integration Tests
  run: |
    cd services/${{ matrix.service }}
    mvn verify -Pintegration-test -Dspring.profiles.active=test
```

---

### 方案三（中期）：引入 MyBatis-Plus Generator，以 SQL 为唯一事实来源

彻底解决问题的方式是：**让 Flyway SQL 成为唯一事实来源，Entity 由工具自动生成**。

```
Flyway SQL Migration（唯一事实来源）
    ↓ mvn mybatis-plus:generate
Java Entity（自动生成，含所有 @TableField 注解）
    ↓
Service / Controller（手写业务逻辑，不触碰字段映射）
```

Generator 配置关键参数（`scripts/generate-entities.java`）：

```java
FastAutoGenerator.create(
        "jdbc:postgresql://localhost:5432/tianjing_prod",
        "tianjing_prod_user", "password")
    .globalConfig(b -> b
        .author("tianjing-codegen")
        .outputDir("src/main/java")
        .disableOpenDir())
    .packageConfig(b -> b
        .parent("com.tianzhu.tianjing.scene")
        .entity("domain"))
    .strategyConfig(b -> b
        .addInclude("scene_config", "algorithm_plugin", "model_version")
        .entityBuilder()
            .enableLombok()
            .enableTableFieldAnnotation()   // ← 核心：强制生成所有 @TableField
            .logicDeleteColumnName("is_deleted")
            .logicDeletePropertyName("isDeleted")
            .versionColumnName("version")
            .versionPropertyName("version"))
    .execute();
```

`enableTableFieldAnnotation()` 是关键开关：即使字段名与列名完全吻合，也会生成 `@TableField("col_name")`，消除对自动推导的任何依赖。

**执行时机**：每次 `V{N}__xxx.sql` 提交后，在 PR 合并前运行 Generator 并将生成的 Entity 变更一并提交。

---

### 方案四（规范层面）：更新 CLAUDE.md

在 `§15 P2 — 工程规范` 中补充以下两条：

```
11. 新增或修改 Flyway SQL Migration 时，必须同步检查对应 Entity：
    a. 新增列 → Entity 必须新增字段，带 @TableField("actual_col_name")
    b. 重命名列 → Entity 字段注解必须同步更新，不得依赖自动推导
    c. 删除列 → Entity 必须删除字段或标注 @TableField(exist = false)
    含 SQL Migration 的 PR，Code Review 时必须包含对应 Entity 变更作为关联提交。

12. 禁止依赖 MyBatis-Plus 的 camelCase→underscore 自动推导（新增字段统一适用）
    所有 Entity 中，凡 Java 字段名与 DB 列名不完全吻合的字段，
    必须显式标注 @TableField("db_col_name")。
    推荐所有字段均显式标注，即使名字完全一致。
```

---

## 三、各方案对比

| 方案 | 实施成本 | 防护时机 | 根治程度 | 推荐优先级 |
|---|---|---|---|---|
| 方案一：补全 `@TableField` | 低（逐文件修改） | 编码阶段 | 消除隐患，不防止新增问题 | ★★★★★ 立即执行 |
| 方案二：Entity 映射集成测试 | 中（每服务一个测试类） | CI 阶段自动拦截 | 每次 Migration 后自动验证 | ★★★★★ 近期落地 |
| 方案三：Generator 自动生成 | 中（一次性配置） | 从源头消除分叉 | 彻底根治 | ★★★★☆ 中期引入 |
| 方案四：CLAUDE.md 规范 | 低（文档更新） | Code Review 人工检查 | 依赖执行纪律 | ★★★☆☆ 配合其他方案 |

---

## 四、本次问题修复清单（2026-04-02）

以下为本次集中修复的 Entity-DB 不对应问题，供参考：

| 服务 | Entity | 错误字段 | 修正后字段 + 注解 |
|---|---|---|---|
| `alarm-rule-service` | `ModelVersion` | `modelVersionId` | `versionId` + `@TableField("version_id")` |
| `alarm-rule-service` | `ModelVersion` | `modelArtifactUrl` | `modelPath` + `@TableField("model_path")` |
| `alarm-rule-service` | `ModelVersion` | `submittedBy`（不存在） | 删除，改用 `createdBy` |
| `alarm-rule-service` | `ModelVersion` | `reviewedBy/At/Comment`（不存在） | 改为 `approvedBy/At` |
| `alarm-rule-service` | `ModelVersion` | `map5095` | `map5095` + `@TableField("map50_95")` |
| `calibration-service` | `CalibrationRecord` | `refLengthMm` | `refDistanceMm` + `@TableField("ref_distance_mm")` |
| `calibration-service` | `CalibrationRecord` | `pt1X/pt1Y/pt2X/pt2Y` | `pixelP1X/Y/P2X/Y` + `@TableField("pixel_p1_x")` 等 |
| `calibration-service` | `CalibrationRecord` | `status` | `isActive`（Boolean） |
| `calibration-service` | `CalibrationRecord` | `frameImageUrl`（不存在） | 删除 |
| `alarm-judge-service` | `AlarmRecord` | `confirmedFrames` | `confirmFrames` + `@TableField("confirm_frames")` |
| `alarm-judge-service` | `AlarmRecord` | `inferResultJson`（不存在） | 删除 |
| `drift-monitor-service` | `DriftMetric` | `precision` | `precisionVal` + `@TableField("precision_val")` |
| `drift-monitor-service` | `DriftMetric` | `totalInferCount` | `totalAlarms` + `@TableField("total_alarms")` |
| `drift-monitor-service` | `DataSyncAudit` | `batchId` | `syncBatchId` + `@TableField("sync_batch_id")` |
| `drift-monitor-service` | `DataSyncAudit` | `status` | `syncStatus` + `@TableField("sync_status")` |
| `drift-monitor-service` | `SysOperationLog` | `operatorUsername` | `operator` + `@TableField("operator")` |
| `drift-monitor-service` | `SysOperationLog` | `createdAt` | `operatedAt` + `@TableField("operated_at")` |
| `history-replay-service` | `SimulationTask` | `videoFileName` | `taskName` + `@TableField("task_name")` |
| `history-replay-service` | `SimulationTask` | `videoObjectPath` | `videoFileUrl` + `@TableField("video_file_url")` |
| `history-replay-service` | `SimulationTask` | `completedAt` | `finishedAt` + `@TableField("finished_at")` |
| `compare-dashboard-service` | `SandboxSession` | `productionModelVersionId` | `prodModelId` + `@TableField("prod_model_id")` |
| `compare-dashboard-service` | `SandboxSession` | `startedAt/stoppedAt` | `startAt/endAt` + `@TableField("start_at")` 等 |
| `compare-dashboard-service` | `SandboxCompareReport` | `sandboxGpuMemoryGb` | `sandboxGpuMb` + `@TableField("sandbox_gpu_mb")` |
| `scene-config-service` | `SceneConfigHistory` | `historyVersion` | `configVersion` + `@TableField("config_version")` |
| `scene-config-service` | `SceneConfigHistory` | `configSnapshot` | `snapshotJson` + `@TableField("snapshot_json")` |
| `scene-config-service` | `SceneConfigHistory` | `createdBy/createdAt` | `changedBy/changedAt` + 对应 `@TableField` |

---

## 五、核心原则

> **让 Flyway SQL 成为唯一事实来源，Entity 是 SQL 的从属产物，而不是平行创建的独立文档。**
>
> 每一个 `@TableField` 注解都是一份"契约"，明确声明 Java 字段与 DB 列的对应关系。
> 隐式约定（自动推导）是隐患的温床，显式声明是工程质量的护栏。

---

*编制：天柱·天镜项目组 · 2026-04-02*
