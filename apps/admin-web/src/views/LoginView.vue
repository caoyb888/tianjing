<template>
  <div class="login-page">
    <div class="login-container">
      <div class="login-header">
        <div class="brand-logo">天镜</div>
        <h1 class="brand-title">天柱·天镜</h1>
        <p class="brand-subtitle">工业视觉 AI 推理平台</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            :prefix-icon="User"
            size="large"
            autocomplete="username"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
            size="large"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          :loading="loading"
          class="login-btn"
          @click="handleLogin"
        >
          登 录
        </el-button>
      </el-form>

      <div class="login-footer">
        © 2026 河北天柱钢铁集团 · 天柱·天镜视觉 AI 平台 v0.1.0
      </div>
    </div>

    <!-- 背景装饰 -->
    <div class="login-bg">
      <div class="bg-circle circle-1" />
      <div class="bg-circle circle-2" />
      <div class="bg-circle circle-3" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const notification = useNotificationStore()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authStore.login(form.username, form.password)
    notification.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    router.push(redirect)
  } catch {
    // 错误已由 request 拦截器处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  // 工业蓝渐变背景
  background: linear-gradient(135deg, #0D1B2E 0%, #1557B0 60%, #00B4C6 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.login-container {
  width: 420px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: var(--tj-radius-lg);
  padding: 48px 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4);
  position: relative;
  z-index: 1;
  // 毛玻璃效果（现代浏览器均支持）
  backdrop-filter: blur(10px);
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.brand-logo {
  width: 60px;
  height: 60px;
  background: linear-gradient(135deg, var(--tj-primary), var(--tj-accent));
  border-radius: var(--tj-radius-lg);
  color: #fff;
  font-size: 20px;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 16px;
}

.brand-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--tj-text-primary);
  margin-bottom: 6px;
  letter-spacing: 0.5px;
}

.brand-subtitle {
  font-size: 13px;
  color: var(--tj-text-secondary);
  margin-top: 4px;
}

.login-form {
  .el-form-item {
    margin-bottom: 20px;
  }
}

.login-btn {
  width: 100%;
  height: 44px;
  background: var(--tj-primary);
  border-color: var(--tj-primary);
  font-size: 15px;
  letter-spacing: 2px;
  margin-top: 8px;

  &:hover {
    background: var(--tj-primary-dark);
    border-color: var(--tj-primary-dark);
  }
}

.login-footer {
  text-align: center;
  font-size: var(--tj-font-xs);
  color: var(--tj-text-placeholder);
  margin-top: 24px;
}

// 背景装饰圆（使用科技青色调）
.bg-circle {
  position: absolute;
  border-radius: 50%;
  background: rgba(0, 180, 198, 0.12);
}

.circle-1 {
  width: 400px;
  height: 400px;
  top: -100px;
  right: -100px;
}

.circle-2 {
  width: 300px;
  height: 300px;
  bottom: -80px;
  left: -80px;
  background: rgba(21, 87, 176, 0.15);
}

.circle-3 {
  width: 200px;
  height: 200px;
  top: 40%;
  left: 10%;
  background: rgba(255, 255, 255, 0.05);
}
</style>
