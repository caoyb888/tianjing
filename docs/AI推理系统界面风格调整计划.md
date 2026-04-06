# AI 推理系统界面风格调整计划

> **版本**：V1.0 · **日期**：2026-04-06  
> **执行范围**：`apps/admin-web/` 前端目录，**严禁触碰任何后端代码**  
> **分支策略**：独立分支开发 → 视觉验收 → PR 合并  
> **负责人**：前端 UI 专项人员（1人）

---

## 一、调整目标与设计方向

### 1.1 现状问题

| 问题 | 具体表现 |
|---|---|
| 视觉风格通用 | 照搬 Ant Design Pro 默认配色（#001529 侧边栏 + Element Plus 蓝），无工业 AI 产品特征 |
| 告警等级辨识度低 | CRITICAL / WARNING / INFO 仅靠 `el-tag` 颜色区分，工业大屏下难以快速定位 |
| 信息密度不足 | 卡片内边距偏大（20px），列表行高松散，单屏信息量少 |
| 数据看板缺乏冲击力 | 统计卡片图标色块过小（52px），数据文字层级不突出 |
| 状态标注不一致 | StatusBadge 在不同页面 `effect` 参数混用，视觉表现不统一 |
| 登录页无品牌感 | 纯白背景表单，无产品身份识别 |

### 1.2 设计方向：工业科技蓝（Industrial Tech）

```
主色调：深邃蓝  #1557B0（替换 Element Plus 默认 #409EFF）
强调色：科技青  #00B4C6（用于图表高亮、激活状态）
危险色：工业红  #D9363E（CRITICAL 告警，保持高可见度）
警告色：琥珀黄  #E6A23C（WARNING，与 Sandbox 虚线框一致）
成功色：绿      #52C41A
背景色：浅岩灰  #F4F5F7（替换 #F0F2F5，更内敛）
卡片色：纯白    #FFFFFF，subtle 阴影（box-shadow: 0 1px 4px rgba(0,0,0,.08)）
侧边栏：深炭蓝  #0D1B2E（比 #001529 更蓝，减少纯黑感）
```

### 1.3 不改动的边界（硬性约束）

以下内容属于**功能逻辑或安全规范**，调整人员**不得修改**：

| 文件/位置 | 禁止修改的内容 |
|---|---|
| 所有 `<script setup>` 块 | 业务逻辑、API 调用、状态管理 |
| `styles/index.scss` 中 `.sandbox-bbox` | Sandbox 虚线框样式（安全规范强制要求） |
| `AppLayout.vue` `<router-view>` | 禁止包裹 `<transition mode="out-in">`（已知 Bug，见 CLAUDE.md §15 P2-9） |
| `router/index.ts` | 路由不动 |
| `stores/` 所有文件 | 状态逻辑不动 |
| `api/` 所有文件 | 接口封装不动 |
| `composables/` `directives/` | 功能逻辑不动 |
| `components.d.ts` `auto-imports.d.ts` | 自动生成文件不手动修改 |

---

## 二、分支策略

### 2.1 分支创建

```bash
# 从 main 当前提交拉取专项分支
git checkout main
git pull origin main
git checkout -b feature/ui-style-2026
git push -u origin feature/ui-style-2026
```

### 2.2 开发期间提交规范

每完成一个 Phase 提交一次，message 格式：

```
style(ui): Phase N — <本阶段简述>

Co-Authored-By: ...
```

### 2.3 合并条件（缺一不可）

- [ ] 所有页面在 1920×1080 分辨率下无布局错位
- [ ] 所有页面在 1440×900 分辨率下无内容溢出
- [ ] 侧边栏折叠/展开动画正常
- [ ] 深色模式（Dark Mode）无白色闪烁
- [ ] `npm run build` 零警告零报错
- [ ] Sandbox 标注框仍为虚线（`.sandbox-bbox` 样式未被污染）
- [ ] 告警抽屉正常展开，CRITICAL 告警行背景色可见
- [ ] 由后端负责人（非本次 UI 调整人员）review PR，确认无后端文件变更

### 2.4 PR 规范

```
标题：style(admin-web): 界面风格调整 — 工业科技蓝主题
描述：
  ## 改动范围
  仅限 apps/admin-web/src/ 下的样式文件和组件 <style> 区块

  ## 截图对比
  [Before] [After] 对比截图（必须提供以下页面）：
  - 登录页
  - Dashboard
  - 告警列表
  - 场景列表
  - 仿真任务详情

  ## 验收清单
  - [ ] 双分辨率无溢出
  - [ ] Dark Mode 正常
  - [ ] build 零报错
  - [ ] Sandbox 虚线框未被修改
```

---

## 三、改动文件清单

### 新增文件（1 个）

| 文件路径 | 用途 |
|---|---|
| `src/styles/variables.scss` | 全局 CSS 自定义属性（设计 Token），所有其他样式文件通过此处引用颜色/间距变量 |

### 修改文件（按改动量排序）

| 序号 | 文件路径 | 改动类型 | 改动说明 |
|---|---|---|---|
| 1 | `src/styles/index.scss` | 修改 | 引入 variables.scss；更新全局背景、字体、滚动条、基础色 |
| 2 | `vite.config.ts` | 修改 | additionalData 注入 variables.scss，使所有组件可用 SCSS 变量 |
| 3 | `src/components/layout/AppSidebar.vue` | 修改 `<style>` | 侧边栏背景、Logo 区域、菜单激活色、折叠按钮 |
| 4 | `src/components/layout/AppHeader.vue` | 修改 `<style>` | 头部背景、边框、告警铃铛、用户头像 |
| 5 | `src/components/layout/AppLayout.vue` | 修改 `<style>` | 内容区背景色、页面最大宽度 |
| 6 | `src/components/layout/AppFooter.vue` | 修改 `<style>` | 底部背景与文字色 |
| 7 | `src/components/layout/AppBreadcrumb.vue` | 修改 `<style>` | 面包屑间距与激活色 |
| 8 | `src/components/common/StatusBadge.vue` | 修改 `<script>` defaultMap + `<style>` | 统一 `effect="dark"`，调整各状态 type 映射 |
| 9 | `src/components/common/PageHeader.vue` | 修改 `<style>` | 页面标题字号、描述色、底部分割线 |
| 10 | `src/components/common/DataTable.vue` | 修改 `<style>` | 表格行高、斑马纹色、悬浮高亮色 |
| 11 | `src/components/common/EmptyState.vue` | 修改 `<style>` | 空状态图标与文字色 |
| 12 | `src/views/LoginView.vue` | 修改 `<style>` | 登录页背景（渐变/工业图）、卡片阴影、Logo 样式 |
| 13 | `src/views/DashboardView.vue` | 修改 `<style>` | 统计卡片图标尺寸、数值字号、图表卡片标题 |
| 14 | `src/views/alarm/AlarmListView.vue` | 修改 `<style>` | CRITICAL 行高亮背景、级别 Tag 加粗 |
| 15 | `src/views/alarm/AlarmDetailView.vue` | 修改 `<style>` | 告警详情页头部级别色块 |
| 16 | `src/views/scene/SceneListView.vue` | 修改 `<style>` | 场景状态卡片 active/inactive 色差 |
| 17 | `src/views/device/DeviceListView.vue` | 修改 `<style>` | 设备健康度进度条颜色 |
| 18 | `src/views/device/DeviceHealthView.vue` | 修改 `<style>` | 健康分卡片背景渐变 |
| 19 | `src/views/simulation/SimulationListView.vue` | 修改 `<style>` | 视频上传区拖拽边框色 |
| 20 | `src/views/simulation/SimulationDetailView.vue` | 修改 `<style>` | 视频列表状态色、进度条 |
| 21 | `src/views/training/TrainingJobListView.vue` | 修改 `<style>` | 任务状态色阶 |
| 22 | `src/views/training/TrainingJobDetailView.vue` | 修改 `<style>` | 日志区背景（深色终端风） |
| 23 | `src/views/sandbox/SandboxDetailView.vue` | 修改 `<style>` | Sandbox 标识栏（橙色警示带，不改虚线框） |
| 24 | `src/views/DriftMonitorView.vue` | 修改 `<style>` | 漂移告警阈值线颜色 |
| 25 | `src/views/HealthMonitorView.vue` | 修改 `<style>` | 健康评分环形进度色 |
| 26 | `index.html` | 修改 | `<title>` 标签文案（可选） |

**合计：新增 1 个，修改 26 个，均在 `apps/admin-web/` 内。**

---

## 四、分阶段实施计划

### Phase 0 — 准备工作（0.5 天）

**目标**：建立分支，截取基准截图，搭建 Token 体系。

**任务清单**：

- [ ] 执行分支创建命令（见二、2.1）
- [ ] 对以下页面截图存档（放入 `docs/ui-baseline/` 目录）：
  - `/login`、`/dashboard`、`/alarms`、`/scenes`、`/devices`
  - `/simulations/{任意已完成任务ID}`、`/training/jobs`、`/sandbox/sessions`
- [ ] 新建 `src/styles/variables.scss`，写入全部设计 Token：

```scss
// ── 主色系 ────────────────────────────────────────
:root {
  --tj-primary:         #1557B0;   // 主操作色（替换 Element Plus 默认蓝）
  --tj-primary-light:   #E8F0FE;   // 主色浅背景（hover、选中背景）
  --tj-primary-dark:    #0F3E82;   // 主色深（按下状态）
  --tj-accent:          #00B4C6;   // 强调色（图表、徽章）

  // 告警色（与 CLAUDE.md 告警级别对应）
  --tj-critical:        #D9363E;   // CRITICAL 告警
  --tj-critical-bg:     #FFF1F0;   // CRITICAL 行背景
  --tj-warning:         #E6A23C;   // WARNING 告警（与 .sandbox-bbox 一致，禁止修改此值）
  --tj-warning-bg:      #FFFBE6;   // WARNING 行背景
  --tj-success:         #52C41A;   // 正常/完成
  --tj-info:            #8C8C8C;   // 信息/停用

  // 中性色
  --tj-text-primary:    #1A1A2E;   // 主文字
  --tj-text-regular:    #4A4A6A;   // 正文
  --tj-text-secondary:  #8C8CA0;   // 次要文字
  --tj-text-placeholder:#BFBFCF;   // 占位符

  // 背景色
  --tj-bg-page:         #F4F5F7;   // 页面背景
  --tj-bg-card:         #FFFFFF;   // 卡片背景
  --tj-bg-sidebar:      #0D1B2E;   // 侧边栏背景

  // 边框
  --tj-border-base:     #E4E7ED;   // 常规边框
  --tj-border-light:    #F0F0F5;   // 轻边框（卡片内分割线）

  // 阴影
  --tj-shadow-card:     0 1px 4px rgba(0, 0, 0, .08);
  --tj-shadow-hover:    0 4px 12px rgba(21, 87, 176, .15);

  // 间距（保持与现有组件兼容）
  --tj-space-xs:  4px;
  --tj-space-sm:  8px;
  --tj-space-md:  16px;
  --tj-space-lg:  24px;
  --tj-space-xl:  32px;

  // 圆角
  --tj-radius-sm:  4px;
  --tj-radius-md:  8px;
  --tj-radius-lg:  12px;

  // 字号
  --tj-font-xs:   12px;
  --tj-font-sm:   13px;
  --tj-font-base: 14px;
  --tj-font-md:   16px;
  --tj-font-lg:   20px;
  --tj-font-xl:   28px;
}

// Element Plus 主色覆盖（在 :root 中覆盖 el- 变量）
:root {
  --el-color-primary:         var(--tj-primary);
  --el-color-primary-light-3: #4A82C8;
  --el-color-primary-light-5: #89ABDB;
  --el-color-primary-light-7: #C4D5EE;
  --el-color-primary-light-8: #DCEAF8;
  --el-color-primary-light-9: var(--tj-primary-light);
  --el-color-primary-dark-2:  var(--tj-primary-dark);
  --el-color-danger:          var(--tj-critical);
  --el-color-warning:         var(--tj-warning);
  --el-color-success:         var(--tj-success);
  --el-bg-color-page:         var(--tj-bg-page);
}
```

- [ ] 修改 `vite.config.ts`，全局注入变量文件：

```typescript
// vite.config.ts css 配置块新增
css: {
  preprocessorOptions: {
    scss: {
      additionalData: `@use "@/styles/variables.scss" as *;`
    }
  }
}
```

- [ ] 修改 `src/styles/index.scss` 顶部，引入变量并更新全局基础色：

```scss
// 替换原有 body 背景 #f0f2f5 → var(--tj-bg-page)
// 替换原有 color: #303133   → var(--tj-text-primary)
// 滚动条 thumb 颜色 → #9BACC8
```

**提交**：`style(ui): Phase 0 — 建立分支、Token体系、基准截图`

---

### Phase 1 — 布局组件（1 天）

**目标**：侧边栏、顶部栏、布局背景统一更新为新主题。

#### 1.1 AppSidebar.vue（仅 `<style scoped>`）

```scss
// 修改项（不改 template 逻辑）：
.app-sidebar {
  background: var(--tj-bg-sidebar);   // #001529 → #0D1B2E
}

.sidebar-logo {
  border-bottom-color: rgba(255,255,255,.08);  // 更轻的分割线
}

.logo-icon {
  background: var(--tj-primary);   // 跟随主色变化
  border-radius: var(--tj-radius-md);
}

.logo-text {
  font-size: 15px;
  letter-spacing: .5px;
  color: #E8F0FE;    // 带蓝调的白，比纯白柔和
}

// 侧边栏菜单激活项指示条（左侧 3px 亮线）
:deep(.el-menu-item.is-active) {
  background: rgba(21, 87, 176, .35) !important;
  border-left: 3px solid var(--tj-primary-light) !important;
  color: #fff !important;
}

// el-menu 的 background-color / text-color / active-text-color
// 同步修改 template 中三个属性值：
//   background-color="#0D1B2E"
//   text-color="rgba(255,255,255,.72)"
//   active-text-color="#E8F0FE"
```

> **注意**：`background-color` / `text-color` / `active-text-color` 是 `el-menu` 的 **prop**，不是样式，需要在 `<template>` 中修改这三个属性的字面量值。这是唯一允许碰 template 的地方（纯视觉 prop，无逻辑）。

#### 1.2 AppHeader.vue（仅 `<style scoped>`）

```scss
.app-header {
  background: #fff;
  border-bottom: 1px solid var(--tj-border-base);
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  // 移除原有 box-shadow（如果有）
}

.user-avatar {
  background: var(--tj-primary);
  font-weight: 600;
}

// 头部告警铃铛 badge 颜色使用 --tj-critical
:deep(.el-badge__content) {
  background-color: var(--tj-critical);
}
```

#### 1.3 AppLayout.vue（仅 `<style scoped>`）

```scss
.page-content {
  background: var(--tj-bg-page);  // #f0f2f5 → #F4F5F7
}
```

#### 1.4 AppBreadcrumb.vue（仅 `<style scoped>`）

```scss
// 当前页（最后一级）使用主色
:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--tj-primary);
  font-weight: 500;
}
```

**提交**：`style(ui): Phase 1 — 侧边栏/头部/布局背景更新`

---

### Phase 2 — 通用组件（0.5 天）

**目标**：StatusBadge、PageHeader、DataTable、EmptyState 四个高复用组件统一风格。

#### 2.1 StatusBadge.vue

```typescript
// 修改 defaultMap，统一使用 effect="dark" + 调整颜色语义
const defaultMap = {
  // 运行态
  active:    { label: '运行中', type: 'success' },
  running:   { label: '运行中', type: 'success' },
  online:    { label: '在线',   type: 'success' },
  completed: { label: '已完成', type: 'success' },
  // 停止态
  inactive:  { label: '已停用', type: 'info' },
  offline:   { label: '离线',   type: 'danger' },
  cancelled: { label: '已取消', type: 'info' },
  // 中间态
  pending:   { label: '等待中', type: 'warning' },
  queued:    { label: '排队中', type: 'warning' },
  draft:     { label: '草稿',   type: 'info' },
  // 异常态
  failed:    { label: '失败',   type: 'danger' },
  warning:   { label: '告警',   type: 'warning' },
  // 告警级别（AlarmListView 使用）
  CRITICAL:  { label: 'CRITICAL', type: 'danger' },
  WARNING:   { label: 'WARNING',  type: 'warning' },
  INFO:      { label: 'INFO',     type: 'info' },
}
```

```scss
// <style scoped> 新增：CRITICAL 标签加粗加边框强调
:deep(.el-tag--danger.el-tag--dark) {
  font-weight: 600;
  border: 1px solid var(--tj-critical);
}
```

#### 2.2 PageHeader.vue（仅 `<style scoped>`）

```scss
.page-header {
  padding-bottom: 16px;
  margin-bottom: 16px;
  border-bottom: 2px solid var(--tj-border-light);
}

.title {
  font-size: var(--tj-font-lg);   // 16px → 20px
  font-weight: 700;
  color: var(--tj-text-primary);
  letter-spacing: -.3px;
}

.description {
  font-size: var(--tj-font-sm);   // 13px
  color: var(--tj-text-secondary);
  margin-top: 4px;
}
```

#### 2.3 DataTable.vue（仅 `<style scoped>`）

```scss
// 表格行高收紧（工业 UI 信息密度优化）
:deep(.el-table__row td) {
  padding: 10px 0;    // 默认 12px → 10px
}

// 表头加强
:deep(.el-table__header th) {
  background: var(--tj-primary-light) !important;
  color: var(--tj-text-primary);
  font-weight: 600;
  font-size: var(--tj-font-sm);
}

// 斑马纹
:deep(.el-table--striped .el-table__body tr.el-table__row--striped td) {
  background: #FAFBFF;
}

// 行悬浮
:deep(.el-table__body tr:hover > td) {
  background: var(--tj-primary-light) !important;
}
```

**提交**：`style(ui): Phase 2 — StatusBadge/PageHeader/DataTable 统一风格`

---

### Phase 3 — 登录页（0.5 天）

**目标**：登录页从"空白表单"升级为有品牌感的工业风。

#### LoginView.vue（仅 `<style scoped>` + template 中纯视觉的文案/图标）

```scss
.login-wrapper {
  // 工业蓝渐变背景，替换纯白
  background: linear-gradient(135deg, #0D1B2E 0%, #1557B0 60%, #00B4C6 100%);
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 420px;
  border-radius: var(--tj-radius-lg);
  box-shadow: 0 20px 60px rgba(0, 0, 0, .4);
  // 毛玻璃效果（现代浏览器均支持）
  background: rgba(255, 255, 255, .95);
  backdrop-filter: blur(10px);
}

.login-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--tj-text-primary);
  letter-spacing: .5px;
}

.login-subtitle {
  font-size: 13px;
  color: var(--tj-text-secondary);
  margin-top: 4px;
}

// 登录按钮主色
.login-btn {
  width: 100%;
  background: var(--tj-primary);
  border-color: var(--tj-primary);
  font-size: 15px;
  letter-spacing: 2px;
  height: 44px;
  &:hover {
    background: var(--tj-primary-dark);
    border-color: var(--tj-primary-dark);
  }
}

// 左侧装饰区（可选，宽屏展示产品介绍）
.login-brand {
  color: rgba(255,255,255,.9);
  .brand-name { font-size: 32px; font-weight: 800; }
  .brand-desc { font-size: 14px; line-height: 1.8; margin-top: 16px; opacity: .8; }
}
```

**提交**：`style(ui): Phase 3 — 登录页工业蓝渐变背景重设计`

---

### Phase 4 — 业务页面（1.5 天）

按页面重要性排序，逐页调整 `<style scoped>` 区块。

#### 4.1 DashboardView.vue — 统计卡片增强

```scss
.stat-card {
  border: none;
  box-shadow: var(--tj-shadow-card);
  transition: box-shadow .2s;
  &:hover { box-shadow: var(--tj-shadow-hover); }
}

.stat-icon {
  width: 60px;    // 52px → 60px
  height: 60px;
  border-radius: var(--tj-radius-lg);
  font-size: 28px;
}

.stat-value {
  font-size: 32px;   // 28px → 32px
  font-weight: 800;
  color: var(--tj-text-primary);
}

.stat-label {
  font-size: var(--tj-font-sm);
  color: var(--tj-text-secondary);
  font-weight: 500;
}

// 图表卡片
.chart-card {
  border: 1px solid var(--tj-border-light);
  box-shadow: var(--tj-shadow-card);
  :deep(.el-card__header) {
    border-bottom-color: var(--tj-border-light);
    padding: 14px 20px;
  }
}
```

#### 4.2 AlarmListView.vue — 告警行高亮

```scss
// CRITICAL 告警行背景（对应 el-table 的 row-class-name）
:deep(.row-critical td) {
  background-color: var(--tj-critical-bg) !important;
}
:deep(.row-critical:hover td) {
  background-color: #FFE0DE !important;
}
:deep(.row-warning td) {
  background-color: var(--tj-warning-bg) !important;
}
```

> 核查 `AlarmListView.vue` 中 `row-class-name` 回调是否已对 CRITICAL/WARNING 行返回对应 class 名，若无则在 `<script>` 中补充（纯展示逻辑，不涉及 API 调用，允许小改）。

#### 4.3 TrainingJobDetailView.vue — 日志区终端风

```scss
.log-container {
  background: #0D1B2E;
  color: #C8E6C9;
  font-family: 'JetBrains Mono', 'Cascadia Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.7;
  border-radius: var(--tj-radius-md);
  padding: 16px;
}
```

#### 4.4 SandboxDetailView.vue — Sandbox 警示标识

```scss
// 在页面顶部添加 Sandbox 警示横幅（橙色条，区别于生产环境）
.sandbox-banner {
  background: linear-gradient(90deg, #E6A23C, #F5A623);
  color: #fff;
  font-weight: 600;
  font-size: 13px;
  padding: 8px 20px;
  display: flex;
  align-items: center;
  gap: 8px;
  letter-spacing: .5px;
}
// 注意：.sandbox-bbox 虚线框样式在 index.scss 中，此处不得触碰
```

#### 4.5 其余页面（DeviceHealthView、DriftMonitorView、HealthMonitorView 等）

- 卡片统一使用 `box-shadow: var(--tj-shadow-card)`，移除 `shadow="hover"` 相关的嵌套 CSS
- 进度条、环形图颜色统一引用 `--tj-primary` / `--tj-critical` / `--tj-warning`
- 表格头部统一使用 Phase 2 中 DataTable 的样式（通过 `:deep()` 覆盖）

**提交**：`style(ui): Phase 4 — Dashboard/告警/训练/Sandbox 等业务页面视觉优化`

---

### Phase 5 — 深色模式适配（0.5 天）

**目标**：在 Dark Mode 下，新 Token 体系的颜色正确呈现。

```scss
// 在 variables.scss 末尾追加
.dark {
  --tj-bg-page:      #141414;
  --tj-bg-card:      #1F1F1F;
  --tj-bg-sidebar:   #0A0F1A;
  --tj-text-primary: #E8E8F0;
  --tj-text-regular: #B0B0C0;
  --tj-text-secondary: #707080;
  --tj-border-base:  #303040;
  --tj-border-light: #2A2A3A;
  --tj-primary-light: rgba(21, 87, 176, .2);
  --tj-critical-bg:  rgba(217, 54, 62, .15);
  --tj-warning-bg:   rgba(230, 162, 60, .15);
  // 主色、告警色在深色模式下保持不变
}
```

**提交**：`style(ui): Phase 5 — 深色模式 Token 适配`

---

### Phase 6 — 整体走查与收尾（0.5 天）

**目标**：全量页面走查，修复视觉细节，准备验收。

**走查清单**：

| 页面 | 检查项 |
|---|---|
| `/login` | 背景渐变、卡片毛玻璃、按钮 hover |
| `/dashboard` | 统计卡片悬浮阴影、图表颜色 |
| `/alarms` | CRITICAL 行背景、分页器颜色 |
| `/scenes` | 启用/停用状态 badge、表格行高 |
| `/devices` | 在线/离线状态色、健康进度条 |
| `/algorithms` | 插件 Tag 颜色 |
| `/models` | 状态步骤条颜色 |
| `/sandbox/sessions` | Sandbox 警示横幅、虚线框不变 |
| `/simulations` | 多视频上传区拖拽边框色 |
| `/training/jobs` | 状态 badge、日志区背景 |
| `/health-monitor` | 健康评分色、摄像头状态 |
| `/drift` | 漂移曲线、精度阈值线 |
| `/system/users` | 角色 Tag 颜色 |
| 侧边栏 | 折叠/展开动画、激活项左侧蓝线 |
| 头部 | 告警红点、全屏按钮 |

- [ ] `npm run build` 检查，零错误零警告
- [ ] 截取全部页面 After 截图，与 Phase 0 基准对比
- [ ] 检查 `.sandbox-bbox` CSS 规则未被修改（`grep -n 'sandbox-bbox' src/styles/index.scss`）

**提交**：`style(ui): Phase 6 — 全量走查、细节修复、收尾`

---

## 五、验收与合并

### 5.1 本地验收命令

```bash
# 1. 构建验证
cd apps/admin-web
npm run build    # 必须零错误

# 2. Sandbox 虚线框未被修改
grep -n 'sandbox-bbox' src/styles/index.scss

# 3. 启动预览，人工走查
npm run preview
```

### 5.2 提 PR

```bash
git push origin feature/ui-style-2026
# 在 GitHub 上发起 PR：feature/ui-style-2026 → main
```

PR 描述中须包含：
1. 改动文件数量及范围声明（"仅 apps/admin-web/ 目录"）
2. Before / After 截图对比（至少 5 个关键页面）
3. `npm run build` 输出截图（零警告）
4. Sandbox 虚线框截图（证明未被修改）

### 5.3 Review 要点（后端负责人）

后端负责人 review PR 只需确认：

- [ ] `git diff --stat origin/main...feature/ui-style-2026` 输出中，**无任何** `services/`、`deploy/`、`proto/`、`inference/`、`training/`、`algorithms/` 目录下的文件
- [ ] `apps/admin-web/src/api/` 目录下文件 diff 为空
- [ ] `apps/admin-web/src/stores/` 目录下文件 diff 为空（或仅有纯视觉的 theme 字段）

### 5.4 合并

满足所有验收条件后，使用 **Squash Merge** 将所有 Phase 提交合并为一条记录：

```
style(admin-web): 界面风格调整 — 工业科技蓝主题 (#PR号)
```

---

## 六、改动工作量估算

| Phase | 主要内容 | 预估工时 |
|---|---|---|
| Phase 0 | 分支、截图、Token 体系 | 0.5 天 |
| Phase 1 | 侧边栏、头部、布局 | 1.0 天 |
| Phase 2 | 四个通用组件 | 0.5 天 |
| Phase 3 | 登录页 | 0.5 天 |
| Phase 4 | 业务页面（14 个页面） | 1.5 天 |
| Phase 5 | 深色模式适配 | 0.5 天 |
| Phase 6 | 走查收尾 + 截图 + PR | 0.5 天 |
| **合计** | | **5.0 人天** |

---

## 七、快速参考：禁止修改的文件

执行人员开始任何 Phase 前，先默读此列表：

```
❌ services/          所有后端 Java 代码
❌ deploy/            部署配置
❌ proto/             gRPC 定义
❌ inference/         Python 推理服务
❌ training/          训练脚本
❌ algorithms/        算法插件
❌ apps/admin-web/src/api/        接口封装
❌ apps/admin-web/src/stores/     状态管理（主题 toggle 逻辑除外）
❌ apps/admin-web/src/router/     路由
❌ apps/admin-web/src/composables/ 组合式函数
❌ apps/admin-web/src/directives/  指令
❌ apps/admin-web/src/styles/index.scss 中的 .sandbox-bbox（不得修改）
❌ AppLayout.vue 中的 <router-view>（不得包裹 transition mode="out-in"）
```

---

*文档版本：V1.0 · 天柱·天镜项目组 · 2026-04-06*
