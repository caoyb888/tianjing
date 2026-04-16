import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import NProgress from 'nprogress'
import { useAuthStore } from '@/stores/auth'
import { UserRole } from '@/types'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '登录', requiresAuth: false },
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        redirect: '/dashboard',
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '数据看板', icon: 'DataAnalysis' },
      },
      // 场景管理
      {
        path: 'scenes',
        name: 'SceneList',
        component: () => import('@/views/scene/SceneListView.vue'),
        meta: { title: '场景列表', icon: 'Film' },
      },
      {
        path: 'scenes/new',
        name: 'SceneCreate',
        component: () => import('@/views/scene/SceneFormView.vue'),
        meta: { title: '新建场景', roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR] },
      },
      {
        path: 'scenes/:sceneId',
        name: 'SceneDetail',
        component: () => import('@/views/scene/SceneDetailView.vue'),
        meta: { title: '场景详情' },
      },
      {
        path: 'scenes/:sceneId/edit',
        name: 'SceneEdit',
        component: () => import('@/views/scene/SceneFormView.vue'),
        meta: { title: '编辑场景', roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR] },
      },
      {
        path: 'scenes/:sceneId/history',
        name: 'SceneHistory',
        component: () => import('@/views/scene/SceneHistoryView.vue'),
        meta: { title: '配置历史' },
      },
      {
        path: 'scenes/:sceneId/workflow',
        name: 'SceneWorkflow',
        component: () => import('@/views/scene/SceneWorkflowView.vue'),
        meta: { title: '低代码编排', roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR] },
      },
      // 设备管理
      {
        path: 'devices',
        name: 'DeviceList',
        component: () => import('@/views/device/DeviceListView.vue'),
        meta: { title: '设备管理', icon: 'VideoCamera' },
      },
      {
        path: 'devices/new',
        name: 'DeviceCreate',
        component: () => import('@/views/device/DeviceFormView.vue'),
        meta: { title: '注册设备', roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR] },
      },
      {
        path: 'devices/:deviceCode',
        name: 'DeviceDetail',
        component: () => import('@/views/device/DeviceDetailView.vue'),
        meta: { title: '设备详情' },
      },
      {
        path: 'devices/:deviceCode/edit',
        name: 'DeviceEdit',
        component: () => import('@/views/device/DeviceFormView.vue'),
        meta: { title: '编辑设备', roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR] },
      },
      {
        path: 'devices/:deviceCode/health',
        name: 'DeviceHealth',
        component: () => import('@/views/device/DeviceHealthView.vue'),
        meta: { title: '健康历史' },
      },
      // 标定工具
      {
        path: 'calibration/:sceneId',
        name: 'Calibration',
        component: () => import('@/views/CalibrationView.vue'),
        meta: { title: '在线标定', roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR] },
      },
      // 算法管理
      {
        path: 'algorithms',
        name: 'AlgorithmList',
        component: () => import('@/views/algorithm/AlgorithmListView.vue'),
        meta: { title: '算法插件', icon: 'Connection' },
      },
      {
        path: 'algorithms/:pluginId',
        name: 'AlgorithmDetail',
        component: () => import('@/views/algorithm/AlgorithmDetailView.vue'),
        meta: { title: '插件详情' },
      },
      // 模型管理
      {
        path: 'models',
        name: 'ModelList',
        component: () => import('@/views/model/ModelListView.vue'),
        meta: { title: '模型版本', icon: 'Files' },
      },
      {
        path: 'models/:versionId',
        name: 'ModelDetail',
        component: () => import('@/views/model/ModelDetailView.vue'),
        meta: { title: '模型详情' },
      },
      // 告警管理
      {
        path: 'alarms',
        name: 'AlarmList',
        component: () => import('@/views/alarm/AlarmListView.vue'),
        meta: { title: '告警管理', icon: 'Bell' },
      },
      {
        path: 'alarms/:alarmId',
        name: 'AlarmDetail',
        component: () => import('@/views/alarm/AlarmDetailView.vue'),
        meta: { title: '告警详情' },
      },
      // Sandbox 实验室
      {
        path: 'sandbox/sessions',
        name: 'SandboxList',
        component: () => import('@/views/sandbox/SandboxListView.vue'),
        meta: {
          title: '实验会话',
          roles: [UserRole.ADMIN, UserRole.SANDBOX_OPERATOR, UserRole.MODEL_REVIEWER],
        },
      },
      {
        path: 'sandbox/sessions/:sessionId',
        name: 'SandboxDetail',
        component: () => import('@/views/sandbox/SandboxDetailView.vue'),
        meta: {
          title: '会话详情',
          roles: [UserRole.ADMIN, UserRole.SANDBOX_OPERATOR, UserRole.MODEL_REVIEWER],
        },
      },
      {
        path: 'sandbox/sessions/:sessionId/report',
        name: 'SandboxReport',
        component: () => import('@/views/sandbox/SandboxReportView.vue'),
        meta: {
          title: '对比报告',
          roles: [UserRole.ADMIN, UserRole.SANDBOX_OPERATOR, UserRole.MODEL_REVIEWER],
        },
      },
      // 仿真任务
      {
        path: 'simulations',
        name: 'SimulationList',
        component: () => import('@/views/simulation/SimulationListView.vue'),
        meta: {
          title: '仿真任务',
          roles: [UserRole.ADMIN, UserRole.SANDBOX_OPERATOR],
        },
      },
      {
        path: 'simulations/:taskId',
        name: 'SimulationDetail',
        component: () => import('@/views/simulation/SimulationDetailView.vue'),
        meta: {
          title: '仿真详情',
          roles: [UserRole.ADMIN, UserRole.SANDBOX_OPERATOR],
        },
      },
      {
        path: 'simulations/:taskId/review',
        name: 'AnnotationReview',
        component: () => import('@/views/simulation/AnnotationReviewView.vue'),
        meta: {
          title: '标注审核',
          roles: [UserRole.ADMIN, UserRole.SANDBOX_OPERATOR, UserRole.SCENE_EDITOR],
        },
      },
      // 训练管理
      {
        path: 'training/datasets',
        name: 'DatasetList',
        component: () => import('@/views/training/DatasetListView.vue'),
        meta: { title: '训练数据集', roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR] },
      },
      {
        path: 'training/datasets/:datasetCode',
        name: 'DatasetDetail',
        component: () => import('@/views/training/DatasetDetailView.vue'),
        meta: { title: '数据集详情', roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR] },
      },
      {
        path: 'training/jobs',
        name: 'TrainingJobList',
        component: () => import('@/views/training/TrainingJobListView.vue'),
        meta: { title: '训练作业', roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR] },
      },
      {
        path: 'training/jobs/:jobId',
        name: 'TrainingJobDetail',
        component: () => import('@/views/training/TrainingJobDetailView.vue'),
        meta: { title: '作业详情', roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR] },
      },
      // 感知健康看板
      {
        path: 'health-monitor',
        name: 'HealthMonitor',
        component: () => import('@/views/HealthMonitorView.vue'),
        meta: { title: '感知健康看板', icon: 'Monitor' },
      },
      // 漂移监测
      {
        path: 'drift',
        name: 'DriftMonitor',
        component: () => import('@/views/DriftMonitorView.vue'),
        meta: {
          title: '漂移监测',
          roles: [UserRole.ADMIN, UserRole.SCENE_EDITOR, UserRole.MODEL_REVIEWER],
        },
      },
      // 审计与系统
      {
        path: 'audit/data-sync',
        name: 'DataSyncAudit',
        component: () => import('@/views/audit/DataSyncAuditView.vue'),
        meta: { title: '数据同步审计', roles: [UserRole.ADMIN] },
      },
      {
        path: 'system/users',
        name: 'UserManagement',
        component: () => import('@/views/system/UserManagementView.vue'),
        meta: { title: '用户管理', roles: [UserRole.ADMIN] },
      },
      {
        path: 'system/logs',
        name: 'SystemLogs',
        component: () => import('@/views/system/SystemLogsView.vue'),
        meta: { title: '操作日志', roles: [UserRole.ADMIN] },
      },
    ],
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { title: '无权限', requiresAuth: false },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '页面不存在', requiresAuth: false },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

// 全局前置守卫
router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  document.title = `${to.meta.title ? to.meta.title + ' - ' : ''}天柱·天镜`

  const requiresAuth = to.meta.requiresAuth !== false
  const authStore = useAuthStore()

  if (!requiresAuth) {
    // 已登录用户访问登录页，跳转首页
    if (to.name === 'Login' && authStore.isLoggedIn) {
      return next('/dashboard')
    }
    return next()
  }

  // 未登录
  if (!authStore.isLoggedIn) {
    return next({ name: 'Login', query: { redirect: to.fullPath } })
  }

  // 获取用户信息（刷新后 store 为空）
  if (!authStore.userInfo) {
    await authStore.fetchUserInfo()
    if (!authStore.userInfo) {
      return next({ name: 'Login' })
    }
  }

  // 权限检查
  const requiredRoles = to.meta.roles as UserRole[] | undefined
  if (requiredRoles && requiredRoles.length > 0) {
    if (!authStore.hasRole(requiredRoles)) {
      return next({ name: 'Forbidden' })
    }
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
