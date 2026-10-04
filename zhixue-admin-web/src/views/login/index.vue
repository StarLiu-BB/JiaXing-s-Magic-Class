<template>
  <div class="login-page">
    <!-- 品牌面板：只陈述后台真实具备的能力 -->
    <aside class="brand-panel">
      <div class="brand-top">
        <BrandMark :size="36" inverse />
        <div class="brand-name">
          <span>智学云</span>
          <small>运营后台</small>
        </div>
      </div>

      <div class="brand-copy">
        <h1>课程、交易与学员互动，<br />在一处管理。</h1>
        <ul class="capabilities">
          <li v-for="item in capabilities" :key="item.title">
            <span class="cap-icon"><el-icon :size="16"><component :is="item.icon" /></el-icon></span>
            <div>
              <strong>{{ item.title }}</strong>
              <span>{{ item.desc }}</span>
            </div>
          </li>
        </ul>
      </div>

      <div class="brand-foot">© {{ year }} 智学云</div>
    </aside>

    <!-- 登录表单 -->
    <main class="form-panel">
      <div class="form-wrap">
        <div class="mobile-brand">
          <BrandMark :size="32" />
          <span>智学云</span>
        </div>

        <header class="form-head">
          <h2>登录{{ appTitle }}</h2>
          <p>请使用管理员或教师账号登录</p>
        </header>

        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          label-position="top"
          size="large"
          class="login-form"
          autocomplete="on"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="账号" prop="username">
            <el-input
              ref="usernameRef"
              v-model="loginForm.username"
              placeholder="请输入账号"
              name="username"
              autocomplete="username"
              :prefix-icon="User"
              @keyup.enter="handleLogin"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-tooltip :visible="capsTooltip" content="大写锁定已开启" placement="right">
              <el-input
                ref="passwordRef"
                v-model="loginForm.password"
                type="password"
                show-password
                placeholder="请输入密码"
                name="password"
                autocomplete="current-password"
                :prefix-icon="Lock"
                @keyup.enter="handleLogin"
                @keyup="checkCapslock"
                @blur="capsTooltip = false"
              />
            </el-tooltip>
          </el-form-item>

          <el-form-item label="验证码" prop="code">
            <div class="captcha-row">
              <el-input
                v-model="loginForm.code"
                placeholder="右侧字符"
                name="code"
                autocomplete="off"
                maxlength="8"
                :prefix-icon="Key"
                @keyup.enter="handleLogin"
              />
              <button
                type="button"
                class="captcha-image"
                title="看不清？点击换一张"
                aria-label="刷新验证码"
                @click="getCaptcha"
              >
                <img v-if="captchaUrl" :src="captchaUrl" alt="验证码" />
                <span v-else>获取验证码</span>
              </button>
            </div>
          </el-form-item>

          <div class="form-extra">
            <el-checkbox v-model="loginForm.rememberMe">记住账号</el-checkbox>
          </div>

          <el-button :loading="loading" type="primary" native-type="submit" class="submit-btn">
            {{ loading ? '登录中' : '登录' }}
          </el-button>
        </el-form>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key, Reading, Tickets, ChatLineSquare } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/modules/user'
import { getCaptcha as getCaptchaApi } from '@/api/auth'
import { validUsername } from '@/utils/validate'
import BrandMark from '@/components/BrandMark/index.vue'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const appTitle = import.meta.env.VITE_APP_TITLE || '智学平台管理系统'
const year = new Date().getFullYear()
const capabilities = [
  { icon: Reading, title: '课程与章节', desc: '发布、上下架与课时编排' },
  { icon: Tickets, title: '交易', desc: '订单、退款与限时秒杀' },
  { icon: ChatLineSquare, title: '内容安全', desc: '弹幕 AI 审核与人工复核' }
]

// 只记住账号。原实现把明文密码写进 localStorage，任何一次 XSS 都能直接读走
const REMEMBER_KEY = 'remembered_username'
const LEGACY_PASSWORD_KEY = 'remembered_password'

const loginFormRef = ref(null)
const usernameRef = ref(null)
const passwordRef = ref(null)
const capsTooltip = ref(false)
const loading = ref(false)
const captchaUrl = ref('')

const loginForm = reactive({
  username: '',
  password: '',
  code: '',
  uuid: '',
  rememberMe: false
})

const validateUsername = (rule, value, callback) => {
  if (!validUsername(value)) {
    callback(new Error('请输入正确的账号'))
  } else {
    callback()
  }
}

const validatePassword = (rule, value, callback) => {
  if (!value || value.length < 6) {
    callback(new Error('密码不能少于 6 位'))
  } else {
    callback()
  }
}

const loginRules = reactive({
  username: [{ required: true, trigger: 'blur', validator: validateUsername }],
  password: [{ required: true, trigger: 'blur', validator: validatePassword }],
  code: [{ required: true, trigger: 'blur', message: '请输入验证码' }]
})

const checkCapslock = (e) => {
  capsTooltip.value = typeof e.getModifierState === 'function' && e.getModifierState('CapsLock')
}

const getCaptcha = async () => {
  try {
    const response = await getCaptchaApi()
    const data = response.data || response
    const img = data.img || data.captchaImg
    if (img) {
      captchaUrl.value = img.startsWith('data:') ? img : `data:image/png;base64,${img}`
    }
    loginForm.uuid = data.uuid || data.captchaUuid || ''
  } catch (error) {
    console.error('获取验证码失败:', error)
    ElMessage.error('获取验证码失败，请点击图片重试')
  }
}

const loadRememberedUsername = () => {
  localStorage.removeItem(LEGACY_PASSWORD_KEY)
  const remembered = localStorage.getItem(REMEMBER_KEY)
  if (remembered) {
    loginForm.username = remembered
    loginForm.rememberMe = true
  }
}

const saveRememberedUsername = () => {
  if (loginForm.rememberMe) {
    localStorage.setItem(REMEMBER_KEY, loginForm.username)
  } else {
    localStorage.removeItem(REMEMBER_KEY)
  }
}

const handleLogin = () => {
  if (loading.value) return
  loginFormRef.value.validate(async (valid) => {
    if (!valid) return
    loading.value = true
    try {
      saveRememberedUsername()
      await userStore.login({
        username: loginForm.username,
        password: loginForm.password,
        code: loginForm.code,
        uuid: loginForm.uuid
      })
      await userStore.getInfo()
      ElMessage.success('登录成功')
      router.push(route.query.redirect || '/')
    } catch (error) {
      console.error('登录失败:', error)
      loginForm.code = ''
      await getCaptcha()
    } finally {
      loading.value = false
    }
  })
}

onMounted(() => {
  if (userStore.token) {
    router.push('/')
    return
  }
  loadRememberedUsername()
  getCaptcha()
  if (loginForm.username) {
    passwordRef.value?.focus()
  } else {
    usernameRef.value?.focus()
  }
})
</script>

<style lang="scss" scoped>
.login-page {
  display: grid;
  grid-template-columns: minmax(420px, 44%) 1fr;
  min-height: 100dvh;
  background: var(--zx-surface);
}

// 品牌面板：深 slate 底 + 极淡网格 + 单一强调色的环境光，不使用紫蓝渐变
.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 40px 48px;
  overflow: hidden;
  color: #e2e8f0;
  background-color: #0f172a;
  background-image:
    radial-gradient(60% 50% at 0% 0%, color-mix(in srgb, var(--el-color-primary) 38%, transparent) 0%, transparent 70%),
    radial-gradient(40% 40% at 100% 100%, color-mix(in srgb, var(--el-color-primary) 16%, transparent) 0%, transparent 70%),
    linear-gradient(rgba(148, 163, 184, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(148, 163, 184, 0.06) 1px, transparent 1px);
  background-size: auto, auto, 32px 32px, 32px 32px;
}

.brand-top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-name {
  display: flex;
  flex-direction: column;
  line-height: 1.2;

  span {
    font-size: 17px;
    font-weight: 650;
    color: #fff;
    letter-spacing: 0.02em;
  }

  small {
    font-size: 12px;
    color: #94a3b8;
  }
}

.brand-copy {
  max-width: 440px;

  h1 {
    color: #fff;
    font-size: 30px;
    line-height: 1.35;
    font-weight: 650;
    letter-spacing: -0.01em;
  }
}

.capabilities {
  list-style: none;
  margin-top: 36px;
  display: grid;
  gap: 18px;

  li {
    display: flex;
    gap: 14px;
    align-items: flex-start;
  }

  strong {
    display: block;
    color: #f1f5f9;
    font-size: 14px;
    font-weight: 600;
  }

  span {
    font-size: 13px;
    color: #94a3b8;
  }
}

.cap-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  color: #bfdbfe !important;
  background: rgba(148, 163, 184, 0.12);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.brand-foot {
  font-size: 12px;
  color: #64748b;
}

// 表单面板
.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.form-wrap {
  width: 100%;
  max-width: 380px;
}

.mobile-brand {
  display: none;
  align-items: center;
  gap: 10px;
  margin-bottom: 28px;
  font-size: 17px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.form-head {
  margin-bottom: 28px;

  h2 {
    font-size: 24px;
    line-height: 1.3;
  }

  p {
    margin-top: 6px;
    font-size: 14px;
    color: var(--el-text-color-secondary);
  }
}

.login-form {
  :deep(.el-form-item) {
    margin-bottom: 20px;
  }

  :deep(.el-form-item__label) {
    margin-bottom: 6px;
    line-height: 20px;
    font-size: 13px;
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

.captcha-image {
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
  transition: border-color 0.16s var(--zx-ease);

  &:hover {
    border-color: var(--el-color-primary-light-5);
  }

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

.form-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -4px 0 20px;
}

.submit-btn {
  width: 100%;
  height: 42px;
  font-size: 15px;
}

@media (max-width: 960px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }

  .mobile-brand {
    display: flex;
  }
}
</style>
