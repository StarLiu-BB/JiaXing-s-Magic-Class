import { defineStore } from 'pinia'
import { getToken, setToken, removeToken } from '@/utils/auth'

// 解析 JWT Token 获取用户信息
function parseJwt(token) {
  try {
    const base64Url = token.split('.')[1]
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/')
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
        .join('')
    )
    return JSON.parse(jsonPayload)
  } catch (e) {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: getToken() || '',
    name: '',
    avatar: '',
    roles: [],
    permissions: []
  }),
  getters: {
    hasToken: (state) => !!state.token,
    isAdmin: (state) => state.roles.includes('ADMIN'),
    isTeacher: (state) => state.roles.includes('TEACHER')
  },
  actions: {
    // 设置 Token
    setToken(token) {
      this.token = token
      setToken(token)
      // 从 Token 解析用户信息
      this.parseTokenInfo(token)
    },
    // 从 Token 解析用户信息
    parseTokenInfo(token) {
      const payload = parseJwt(token)
      if (payload) {
        this.name = payload.username || payload.sub || ''
        // 从 Token 中获取角色，默认为 ADMIN
        this.roles = payload.roles || ['ADMIN']
      }
    },
    // 设置用户信息
    setUserInfo(userInfo) {
      this.name = userInfo.name || ''
      this.avatar = userInfo.avatar || ''
      this.roles = userInfo.roles || []
      this.permissions = userInfo.permissions || []
    },
    // 设置角色
    setRoles(roles) {
      this.roles = roles
    },
    // 退出登录
    logout() {
      this.token = ''
      this.name = ''
      this.avatar = ''
      this.roles = []
      this.permissions = []
      removeToken()
    },
    // 重置 Token（用于 Token 过期等情况）
    resetToken() {
      this.token = ''
      this.roles = []
      this.permissions = []
      removeToken()
    },
    // 初始化用户信息（页面刷新时调用）
    initUserInfo() {
      if (this.token && this.roles.length === 0) {
        this.parseTokenInfo(this.token)
      }
    }
  }
})

