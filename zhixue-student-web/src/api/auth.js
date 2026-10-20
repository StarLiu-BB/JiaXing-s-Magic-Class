import request from '@/utils/request'

export const getCaptcha = () => request({ url: '/auth/captcha', method: 'get' })

export const login = (form) => request({ url: '/auth/login', method: 'post', data: { ...form, loginType: 'password' } })

// 由路由守卫与登录页自己处理 401（要带上原地址），不触发全局跳转
export const getUserInfo = () => request({ url: '/auth/user/info', method: 'get', skipAuthRedirect: true })

export const logout = () => request({ url: '/auth/logout', method: 'post', skipAuthRedirect: true })
