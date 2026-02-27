import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getToken } from './auth'
import router from '@/router'

// 创建 axios 实例
const service = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API, // 基础 URL
  timeout: 10000 // 请求超时时间 10秒
})

// 请求拦截器
service.interceptors.request.use(
  config => {
    // 在发送请求之前做些什么
    
    // 如果 token 存在，则添加到请求头
    const token = getToken()
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    
    // 设置请求头
    config.headers['Content-Type'] = config.headers['Content-Type'] || 'application/json'
    
    return config
  },
  error => {
    // 对请求错误做些什么
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器
service.interceptors.response.use(
  response => {
    // 对响应数据做点什么
    
    const res = response.data
    
    // 如果返回的状态码不是200，则视为错误
    if (res.code !== undefined && res.code !== 200) {
      // 401: 未登录或 token 过期
      if (res.code === 401) {
        ElMessageBox.confirm(
          '登录状态已过期，您可以继续留在该页面，或者重新登录',
          '系统提示',
          {
            confirmButtonText: '重新登录',
            cancelButtonText: '取消',
            type: 'warning'
          }
        ).then(() => {
          // 清除 token 并跳转到登录页
          localStorage.removeItem('token')
          router.push('/login')
        })
        return Promise.reject(new Error(res.msg || '未授权，请重新登录'))
      }
      
      // 403: 无权限
      if (res.code === 403) {
        ElMessage({
          message: res.msg || '没有权限访问该资源',
          type: 'error',
          duration: 5 * 1000
        })
        return Promise.reject(new Error(res.msg || '没有权限访问该资源'))
      }
      
      // 其他错误
      ElMessage({
        message: res.msg || '请求失败',
        type: 'error',
        duration: 5 * 1000
      })
      return Promise.reject(new Error(res.msg || '请求失败'))
    } else {
      // 如果响应数据直接返回，则直接返回
      return res
    }
  },
  error => {
    // 对响应错误做点什么
    console.error('响应错误:', error)
    
    let message = '请求失败'
    
    if (error.response) {
      // 服务器返回了错误状态码
      const { status, data } = error.response
      
      switch (status) {
        case 400:
          message = data?.msg || '请求参数错误'
          break
        case 401:
          message = '未授权，请重新登录'
          // 清除 token 并跳转到登录页
          localStorage.removeItem('token')
          router.push('/login')
          break
        case 403:
          message = '拒绝访问'
          break
        case 404:
          message = '请求的资源不存在'
          break
        case 500:
          message = '服务器内部错误'
          break
        case 502:
          message = '网关错误'
          break
        case 503:
          message = '服务不可用'
          break
        case 504:
          message = '网关超时'
          break
        default:
          message = data?.msg || `请求失败(${status})`
      }
    } else if (error.request) {
      // 请求已发出，但没有收到响应
      message = '网络连接失败，请检查网络'
    } else {
      // 发送请求时出了点问题
      message = error.message || '请求失败'
    }
    
    // 请求超时
    if (error.code === 'ECONNABORTED' || error.message.indexOf('timeout') !== -1) {
      message = '请求超时，请稍后重试'
    }
    
    ElMessage({
      message,
      type: 'error',
      duration: 5 * 1000
    })
    
    return Promise.reject(error)
  }
)

export default service

