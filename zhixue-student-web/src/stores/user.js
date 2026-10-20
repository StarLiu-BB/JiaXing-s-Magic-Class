import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getUserInfo, login as loginApi, logout as logoutApi } from '@/api/auth'
import { getToken, removeToken, setToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  /** GET /auth/user/info：{ userId, username, nickname, avatar, roles, permissions } */
  const profile = ref(null)

  const isLoggedIn = computed(() => !!token.value)
  const displayName = computed(() => profile.value?.nickname || profile.value?.username || '同学')

  async function login({ username, password, code, uuid }) {
    const data = await loginApi({ username, password, code, uuid })
    if (!data?.token) {
      throw new Error('登录响应缺少令牌')
    }
    token.value = data.token
    setToken(data.token)
    profile.value = null
  }

  async function loadProfile() {
    profile.value = await getUserInfo()
    return profile.value
  }

  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 服务端登出失败也要清掉本地登录态
    } finally {
      reset()
    }
  }

  function reset() {
    token.value = ''
    profile.value = null
    removeToken()
  }

  return { token, profile, isLoggedIn, displayName, login, loadProfile, logout, reset }
})
