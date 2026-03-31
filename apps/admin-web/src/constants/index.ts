import { AlarmLevel, Factory, SceneCategory, ModelStatus, UserRole } from '@/types'

// 告警级别配置
export const ALARM_LEVEL_CONFIG = {
  [AlarmLevel.CRITICAL]: {
    label: '严重',
    type: 'danger' as const,
    color: '#f56c6c',
  },
  [AlarmLevel.WARNING]: {
    label: '警告',
    type: 'warning' as const,
    color: '#e6a23c',
  },
  [AlarmLevel.INFO]: {
    label: '信息',
    type: 'info' as const,
    color: '#909399',
  },
}

// 工厂厂部配置
export const FACTORY_CONFIG = {
  [Factory.PELLET]: { label: '球团厂', prefix: 'SCENE-PELLET' },
  [Factory.SINTERING]: { label: '烧结厂', prefix: 'SCENE-SINTER' },
  [Factory.STEEL]: { label: '炼钢厂', prefix: 'SCENE-STEEL' },
  [Factory.SECTION]: { label: '型钢厂', prefix: 'SCENE-SECTION' },
  [Factory.STRIP]: { label: '带钢厂', prefix: 'SCENE-STRIP' },
}

// 场景类别配置
export const SCENE_CATEGORY_CONFIG = {
  [SceneCategory.QUALITY]: { label: '质量检测', icon: 'Checked' },
  [SceneCategory.EQUIPMENT]: { label: '设备监测', icon: 'Monitor' },
  [SceneCategory.PROCESS]: { label: '工艺参数', icon: 'Setting' },
}

// 模型状态配置
export const MODEL_STATUS_CONFIG = {
  [ModelStatus.PENDING_REVIEW]: { label: '待审核', type: 'info' as const },
  [ModelStatus.SANDBOX_TESTING]: { label: 'Sandbox 测试中', type: 'warning' as const },
  [ModelStatus.APPROVED]: { label: '已通过', type: 'success' as const },
  [ModelStatus.REJECTED]: { label: '已拒绝', type: 'danger' as const },
  [ModelStatus.PRODUCTION]: { label: '生产中', type: 'success' as const },
  [ModelStatus.DEPRECATED]: { label: '已废弃', type: 'info' as const },
}

// 角色配置
export const ROLE_CONFIG = {
  [UserRole.ADMIN]: { label: '系统管理员', color: '#6366f1' },
  [UserRole.SCENE_EDITOR]: { label: '场景编辑员', color: '#3b82f6' },
  [UserRole.MODEL_REVIEWER]: { label: '模型审核员', color: '#10b981' },
  [UserRole.SANDBOX_OPERATOR]: { label: 'Sandbox 操作员', color: '#f59e0b' },
  [UserRole.VIEWER]: { label: '只读用户', color: '#6b7280' },
}

// 权限矩阵
export const PERMISSION_MATRIX = {
  // 场景管理
  'scene:create': [UserRole.ADMIN, UserRole.SCENE_EDITOR],
  'scene:edit': [UserRole.ADMIN, UserRole.SCENE_EDITOR],
  'scene:delete': [UserRole.ADMIN],
  'scene:enable': [UserRole.ADMIN, UserRole.SCENE_EDITOR],
  // 模型管理
  'model:submit': [UserRole.ADMIN, UserRole.SCENE_EDITOR],
  'model:review': [UserRole.ADMIN, UserRole.MODEL_REVIEWER],
  'model:promote': [UserRole.ADMIN, UserRole.MODEL_REVIEWER],
  // Sandbox
  'sandbox:manage': [UserRole.ADMIN, UserRole.SANDBOX_OPERATOR],
  'sandbox:promote': [UserRole.ADMIN, UserRole.MODEL_REVIEWER],
  // 系统管理
  'system:manage': [UserRole.ADMIN],
  // 通用只读
  'common:view': [UserRole.ADMIN, UserRole.SCENE_EDITOR, UserRole.MODEL_REVIEWER, UserRole.SANDBOX_OPERATOR, UserRole.VIEWER],
}

// 侧边栏菜单配置
export const SIDEBAR_MENUS = [
  {
    path: '/dashboard',
    title: '数据看板',
    icon: 'DataAnalysis',
    roles: [] as UserRole[], // 空数组代表所有角色可见
  },
  {
    title: '场景管理',
    icon: 'Film',
    roles: [],
    children: [
      { path: '/scenes', title: '场景列表', icon: 'List' },
      { path: '/devices', title: '设备管理', icon: 'VideoCamera' },
    ],
  },
  {
    title: '算法与模型',
    icon: 'Cpu',
    roles: [],
    children: [
      { path: '/algorithms', title: '算法插件', icon: 'Connection' },
      { path: '/models', title: '模型版本', icon: 'Files' },
    ],
  },
  {
    path: '/alarms',
    title: '告警管理',
    icon: 'Bell',
    roles: [],
  },
  {
    title: 'Sandbox 实验室',
    icon: 'Experiment',
    roles: [UserRole.ADMIN, UserRole.SANDBOX_OPERATOR, UserRole.MODEL_REVIEWER],
    children: [
      { path: '/sandbox/sessions', title: '实验会话', icon: 'SetUp' },
      { path: '/simulations', title: '仿真任务', icon: 'VideoPlay' },
    ],
  },
  {
    title: '训练管理',
    icon: 'TrendCharts',
    roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR],
    children: [
      { path: '/training/datasets', title: '训练数据集', icon: 'FolderOpened' },
      { path: '/training/jobs', title: '训练作业', icon: 'Operation' },
    ],
  },
  {
    path: '/drift',
    title: '漂移监测',
    icon: 'Odometer',
    roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR, UserRole.MODEL_REVIEWER],
  },
  {
    title: '审计与系统',
    icon: 'Lock',
    roles: [UserRole.ADMIN],
    children: [
      { path: '/audit/data-sync', title: '数据同步审计', icon: 'DocumentChecked' },
      { path: '/system/users', title: '用户管理', icon: 'User' },
      { path: '/system/logs', title: '操作日志', icon: 'Document' },
    ],
  },
]
