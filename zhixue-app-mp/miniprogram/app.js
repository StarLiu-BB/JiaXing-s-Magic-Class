// app.js
import { getToken, setToken, removeToken } from './utils/auth'
import { wxLogin, getUserInfo } from './api/auth'

App({
  /**
   * 全局数据
   */
  globalData: {
    userInfo: null, // 用户信息
    token: '', // 登录token
    isLogin: false // 是否已登录
  },

  /**
   * 小程序初始化
   */
  onLaunch() {
    console.log('智学云小程序启动')
    
    // 检查登录状态
    this.checkLogin()
    
    // 检查更新
    this.checkUpdate()
  },

  /**
   * 小程序显示
   */
  onShow() {
    // 检查登录状态
    this.checkLogin()
  },

  /**
   * 检查登录状态
   */
  checkLogin() {
    const token = getToken()
    if (token) {
      this.globalData.token = token
      this.globalData.isLogin = true
      
      // 获取用户信息
      this.getUserInfo()
    } else {
      this.globalData.isLogin = false
      this.globalData.userInfo = null
    }
  },

  /**
   * 微信登录
   */
  async wxLogin() {
    return new Promise((resolve, reject) => {
      wx.login({
        success: async (res) => {
          if (res.code) {
            try {
              // 调用后端登录接口
              const loginRes = await wxLogin(res.code)
              
              if (loginRes.code === 200 && loginRes.data) {
                const { token, userInfo } = loginRes.data
                
                // 保存token
                setToken(token)
                this.globalData.token = token
                this.globalData.isLogin = true
                this.globalData.userInfo = userInfo
                
                resolve({ token, userInfo })
              } else {
                reject(new Error(loginRes.msg || '登录失败'))
              }
            } catch (error) {
              console.error('登录失败:', error)
              reject(error)
            }
          } else {
            reject(new Error('获取微信登录code失败'))
          }
        },
        fail: (err) => {
          console.error('微信登录失败:', err)
          reject(err)
        }
      })
    })
  },

  /**
   * 获取用户信息
   */
  async getUserInfo() {
    if (!this.globalData.isLogin) {
      return null
    }

    try {
      const res = await getUserInfo()
      if (res.code === 200 && res.data) {
        this.globalData.userInfo = res.data
        return res.data
      }
    } catch (error) {
      console.error('获取用户信息失败:', error)
    }
    return null
  },

  /**
   * 登录
   */
  async login() {
    try {
      const result = await this.wxLogin()
      return result
    } catch (error) {
      console.error('登录失败:', error)
      wx.showToast({
        title: error.message || '登录失败',
        icon: 'none'
      })
      throw error
    }
  },

  /**
   * 退出登录
   */
  logout() {
    removeToken()
    this.globalData.token = ''
    this.globalData.isLogin = false
    this.globalData.userInfo = null
    
    wx.showToast({
      title: '已退出登录',
      icon: 'success'
    })
  },

  /**
   * 检查更新
   */
  checkUpdate() {
    if (wx.canIUse('getUpdateManager')) {
      const updateManager = wx.getUpdateManager()
      
      updateManager.onCheckForUpdate((res) => {
        if (res.hasUpdate) {
          updateManager.onUpdateReady(() => {
            wx.showModal({
              title: '更新提示',
              content: '新版本已经准备好，是否重启应用？',
              success: (res) => {
                if (res.confirm) {
                  updateManager.applyUpdate()
                }
              }
            })
          })
          
          updateManager.onUpdateFailed(() => {
            wx.showModal({
              title: '更新失败',
              content: '新版本下载失败，请删除小程序后重新搜索打开',
              showCancel: false
            })
          })
        }
      })
    }
  }
})

