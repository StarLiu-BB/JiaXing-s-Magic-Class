<template>
  <div class="login-page">
    <main class="login-card">
      <div class="brand">
        <BrandMark :size="36" />
        <span>智学云</span>
      </div>

      <header class="head">
        <h1>登录学习</h1>
        <p>登录后可以向 AI 课程助教提问，并获得相关的视频课推荐</p>
      </header>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        autocomplete="on"
        @submit.prevent="submit"
      >
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" name="username" autocomplete="username" placeholder="请输入账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            name="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item label="验证码" prop="code">
          <div class="captcha-row">
            <el-input v-model="form.code" maxlength="8" autocomplete="off" placeholder="右侧字符" :prefix-icon="Key" />
            <button type="button" class="captcha" title="看不清？点击换一张" aria-label="刷新验证码" @click="refreshCaptcha">
              <img v-if="captcha" :src="captcha" alt="验证码" />
              <span v-else>获取验证码</span>
            </button>
          </div>
        </el-form-item>

        <p v-if="error" class="form-error" role="alert">{{ error }}</p>

        <el-button type="primary" native-type="submit" class="submit" :loading="loading">
          {{ loading ? '登录中' : '登录' }}
        </el-button>
      </el-form>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Key, Lock, User } from '@element-plus/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import { getCaptcha } from '@/api/auth'
import { safeRedirect } from '@/router/guard'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const user = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const error = ref('')
const captcha = ref('')
const form = reactive({ username: '', password: '', code: '', uuid: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function refreshCaptcha() {
  try {
    const data = await getCaptcha()
    captcha.value = data.img?.startsWith('data:') ? data.img : `data:image/png;base64,${data.img}`
    form.uuid = data.uuid
    form.code = ''
  } catch {
    error.value = '获取验证码失败，请点击图片重试'
  }
}

async function submit() {
  if (loading.value) {
    return
  }
  error.value = ''
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    await user.login(form)
    await user.loadProfile()
    router.replace(safeRedirect(route.query.redirect))
  } catch (e) {
    error.value = e.message || '登录失败，请重试'
    await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (user.isLoggedIn) {
    router.replace(safeRedirect(route.query.redirect))
    return
  }
  refreshCaptcha()
})
</script>

<style lang="scss" scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100dvh;
  padding: 32px 16px;
  background:
    radial-gradient(50% 40% at 50% 0%, color-mix(in srgb, var(--el-color-primary) 10%, transparent) 0%, transparent 100%),
    var(--el-fill-color-lighter);
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: 32px 32px 28px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  background: var(--zx-surface);
  box-shadow: var(--el-box-shadow-light);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.head {
  margin: 22px 0 22px;

  h1 {
    font-size: 22px;
  }

  p {
    margin-top: 6px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;

  .el-input {
    flex: 1;
    min-width: 0;
  }
}

.captcha {
  width: 120px;
  height: 40px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  cursor: pointer;

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.form-error {
  margin: -4px 0 14px;
  font-size: 13px;
  color: var(--el-color-danger);
}

.submit {
  width: 100%;
  height: 42px;
  font-size: 15px;
}
</style>
