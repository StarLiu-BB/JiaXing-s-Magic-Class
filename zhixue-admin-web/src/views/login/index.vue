<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h2 class="login-title">{{ appTitle }}</h2>
        <p class="login-subtitle">欢迎登录</p>
      </div>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        autocomplete="on"
        label-position="left"
      >
        <el-form-item prop="username">
          <span class="svg-container">
            <el-icon><User /></el-icon>
          </span>
          <el-input
            ref="usernameRef"
            v-model="loginForm.username"
            placeholder="用户名"
            name="username"
            type="text"
            tabindex="1"
            autocomplete="on"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-tooltip v-model:visible="capsTooltip" content="大写锁定已开启" placement="right" manual>
          <el-form-item prop="password">
            <span class="svg-container">
              <el-icon><Lock /></el-icon>
            </span>
            <el-input
              :key="passwordType"
              ref="passwordRef"
              v-model="loginForm.password"
              :type="passwordType"
              placeholder="密码"
              name="password"
              tabindex="2"
              autocomplete="on"
              @keyup.enter="handleLogin"
              @blur="capsTooltip = false"
              @keyup="checkCapslock"
            />
            <span class="show-pwd" @click="showPwd">
              <el-icon>
                <component :is="passwordType === 'password' ? 'View' : 'Hide'" />
              </el-icon>
            </span>
          </el-form-item>
        </el-tooltip>

        <el-form-item prop="code">
          <span class="svg-container">
            <el-icon><Key /></el-icon>
          </span>
          <el-input
            v-model="loginForm.code"
            placeholder="验证码"
            name="code"
            tabindex="3"
            autocomplete="off"
            style="width: 60%"
            @keyup.enter="handleLogin"
          />
          <div class="captcha-image" @click="getCaptcha">
            <img v-if="captchaUrl" :src="captchaUrl" alt="验证码" />
            <span v-else class="captcha-loading">点击获取验证码</span>
          </div>
        </el-form-item>

        <el-checkbox v-model="loginForm.rememberMe" class="remember-me">记住密码</el-checkbox>

        <el-button
          :loading="loading"
          type="primary"
          style="width: 100%; margin-bottom: 30px"
          @click.prevent="handleLogin"
        >
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Key, View, Hide } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/modules/user'
import { getCaptcha as getCaptchaApi, login as loginApi } from '@/api/auth'
import { validUsername } from '@/utils/validate'
import { getToken, setToken, removeToken } from '@/utils/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const appTitle = ref(import.meta.env.VITE_APP_TITLE || '智学平台管理系统')

const loginFormRef = ref(null)
const usernameRef = ref(null)
const passwordRef = ref(null)
const passwordType = ref('password')
const capsTooltip = ref(false)
const loading = ref(false)
const captchaUrl = ref('')
const captchaUuid = ref('')

const loginForm = reactive({
  username: '',
  password: '',
  code: '',
  uuid: '',
  rememberMe: false
})

// 表单验证规则
const validateUsername = (rule, value, callback) => {
  if (!validUsername(value)) {
    callback(new Error('请输入正确的用户名'))
  } else {
    callback()
  }
}

const validatePassword = (rule, value, callback) => {
  if (value.length < 6) {
    callback(new Error('密码不能少于6位'))
  } else {
    callback()
  }
}

const validateCode = (rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入验证码'))
  } else {
    callback()
  }
}

const loginRules = reactive({
  username: [{ required: true, trigger: 'blur', validator: validateUsername }],
  password: [{ required: true, trigger: 'blur', validator: validatePassword }],
  code: [{ required: true, trigger: 'blur', validator: validateCode }]
})

// 显示/隐藏密码
const showPwd = () => {
  if (passwordType.value === 'password') {
    passwordType.value = ''
  } else {
    passwordType.value = 'password'
  }
  nextTick(() => {
    passwordRef.value.focus()
  })
}

// 检查大写锁定
const checkCapslock = (e) => {
  const { key } = e
  capsTooltip.value = key && key.length === 1 && key >= 'A' && key <= 'Z'
}

// 获取验证码
const getCaptcha = async () => {
  try {
    const response = await getCaptchaApi()
    // 后端返回格式: { code: 200, data: { img: "...", uuid: "..." } }
    const data = response.data || response
    
    if (data.img) {
      captchaUrl.value = data.img.startsWith('data:') 
        ? data.img 
        : `data:image/png;base64,${data.img}`
    } else if (data.captchaImg) {
      captchaUrl.value = data.captchaImg.startsWith('data:')
        ? data.captchaImg
        : `data:image/png;base64,${data.captchaImg}`
    }
    
    captchaUuid.value = data.uuid || data.captchaUuid || ''
    loginForm.uuid = captchaUuid.value
  } catch (error) {
    console.error('获取验证码失败:', error)
    ElMessage.error('获取验证码失败，请重试')
  }
}

// 加载记住的密码
const loadRememberedPassword = () => {
  const rememberedUsername = localStorage.getItem('remembered_username')
  const rememberedPassword = localStorage.getItem('remembered_password')
  
  if (rememberedUsername && rememberedPassword) {
    loginForm.username = rememberedUsername
    loginForm.password = rememberedPassword
    loginForm.rememberMe = true
  }
}

// 保存记住的密码
const saveRememberedPassword = () => {
  if (loginForm.rememberMe) {
    localStorage.setItem('remembered_username', loginForm.username)
    localStorage.setItem('remembered_password', loginForm.password)
  } else {
    localStorage.removeItem('remembered_username')
    localStorage.removeItem('remembered_password')
  }
}

// 登录处理
const handleLogin = () => {
  loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      
      try {
        // 保存记住密码
        saveRememberedPassword()
        
        // 调用登录接口（登录接口已返回用户信息，不需要再调用 getInfo）
        await userStore.login({
          username: loginForm.username,
          password: loginForm.password,
          code: loginForm.code,
          uuid: loginForm.uuid
        })
        
        ElMessage.success('登录成功')
        
        // 跳转到首页或之前访问的页面
        const redirect = route.query.redirect || '/'
        router.push(redirect)
      } catch (error) {
        console.error('登录失败:', error)
        // 刷新验证码
        await getCaptcha()
        loginForm.code = ''
      } finally {
        loading.value = false
      }
    } else {
      console.log('表单验证失败')
      return false
    }
  })
}

// 组件挂载时
onMounted(() => {
  // 如果已登录，直接跳转
  if (userStore.token) {
    router.push('/')
    return
  }
  
  // 加载记住的密码
  loadRememberedPassword()
  
  // 获取验证码
  getCaptcha()
  
  // 聚焦用户名输入框
  if (loginForm.username === '') {
    usernameRef.value.focus()
  } else {
    passwordRef.value.focus()
  }
})
</script>

<style lang="scss" scoped>
.login-container {
  min-height: 100vh;
  width: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  overflow: hidden;
  display: flex;
  justify-content: center;
  align-items: center;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1440 320"><path fill="%23ffffff" fill-opacity="0.1" d="M0,96L48,112C96,128,192,160,288,160C384,160,480,128,576,122.7C672,117,768,139,864,154.7C960,171,1056,181,1152,165.3C1248,149,1344,107,1392,85.3L1440,64L1440,320L1392,320C1344,320,1248,320,1152,320C1056,320,960,320,864,320C768,320,672,320,576,320C480,320,384,320,288,320C192,320,96,320,48,320L0,320Z"></path></svg>')
      no-repeat bottom;
    background-size: cover;
  }
}

.login-box {
  position: relative;
  width: 450px;
  max-width: 100%;
  padding: 40px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.1);
  backdrop-filter: blur(10px);
  z-index: 1;

  .login-header {
    text-align: center;
    margin-bottom: 40px;

    .login-title {
      font-size: 32px;
      font-weight: bold;
      color: #333;
      margin: 0 0 10px 0;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
    }

    .login-subtitle {
      font-size: 16px;
      color: #999;
      margin: 0;
    }
  }

  .login-form {
    .el-form-item {
      margin-bottom: 25px;

      .svg-container {
        padding: 6px 5px 6px 15px;
        color: #889aa4;
        vertical-align: middle;
        width: 30px;
        display: inline-block;
      }

      .el-input {
        display: inline-block;
        height: 47px;
        width: calc(100% - 30px);

        :deep(.el-input__wrapper) {
          padding-left: 0;
          box-shadow: none;
          border-bottom: 1px solid #dcdfe6;
          border-radius: 0;

          &.is-focus {
            border-bottom-color: #409eff;
          }
        }

        :deep(input) {
          background: transparent;
          border: 0px;
          border-radius: 0px;
          padding: 12px 5px 12px 15px;
          color: #333;
          height: 47px;
          caret-color: #409eff;

          &:-webkit-autofill {
            box-shadow: 0 0 0px 1000px transparent inset !important;
            -webkit-text-fill-color: #333 !important;
          }
        }
      }

      .show-pwd {
        position: absolute;
        right: 10px;
        top: 7px;
        font-size: 16px;
        color: #889aa4;
        cursor: pointer;
        user-select: none;
      }
    }

    .captcha-image {
      display: inline-block;
      width: 35%;
      height: 47px;
      margin-left: 5%;
      vertical-align: middle;
      cursor: pointer;
      border: 1px solid #dcdfe6;
      border-radius: 4px;
      overflow: hidden;
      background: #f5f7fa;
      transition: all 0.3s;

      &:hover {
        border-color: #409eff;
      }

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }

      .captcha-loading {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100%;
        font-size: 12px;
        color: #999;
      }
    }

    .remember-me {
      margin-bottom: 20px;
      color: #606266;
    }
  }
}

// 响应式适配
@media (max-width: 768px) {
  .login-box {
    width: 90%;
    padding: 30px 20px;
    margin: 20px;

    .login-header {
      .login-title {
        font-size: 24px;
      }
    }
  }
}
</style>

