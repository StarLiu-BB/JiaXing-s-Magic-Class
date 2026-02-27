import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { login as loginApi, getInfo as getInfoApi, logout as logoutApi } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  // State
  const token = ref(getToken() || '')
  const userInfo = ref({})
  const roles = ref([])
  const permissions = ref([])

  // Getters
  const hasToken = computed(() => !!token.value)

  // Actions
  /**
   * 登录
   */
  async function login(loginForm) {
    try {
      const response = await loginApi(
        loginForm.username,
        loginForm.password,
        loginForm.code,
        loginForm.uuid
      )
      // 后端返回格式: { code: 200, data: { token: "...", user: {...} } }
      const data = response.data || response
      const accessToken = data.token
      token.value = accessToken
      setToken(accessToken)
      
      // 如果返回了用户信息，也保存
      if (data.user) {
        userInfo.value = data.user
        if (data.user.roles) {
          roles.value = data.user.roles
        }
      }
      
      return Promise.resolve(response)
    } catch (error) {
      return Promise.reject(error)
    }
  }

  /**
   * 获取用户信息
   */
  async function getInfo() {
    try {
      const response = await getInfoApi()
      const { roles: userRoles, permissions: userPermissions, ...info } = response
      
      // 验证返回的 roles 是否为数组
      if (!userRoles || userRoles.length === 0) {
        throw new Error('getInfo: roles must be a non-null array!')
      }
      
      roles.value = userRoles
      permissions.value = userPermissions || []
      userInfo.value = info
      
      return Promise.resolve(response)
    } catch (error) {
      return Promise.reject(error)
    }
  }

  /**
   * 退出登录
   */
  async function logout() {
    try {
      await logoutApi()
      resetToken()
      return Promise.resolve()
    } catch (error) {
      resetToken()
      return Promise.reject(error)
    }
  }

  /**
   * 重置 Token
   */
  function resetToken() {
    token.value = ''
    userInfo.value = {}
    roles.value = []
    permissions.value = []
    removeToken()
  }

  /**
   * 设置 Token
   */
  function setTokenValue(newToken) {
    token.value = newToken
    setToken(newToken)
  }

  /**
   * 设置用户信息
   */
  function setUserInfoValue(info) {
    userInfo.value = info
    if (info.roles) {
      roles.value = info.roles
    }
    if (info.permissions) {
      permissions.value = info.permissions
    }
  }

  return {
    // State
    token,
    userInfo,
    roles,
    permissions,
    // Getters
    hasToken,
    // Actions
    login,
    getInfo,
    logout,
    resetToken,
    setToken: setTokenValue,
    setUserInfo: setUserInfoValue
  }
})

