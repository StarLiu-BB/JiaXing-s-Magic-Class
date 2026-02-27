// pages/my/index.js
Page({
  /**
   * 页面的初始数据
   */
  data: {
    isLogin: true,
    userInfo: {
      id: '10001',
      nickname: '智学云用户',
      avatar: ''
    },
    stats: {
      studyHours: 128,
      courseCount: 12,
      orderCount: 5,
      collectCount: 8
    },
    orderStats: {
      unpaid: 2
    }
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad(options) {

  },

  /**
   * 生命周期函数--监听页面显示
   */
  onShow() {

  },

  /**
   * 点击登录
   */
  onLoginTap() {
    if (!this.data.isLogin) {
      wx.showToast({
        title: '跳转登录页面',
        icon: 'none'
      })
    }
  },

  /**
   * 点击头像
   */
  onAvatarTap() {
    wx.showToast({
      title: '更换头像',
      icon: 'none'
    })
  },

  /**
   * 点击学习时长
   */
  onStudyTap() {
    wx.switchTab({
      url: '/pages/study/index'
    })
  },

  /**
   * 点击我的课程
   */
  onCourseTap() {
    wx.switchTab({
      url: '/pages/study/index'
    })
  },

  /**
   * 点击我的订单
   */
  onOrderTap() {
    wx.showToast({
      title: '查看全部订单',
      icon: 'none'
    })
  },

  /**
   * 点击我的收藏
   */
  onCollectTap() {
    wx.showToast({
      title: '查看收藏',
      icon: 'none'
    })
  },

  /**
   * 点击全部订单
   */
  onAllOrderTap() {
    this.onOrderTap()
  },

  /**
   * 点击订单状态
   */
  onOrderStatusTap(e) {
    const status = e.currentTarget.dataset.status
    const statusMap = {
      '0': '待付款',
      '1': '已付款',
      '2': '已完成'
    }
    wx.showToast({
      title: statusMap[status] || '订单',
      icon: 'none'
    })
  },

  /**
   * 点击售后
   */
  onAfterSaleTap() {
    wx.showToast({
      title: '售后/退款',
      icon: 'none'
    })
  },

  /**
   * 点击学习记录
   */
  onStudyRecordTap() {
    wx.switchTab({
      url: '/pages/study/index'
    })
  },

  /**
   * 点击我的课程
   */
  onMyCourseTap() {
    wx.switchTab({
      url: '/pages/study/index'
    })
  },

  /**
   * 点击我的收藏
   */
  onMyCollectTap() {
    this.onCollectTap()
  },

  /**
   * 点击优惠券
   */
  onCouponTap() {
    wx.showToast({
      title: '我的优惠券',
      icon: 'none'
    })
  },

  /**
   * 点击联系客服
   */
  onContactTap() {
    wx.showToast({
      title: '联系客服',
      icon: 'none'
    })
  },

  /**
   * 点击意见反馈
   */
  onFeedbackTap() {
    wx.showToast({
      title: '意见反馈',
      icon: 'none'
    })
  },

  /**
   * 点击关于我们
   */
  onAboutTap() {
    wx.showToast({
      title: '关于我们',
      icon: 'none'
    })
  },

  /**
   * 点击设置
   */
  onSettingTap() {
    wx.showToast({
      title: '设置',
      icon: 'none'
    })
  },

  /**
   * 退出登录
   */
  onLogoutTap() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          this.setData({
            isLogin: false,
            userInfo: {}
          })
          wx.showToast({
            title: '已退出登录',
            icon: 'success'
          })
        }
      }
    })
  }
})
