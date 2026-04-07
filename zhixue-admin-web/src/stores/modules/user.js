import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { login as loginApi, getInfo as getInfoApi, logout as logoutApi } from '@/api/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken() || '')
  const userInfo = ref({})
  const roles = ref([])
  const permissions = ref([])
  const infoLoaded = ref(false)
  const routesLoaded = ref(false)

  const hasToken = computed(() => !!token.value)
  const name = computed(() => userInfo.value.username || userInfo.value.name || '')
  const avatar = computed(() => userInfo.value.avatar || '')

  async function login(loginForm) {
    const response = await loginApi(
      loginForm.username,
      loginForm.password,
      loginForm.code,
      loginForm.uuid
    )
    const payload = response?.data || response || {}
    const accessToken = payload.token
    if (!accessToken) {
      throw new Error('登录响应缺少 token')
    }
    token.value = accessToken
    setToken(accessToken)
    infoLoaded.value = false
    routesLoaded.value = false

    const loginUser = payload.user || {}
    if (Object.keys(loginUser).length > 0) {
      setProfile({
        ...loginUser,
        roles: loginUser.roles || [],
        permissions: loginUser.permissions || []
      })
    }
    return response
  }

  async function getInfo() {
    const response = await getInfoApi()
    const payload = response?.data || response || {}
    const userPayload = payload.user || payload

    const userRoles = payload.roles || userPayload.roles || []
    if (!Array.isArray(userRoles) || userRoles.length === 0) {
      throw new Error('用户角色为空，无法完成鉴权')
    }

    const userPermissions = payload.permissions || userPayload.permissions || []
    setProfile({
      ...userPayload,
      roles: userRoles,
      permissions: Array.isArray(userPermissions) ? userPermissions : []
    })
    infoLoaded.value = true
    return userInfo.value
  }

  async function logout() {
    try {
      await logoutApi()
    } catch (error) {
      // Always clear local auth state even if server logout fails.
      console.error('登出接口调用失败，已清理本地状态', error)
    } finally {
      resetToken()
    }
  }

  function resetToken() {
    token.value = ''
    userInfo.value = {}
    roles.value = []
    permissions.value = []
    infoLoaded.value = false
    routesLoaded.value = false
    removeToken()
  }

  function setTokenValue(newToken) {
    token.value = newToken
    setToken(newToken)
    infoLoaded.value = false
    routesLoaded.value = false
  }

  function setProfile(info) {
    const { roles: roleList = [], permissions: permList = [], ...profile } = info || {}
    userInfo.value = profile
    roles.value = Array.isArray(roleList) ? roleList : []
    permissions.value = Array.isArray(permList) ? permList : []
    infoLoaded.value = true
  }

  function markRoutesLoaded(loaded) {
    routesLoaded.value = !!loaded
  }

  return {
    token,
    userInfo,
    roles,
    permissions,
    infoLoaded,
    routesLoaded,
    hasToken,
    name,
    avatar,
    login,
    getInfo,
    logout,
    resetToken,
    setToken: setTokenValue,
    setProfile,
    markRoutesLoaded
  }
})
