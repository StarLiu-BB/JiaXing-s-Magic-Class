import axios from 'axios'
import { getToken } from './auth'
import { sessionExpired } from './session'

/** 接口错误。code 为 body.code（业务错误）或 HTTP 状态码；0 表示网络不通或超时 */
export class ApiError extends Error {
  constructor(code, message) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

/**
 * 创建请求实例。约定与后端一致：
 * - 业务错误返回 HTTP 200，错误码在 body.code，以它为准；
 * - 网关鉴权失败返回 HTTP 401。两种 401 都视为登录失效，调用 onUnauthorized；
 *   请求配置 skipAuthRedirect: true 时不调用，由调用方自己处理（例如路由守卫要带上原地址）。
 * 成功时直接返回 body.data。这里不弹提示，由页面决定怎么告诉学生。
 */
export function createRequest({ baseURL, getToken: readToken, onUnauthorized, timeout = 15000 }) {
  const instance = axios.create({ baseURL, timeout })

  instance.interceptors.request.use((config) => {
    const token = readToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  instance.interceptors.response.use(
    (response) => {
      const body = response.data
      const wrapped = body !== null && typeof body === 'object' && 'code' in body
      if (wrapped && body.code !== 200) {
        if (body.code === 401 && !response.config.skipAuthRedirect) {
          onUnauthorized()
        }
        return Promise.reject(new ApiError(body.code, body.msg || '请求失败'))
      }
      return wrapped ? body.data : body
    },
    (error) => {
      const status = error.response?.status
      if (status === 401) {
        if (!error.config?.skipAuthRedirect) {
          onUnauthorized()
        }
        return Promise.reject(new ApiError(401, error.response.data?.msg || '登录已失效，请重新登录'))
      }
      if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
        return Promise.reject(new ApiError(0, '请求超时，请稍后重试'))
      }
      if (!error.response) {
        return Promise.reject(new ApiError(0, '网络连接失败，请检查网络'))
      }
      return Promise.reject(new ApiError(status, error.response.data?.msg || `请求失败（${status}）`))
    }
  )
  return instance
}

export default createRequest({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  getToken,
  onUnauthorized: sessionExpired
})
