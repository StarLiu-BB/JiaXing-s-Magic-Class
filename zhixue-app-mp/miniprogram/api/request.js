/**
 * 请求封装
 */
import { getToken, removeToken } from '../utils/auth'

// 基础URL
const BASE_URL = 'http://192.168.211.175:9000'

// 请求队列（用于防止重复请求）
const requestQueue = new Map()

/**
 * 请求拦截器
 */
function requestInterceptor(config) {
  // 添加token
  const token = getToken()
  if (token) {
    config.header = config.header || {}
    config.header['Authorization'] = `Bearer ${token}`
  }

  // 添加请求ID（用于取消重复请求）
  const requestId = `${config.url}_${JSON.stringify(config.data || {})}`
  config.requestId = requestId

  return config
}

/**
 * 响应拦截器
 */
function responseInterceptor(response, config) {
  const { statusCode, data } = response

  // HTTP状态码处理
  if (statusCode === 200) {
    // 业务状态码处理
    if (data.code === 200) {
      return Promise.resolve(data)
    } else if (data.code === 401) {
      // Token过期，清除token并跳转登录
      removeToken()
      const app = getApp()
      app.globalData.isLogin = false
      app.globalData.userInfo = null
      app.globalData.token = ''

      wx.showModal({
        title: '提示',
        content: '登录已过期，请重新登录',
        showCancel: false,
        success: () => {
          wx.reLaunch({
            url: '/pages/my/index'
          })
        }
      })

      return Promise.reject(new Error('登录已过期'))
    } else if (data.code === 403) {
      wx.showToast({
        title: '无权限访问',
        icon: 'none'
      })
      return Promise.reject(new Error('无权限访问'))
    } else {
      // 其他业务错误
      const errorMsg = data.msg || '请求失败'
      wx.showToast({
        title: errorMsg,
        icon: 'none',
        duration: 2000
      })
      return Promise.reject(new Error(errorMsg))
    }
  } else if (statusCode === 401) {
    // HTTP 401 未授权
    removeToken()
    const app = getApp()
    app.globalData.isLogin = false
    app.globalData.userInfo = null
    app.globalData.token = ''

    wx.showModal({
      title: '提示',
      content: '登录已过期，请重新登录',
      showCancel: false,
      success: () => {
        wx.reLaunch({
          url: '/pages/my/index'
        })
      }
    })

    return Promise.reject(new Error('登录已过期'))
  } else {
    // 其他HTTP错误
    wx.showToast({
      title: `请求失败(${statusCode})`,
      icon: 'none'
    })
    return Promise.reject(new Error(`请求失败: ${statusCode}`))
  }
}

/**
 * 刷新Token
 */
async function refreshToken() {
  try {
    // 这里调用刷新token的接口
    // const res = await request({
    //   url: '/auth/refresh',
    //   method: 'POST'
    // })
    // if (res.code === 200) {
    //   setToken(res.data.token)
    //   return res.data.token
    // }
    return null
  } catch (error) {
    console.error('刷新Token失败:', error)
    return null
  }
}

/**
 * 统一请求方法
 */
function request(options = {}) {
  return new Promise((resolve, reject) => {
    // 默认配置
    const config = {
      url: options.url,
      method: options.method || 'GET',
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        ...options.header
      },
      timeout: options.timeout || 10000,
      ...options
    }

    // 构建完整URL
    if (!config.url.startsWith('http')) {
      config.url = BASE_URL + config.url
    }

    // 请求拦截
    const interceptedConfig = requestInterceptor(config)

    // 检查是否有重复请求
    const requestId = interceptedConfig.requestId
    if (requestQueue.has(requestId)) {
      console.warn('检测到重复请求，已取消:', requestId)
      return reject(new Error('重复请求'))
    }

    // 添加到请求队列
    requestQueue.set(requestId, interceptedConfig)

    // 显示加载提示
    if (options.showLoading !== false) {
      wx.showLoading({
        title: options.loadingText || '加载中...',
        mask: true
      })
    }

    // 发起请求
    wx.request({
      ...interceptedConfig,
      success: async (res) => {
        // 从请求队列移除
        requestQueue.delete(requestId)

        // 隐藏加载提示
        if (options.showLoading !== false) {
          wx.hideLoading()
        }

        try {
          const result = await responseInterceptor(res, interceptedConfig)
          resolve(result)
        } catch (error) {
          reject(error)
        }
      },
      fail: (err) => {
        // 从请求队列移除
        requestQueue.delete(requestId)

        // 隐藏加载提示
        if (options.showLoading !== false) {
          wx.hideLoading()
        }

        console.error('请求失败:', err)
        
        // 网络错误处理
        if (err.errMsg) {
          if (err.errMsg.includes('timeout')) {
            wx.showToast({
              title: '请求超时',
              icon: 'none'
            })
          } else if (err.errMsg.includes('fail')) {
            wx.showToast({
              title: '网络连接失败',
              icon: 'none'
            })
          }
        }

        reject(err)
      }
    })
  })
}

/**
 * GET请求
 */
function get(url, params = {}, options = {}) {
  return request({
    url,
    method: 'GET',
    data: params,
    ...options
  })
}

/**
 * POST请求
 */
function post(url, data = {}, options = {}) {
  return request({
    url,
    method: 'POST',
    data,
    ...options
  })
}

/**
 * PUT请求
 */
function put(url, data = {}, options = {}) {
  return request({
    url,
    method: 'PUT',
    data,
    ...options
  })
}

/**
 * DELETE请求
 */
function del(url, params = {}, options = {}) {
  return request({
    url,
    method: 'DELETE',
    data: params,
    ...options
  })
}

module.exports = {
  request,
  get,
  post,
  put,
  del,
  BASE_URL
}

