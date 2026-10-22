const TAB_PAGES = ['/pages/index/index', '/pages/category/index', '/pages/study/index', '/pages/my/index']

function openPage(url, options = {}) {
  if (!url) return
  const path = url.split('?')[0]
  if (TAB_PAGES.includes(path)) {
    wx.switchTab({ ...options, url: path })
  } else {
    wx.navigateTo({ ...options, url })
  }
}

function requestLogin(redirectUrl = '/pages/my/index') {
  wx.showModal({
    title: '提示',
    content: '请先登录',
    success: res => {
      if (res.confirm) openPage(redirectUrl)
    }
  })
}

module.exports = { TAB_PAGES, openPage, requestLogin }
