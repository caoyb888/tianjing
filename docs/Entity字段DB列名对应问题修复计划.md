# Entity 字段与数据库列名对应问题 — 系统性修复计划

> **依据**：《根治Entity字段与数据库列名不对应问题解决方案.md》
> **编制日期**：2026-04-02
> **当前状态**：2026-04-02 已完成 8 个服务的紧急修复，本计划覆盖剩余工作

---

## 现状评估

### 已修复（2026-04-02 紧急修复批次）

| 服务 | 修复字段数 | 状态 |
|------|-----------|------|
| `alarm-judge-service` | 8 | ✅ 已修复并重启 |
| `alarm-rule-service` | 12 | ✅ 已修复并重启 |
| `calibration-service` | 9 | ✅ 已修复并重启 |
| `drift-monitor-service` | 18 | ✅ 已修复并重启 |
| `history-replay-service` | 7 | ✅ 已修复并重启 |
| `compare-dashboard-service` | 11 | ✅ 已修复并重启 |
| `scene-config-service` | 6 | ✅ 已修复并重启 |
| `auth-service` | 4 | ✅ 已修复并重启 |

### 待修复（本计划覆盖范围）

经扫描，以下服务仍存在缺少 `@TableField` 显式注解的字段，存在静默映射失败风险：

| 服务 | Entity | 缺注解字段数 | 风险等级 |
|------|--------|------------|---------|
| `device-manage-service` | `CameraDevice` | 15 | 🔴 高 |
| `device-manage-service` | `CameraHealthRecord` | 13 | 🔴 高 |
| `auth-service` | `SysUser` | 13 | 🔴 高（登录链路） |
| `drift-monitor-service` | `TrainJob` | 10 | 🟡 中 |
| `calibration-service` | `CalibrationRecord` | 8 | 🟡 中 |
| `alarm-rule-service` | `AlgorithmPlugin` | 7 | 🟡 中 |
| `compare-dashboard-service` | `SandboxCompareReport` | 7 | 🟡 中 |
| `scene-config-service` | `SceneConfig` | 9 | 🟡 中 |
| `alarm-judge-service` | `AlarmRecord` | 2 | 🟢 低 |

> **注**：风险等级基于字段命名偏差可能性 + 业务影响范围综合评估。
> `SysUser` 标为高风险是因为登录、鉴权链路对字段完整性要求极高。

---

## 修复计划

### 阶段一：补全所有 `@TableField` 显式注解
**目标**：消除所有依赖自动推导的字段，每个字段均有明确的列名声明。
**工期**：1 个 Sprint（约 1 周）
**负责人**：各服务 Owner

---

#### 任务 1-1：`device-manage-service` — `CameraDevice`（优先级：P0）

需对照 `camera_device` 表补全以下字段的 `@TableField`：

```java
// 以下字段需逐一核对 camera_device 表列名后补注解
@TableField("device_code")       private String deviceCode;
@TableField("device_name")       private String deviceName;
@TableField("scene_id")          private String sceneId;
@TableField("ip_address")        private String ipAddress;
@TableField("mac_address")       private String macAddress;
@TableField("vendor")            private String vendor;
@TableField("firmware_version")  private String firmwareVersion;
@TableField("protocol")          private String protocol;
@TableField("resolution_width")  private Integer resolutionWidth;
@TableField("resolution_height") private Integer resolutionHeight;
@TableField("fps")               private Integer fps;
@TableField("location_desc")     private String locationDesc;
@TableField("edge_node_id")      private String edgeNodeId;
@TableField("is_supplement_light") private Boolean isSupplementLight;
@TableField("health_status")     private String healthStatus;
```

**验证方法**：重启 `device-manage-service`，访问 `GET /api/v1/devices` 确认所有字段有值。

---

#### 任务 1-2：`device-manage-service` — `CameraHealthRecord`（优先级：P0）

需对照 `camera_health_record` 表补全以下字段：

```java
@TableField("device_code")     private String deviceCode;
@TableField("scene_id")        private String sceneId;
@TableField("check_at")        private OffsetDateTime checkAt;
@TableField("laplacian_var")   private Double laplacianVar;
@TableField("avg_brightness")  private Double avgBrightness;
@TableField("optical_flow_dx") private Double opticalFlowDx;
@TableField("optical_flow_dy") private Double opticalFlowDy;
@TableField("health_score")    private Integer healthScore;
@TableField("fault_type")      private String faultType;
@TableField("fault_desc")      private String faultDesc;
@TableField("action_taken")    private String actionTaken;
@TableField("work_order_id")   private String workOrderId;
```

---

#### 任务 1-3：`auth-service` — `SysUser`（优先级：P0）

对照 `sys_user` 表补全：

```java
@TableField("user_id")              private String userId;
@TableField("username")             private String username;
@TableField("password_hash")        private String passwordHash;
@TableField("display_name")         private String displayName;
@TableField("dept_code")            private String deptCode;
@TableField("email")                private String email;
@TableField("phone")                private String phone;
@TableField("status")               private String status;
@TableField("is_locked")            private Boolean isLocked;
@TableField("failed_login_count")   private Integer failedLoginCount;
@TableField("last_login_at")        private OffsetDateTime lastLoginAt;
@TableField("password_changed_at")  private OffsetDateTime passwordChangedAt;
@TableField("created_by")           private String createdBy;
```

---

#### 任务 1-4：`drift-monitor-service` — `TrainJob`（优先级：P1）

对照 `train_job` 表（`tianjing_train` 库）补全：

```java
@TableField("job_id")              private String jobId;
@TableField("plugin_id")           private String pluginId;
@TableField("dataset_version_id")  private String datasetVersionId;
@TableField("train_config_json")   private String trainConfigJson;
@TableField("gpu_count")           private Integer gpuCount;
@TableField("trigger_type")        private String triggerType;
@TableField("status")              private String status;
@TableField("best_epoch")          private Integer bestEpoch;
@TableField("best_map50")          private Double bestMap50;
// 注意：字段名含数字，自动推导 best_map5095，需确认实际列名
@TableField("best_map50_95")       private Double bestMap5095;
```

---

#### 任务 1-5：`scene-config-service` — `SceneConfig`（优先级：P1）

对照 `scene_config` 表补全：

```java
@TableField("scene_id")         private String sceneId;
@TableField("scene_name")       private String sceneName;
@TableField("factory_code")     private String factoryCode;
@TableField("process_code")     private String processCode;
@TableField("category")         private String category;
@TableField("priority")         private String priority;
@TableField("status")           private String status;
@TableField("bound_device_code") private String boundDeviceCode;
@TableField("active_plugin_id") private String activePluginId;
```

---

#### 任务 1-6：其余服务补全（优先级：P2）

按以下顺序完成，每个任务均需对照 Flyway SQL 逐字段核对：

| 任务 | 服务 | Entity | 预计工时 |
|------|------|--------|---------|
| 1-6a | `alarm-rule-service` | `AlgorithmPlugin`（7个字段） | 0.5h |
| 1-6b | `compare-dashboard-service` | `SandboxCompareReport`（7个字段） | 0.5h |
| 1-6c | `compare-dashboard-service` | `SandboxSession`（5个字段） | 0.5h |
| 1-6d | `calibration-service` | `CalibrationRecord`（8个字段） | 0.5h |
| 1-6e | `alarm-rule-service` | `ModelVersion`（4个字段） | 0.5h |
| 1-6f | `drift-monitor-service` | `DriftMetric`（7个字段） | 0.5h |
| 1-6g | `alarm-judge-service` | `AlarmRecord`（2个字段） | 0.25h |

---

### 阶段二：为每个服务增加 Entity 映射集成测试
**目标**：建立 CI 防线，每次 Migration 后自动验证 Entity-DB 对齐。
**工期**：1 个 Sprint
**前提**：本地开发数据库（`localhost:5432`）已通过 Flyway 完成 migrate。

#### 任务 2-1：在父 `pom.xml` 中添加集成测试依赖

```xml
<!-- services/pom.xml 的 <dependencyManagement> 中添加 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.7</version>
    <scope>test</scope>
</dependency>
```

#### 任务 2-2：创建共享测试基类

在 `tianjing-common` 模块中新建：

```java
// tianjing-common/src/test/java/.../AbstractEntityMappingIT.java
@SpringBootTest
@ActiveProfiles("it")
@Testcontainers
public abstract class AbstractEntityMappingIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tianjing_prod")
            .withUsername("tianjing_app")
            .withPassword("test_password");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Flyway 自动执行 Migration，保证与生产 Schema 一致
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,filesystem:../../deploy/flyway/prod");
    }
}
```

#### 任务 2-3：为每个服务创建 `EntityMappingIT.java`

**模板（以 `scene-config-service` 为例）：**

```java
// src/test/java/.../EntityMappingIT.java
@DisplayName("Entity 字段映射集成测试 — 防止列名与 DB 不对应")
class EntityMappingIT extends AbstractEntityMappingIT {

    @Autowired SceneConfigMapper sceneMapper;
    @Autowired AlgorithmPluginMapper pluginMapper;  // 若本服务有共享引用

    /**
     * 触发真实 SELECT，任何 @TableField 列名错误立即抛出 BadSqlGrammarException。
     * 这是最低成本的防护手段，CI 强制在每次 Flyway Migration 变更后通过此测试。
     */
    @Test
    @DisplayName("SceneConfig — 所有字段列名映射正确")
    void sceneConfig_fieldMappingCorrect() {
        assertThatNoException().isThrownBy(() ->
            sceneMapper.selectOne(new LambdaQueryWrapper<SceneConfig>().last("LIMIT 1"))
        );
    }

    @Test
    @DisplayName("SceneConfig — INSERT 不丢字段，SELECT 完整返回")
    void sceneConfig_insertAndSelectRoundTrip() {
        SceneConfig config = new SceneConfig();
        config.setSceneId("SCENE-TEST-" + System.currentTimeMillis());
        config.setSceneName("集成测试场景");
        config.setFactoryCode("SINTER");
        // ... 填充必填字段

        assertThatNoException().isThrownBy(() -> sceneMapper.insert(config));

        SceneConfig loaded = sceneMapper.selectById(config.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getSceneId()).isEqualTo(config.getSceneId());
        assertThat(loaded.getSceneName()).isEqualTo(config.getSceneName());
    }
}
```

**需创建 EntityMappingIT 的服务清单：**

| 服务 | 需测试的 Entity | 优先级 |
|------|--------------|-------|
| `scene-config-service` | SceneConfig, SceneConfigHistory | P0 |
| `device-manage-service` | CameraDevice, CameraHealthRecord | P0 |
| `auth-service` | SysUser | P0 |
| `alarm-rule-service` | AlgorithmPlugin, ModelVersion | P1 |
| `alarm-judge-service` | AlarmRecord | P1 |
| `calibration-service` | CalibrationRecord | P1 |
| `drift-monitor-service` | DriftMetric, TrainJob, DataSyncAudit, SysOperationLog | P1 |
| `history-replay-service` | SimulationTask | P1 |
| `compare-dashboard-service` | SandboxSession, SandboxCompareReport | P1 |

---

### 阶段三：更新 CLAUDE.md，固化规范
**目标**：将防护规则写入项目规范，后续 AI 编码和人工开发均受约束。
**工期**：0.5 天
**负责人**：技术负责人

在 `CLAUDE.md §15 P2 工程规范` 末尾追加：

```
11. 新增或修改 Flyway SQL Migration 时，必须同步检查对应 Entity：
    a. 新增列 → Entity 新增字段，带 @TableField("actual_col_name")
    b. 重命名列 → Entity 同步更新注解，不得依赖 camelCase 自动推导
    c. 删除列 → Entity 删除字段或标注 @TableField(exist = false)
    含 SQL Migration 的 PR，Code Review 时必须包含对应 Entity 变更作为关联提交。

12. 禁止依赖 MyBatis-Plus 的 camelCase→underscore 自动推导（即日起生效）
    所有 @Data Entity 中，每个字段必须是以下四种之一：
      - @TableId          — 主键
      - @TableField("col") — 普通列，显式写出列名
      - @TableField(exist = false) — 非数据库字段
      - @TableLogic / @Version  — 逻辑删除/乐观锁专用注解
    违反此规范的字段在 Code Review 阶段必须拦截，不得合入 develop 分支。
```

---

### 阶段四（中期）：引入 MyBatis-Plus Generator
**目标**：从源头消除手写 Entity 与 DB Schema 分叉的可能性。
**工期**：2 个 Sprint（含验证周期）
**前提**：阶段一、二、三完成后再推进，避免生成代码覆盖已修复内容。

#### 执行步骤

**步骤 4-1**：在 `scripts/` 下创建 `generate-entities.sh`

```bash
#!/bin/bash
# 使用方式：./scripts/generate-entities.sh scene-config-service scene_config,scene_config_history
# 参数一：服务名；参数二：逗号分隔的表名列表

SERVICE=$1
TABLES=$2
MODULE_PATH="services/$SERVICE"

mvn mybatis-plus-generator:generate \
  -Dgenerator.datasource.url="jdbc:postgresql://localhost:5432/tianjing_prod" \
  -Dgenerator.datasource.username="tianjing_prod_user" \
  -Dgenerator.datasource.password="${TIANJING_POSTGRES_PROD_PASSWORD}" \
  -Dgenerator.outputDir="$MODULE_PATH/src/main/java" \
  -Dgenerator.tables="$TABLES" \
  -Dgenerator.enableTableFieldAnnotation=true \
  -f "$MODULE_PATH/generator-config.xml"
```

**步骤 4-2**：关键 Generator 配置（每个服务的 `generator-config.xml`）

```xml
<strategyConfig>
    <!-- 强制为每个字段生成 @TableField，包括名字吻合的字段 -->
    <enableTableFieldAnnotation>true</enableTableFieldAnnotation>
    <!-- 逻辑删除列 -->
    <logicDeleteColumnName>is_deleted</logicDeleteColumnName>
    <!-- 乐观锁列 -->
    <versionColumnName>version</versionColumnName>
    <!-- 保留 Lombok @Data -->
    <enableLombok>true</enableLombok>
    <!-- 不覆盖 Service/Controller，只重新生成 Entity -->
    <generateService>false</generateService>
    <generateController>false</generateController>
</strategyConfig>
```

**步骤 4-3**：将 Generator 执行纳入 Flyway Migration 的 PR 模板

在 `.github/PULL_REQUEST_TEMPLATE.md` 中添加检查项：
```markdown
## Migration 相关检查（仅含 Flyway SQL 变更时填写）

- [ ] 已运行 `./scripts/generate-entities.sh` 重新生成受影响的 Entity
- [ ] 生成的 Entity 已通过 `EntityMappingIT` 集成测试
- [ ] `@TableField` 注解已人工复查，无遗漏
```

---

## 里程碑与时间线

```
2026-04-02  ████ 紧急修复批次（8个服务，已完成）
            │
2026-04-09  ████ 阶段一完成：剩余服务 @TableField 补全
            │     ├── 任务1-1 ~ 1-3（P0，高风险服务）：前3天
            │     └── 任务1-4 ~ 1-6（P1/P2）：后2天
            │
2026-04-16  ████ 阶段二完成：EntityMappingIT 集成测试全覆盖
            │     ├── 任务2-1：父 pom 添加依赖（0.5天）
            │     ├── 任务2-2：创建测试基类（0.5天）
            │     └── 任务2-3：9个服务各创建测试类（4天）
            │
2026-04-17  ████ 阶段三完成：CLAUDE.md 规范更新（0.5天）
            │
2026-05-07  ████ 阶段四完成：Generator 引入并验证（2个Sprint）
```

---

## 验收标准

| 阶段 | 完成标志 |
|------|---------|
| 阶段一 | `grep -rn "private.*;" services/*/src/main/java/*/domain/*.java` 输出的所有字段均有 `@TableField`、`@TableId`、`@TableLogic` 或 `@Version` 之一 |
| 阶段二 | `mvn verify -Pintegration-test` 在所有 9 个服务上全部通过，CI pipeline 绿灯 |
| 阶段三 | CLAUDE.md §15 P2 规则 11-12 合入 main 分支 |
| 阶段四 | 任意新增表后运行 Generator，生成的 Entity 可直接用于 EntityMappingIT 且测试通过 |

---

## 风险与注意事项

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 补注解时列名写错 | 引入新的映射错误 | 每次补注解后必须对照 Flyway SQL 人工校验，并重启服务验证 |
| Testcontainers 启动慢 | CI 耗时增加 5~10 分钟 | 仅在 `integration-test` Maven Profile 下触发，日常 `mvn test` 不执行 |
| Generator 覆盖已有业务注释 | 丢失 Entity 上的中文注释 | 生成后 `git diff` 确认，只保留字段注解变更，注释手动合并 |
| 阶段四与现有修复冲突 | Generator 生成的字段名可能与手写不一致 | 阶段四必须在阶段一完成并充分运行后再执行 |

---

*天柱·天镜项目组 · 2026-04-02*
