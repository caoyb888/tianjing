# 分支保护规则配置指南

> **Sprint**: S0-06 · **执行人**: PM · **日期**: 2026-04-01

## 分支保护规则（需在 GitHub/GitLab 界面手动配置）

### `main` 分支

| 规则 | 配置值 |
|------|--------|
| 需要 PR 合并 | ✅ 开启 |
| 审批人数量 | **2 人** |
| 需要代码所有者审批 | ✅ 开启 |
| 解散过期审批 | ✅ 开启 |
| 需要 CI 通过 | ✅ 开启（必须通过全部 CI Job） |
| 禁止强制推送 | ✅ 开启 |
| 禁止删除分支 | ✅ 开启 |
| 合并策略 | **仅允许 Squash Merge**（保持历史整洁） |

### `develop` 分支

| 规则 | 配置值 |
|------|--------|
| 需要 PR 合并 | ✅ 开启 |
| 审批人数量 | **1 人** |
| 需要 CI 通过 | ✅ 开启 |
| 禁止强制推送 | ✅ 开启 |
| 合并策略 | Squash Merge 或 Merge Commit |

### `release/*` 分支

| 规则 | 配置值 |
|------|--------|
| 需要 PR 合并 | ✅ 开启 |
| 审批人数量 | **2 人** |
| 禁止强制推送 | ✅ 开启 |

## GitHub Environment 配置（四眼原则）

### `sandbox-promote` Environment

用于 Sandbox 转正门禁流水线的自动校验阶段：
- **Required reviewers**: 无（自动通过）
- **Deployment branches**: develop, release/*

### `model-production-approve` Environment

用于模型转正人工审核阶段（四眼原则）：
- **Required reviewers**: 至少 2 名具有 `MODEL_REVIEWER` 角色的账号
- **Prevent self-review**: 开启（禁止提交者自己审核）
- **Deployment branches**: develop, release/*
- **Wait timer**: 0 分钟（立即等待审批）

## Conventional Commits 提交规范

格式：`<type>(<scope>): <subject>`

### Type 说明

| type | 含义 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `refactor` | 代码重构（不影响功能） |
| `perf` | 性能优化 |
| `test` | 测试相关 |
| `docs` | 文档更新 |
| `chore` | 构建/依赖变更 |
| `ci` | CI/CD 流水线变更 |
| `revert` | 回滚提交 |

### 提交示例

```bash
# 新功能
feat(scene-config): 实现场景配置乐观锁更新接口

# Bug 修复
fix(alarm-judge): 修复 Sandbox 拦截器在连续帧确认时的竞态条件

# 数据库变更（必须同步更新 DB 设计文档）
chore(db): 新增 replay_session 表 V2.1 Flyway 迁移脚本

TICKET-S0-03
```

### 本地配置 commitlint

```bash
# 安装
npm install -g @commitlint/cli @commitlint/config-conventional

# 安装 git hook（在项目根目录执行）
npm install --save-dev husky
npx husky install
npx husky add .husky/commit-msg 'npx commitlint --edit $1'
```
