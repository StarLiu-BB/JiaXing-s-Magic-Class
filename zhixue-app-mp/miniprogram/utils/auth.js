/**
 * 认证工具函数
 */

// Token存储key
const TOKEN_KEY = 'zhixue_token'
const USER_INFO_KEY = 'zhixue_user_info'

/**
 * 获取Token
 */
function getToken() {
  try {
    return wx.getStorageSync(TOKEN_KEY) || ''
  } catch (error) {
    console.error('获取Token失败:', error)
    return ''
  }
}

/**
 * 设置Token
 */
function setToken(token) {
  try {
    wx.setStorageSync(TOKEN_KEY, token)
    return true
  } catch (error) {
    console.error('设置Token失败:', error)
    return false
  }
}

/**
 * 移除Token
 */
function removeToken() {
  try {
    wx.removeStorageSync(TOKEN_KEY)
    return true
  } catch (error) {
    console.error('移除Token失败:', error)
    return false
  }
}

/**
 * 获取用户信息
 */
function getUserInfo() {
  try {
    return wx.getStorageSync(USER_INFO_KEY) || null
  } catch (error) {
    console.error('获取用户信息失败:', error)
    return null
  }
}

/**
 * 设置用户信息
 */
function setUserInfo(userInfo) {
  try {
    wx.setStorageSync(USER_INFO_KEY, userInfo)
    return true
  } catch (error) {
    console.error('设置用户信息失败:', error)
    return false
  }
}

/**
 * 移除用户信息
 */
function removeUserInfo() {
  try {
    wx.removeStorageSync(USER_INFO_KEY)
    return true
  } catch (error) {
    console.error('移除用户信息失败:', error)
    return false
  }
}

/**
 * 检查是否已登录
 */
function isLogin() {
  const token = getToken()
  return !!token
}

/**
 * 清除所有认证信息
 */
function clearAuth() {
  removeToken()
  removeUserInfo()
}

/**
 * 检查登录状态并跳转
 */
function checkLoginAndRedirect(redirectUrl = '/pages/my/index') {
  if (!isLogin()) {
    wx.showModal({
      title: '提示',
      content: '请先登录',
      showCancel: false,
      success: () => {
        wx.navigateTo({
          url: redirectUrl
        })
      }
    })
    return false
  }
  return true
}

module.exports = {
  getToken,
  setToken,
  removeToken,
  getUserInfo,
  setUserInfo,
  removeUserInfo,
  isLogin,
  clearAuth,
  checkLoginAndRedirect
}

