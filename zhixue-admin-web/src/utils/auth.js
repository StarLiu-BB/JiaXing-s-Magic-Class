const TokenKey = 'token'

/**
 * 获取 Token
 */
export function getToken() {
  return localStorage.getItem(TokenKey)
}

/**
 * 设置 Token
 */
export function setToken(token) {
  return localStorage.setItem(TokenKey, token)
}

/**
 * 移除 Token
 */
export function removeToken() {
  return localStorage.removeItem(TokenKey)
}

/**
 * 检查是否已登录
 */
export function isLogin() {
  return !!getToken()
}

