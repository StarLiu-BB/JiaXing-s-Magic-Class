/**
 * 认证相关API
 */
const { get, post } = require('./request')

/**
 * 微信登录
 * @param {string} code 微信登录code
 */
function wxLogin(code) {
  return post('/auth/wx/login', { code })
}

/**
 * 获取用户信息
 */
function getUserInfo() {
  return get('/auth/user/info')
}

/**
 * 退出登录
 */
function logout() {
  return post('/auth/logout')
}

/**
 * 刷新Token
 */
function refreshToken() {
  return post('/auth/refresh')
}

module.exports = {
  wxLogin,
  getUserInfo,
  logout,
  refreshToken
}

