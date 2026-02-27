/**
 * 认证相关 API
 */
import request from '@/utils/request'

/**
 * 登录
 * @param {string} username 用户名
 * @param {string} password 密码
 * @param {string} code 验证码
 * @param {string} uuid 验证码唯一标识
 */
export function login(username, password, code, uuid) {
  const data = {
    username,
    password,
    code,
    uuid,
    loginType: 'password'
  }
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

/**
 * 退出登录
 */
export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}

/**
 * 获取用户信息
 */
export function getInfo() {
  return request({
    url: '/auth/user/info',
    method: 'get'
  })
}

/**
 * 刷新Token
 */
export function refreshToken() {
  return request({
    url: '/auth/refresh',
    method: 'post'
  })
}

/**
 * 获取验证码
 */
export function getCaptcha() {
  return request({
    url: '/auth/captcha',
    method: 'get'
  })
}

