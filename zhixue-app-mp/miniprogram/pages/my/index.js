// pages/my/index.js
const app = getApp()
const { getOrderList } = require('../../api/order')
const { getFavoriteList, getStudyRecords } = require('../../api/user')
const { getAvailableCouponList } = require('../../api/marketing')

Page({
  /**
   * 页面的初始数据
   */
  data: {
    isLogin: false,
    userInfo: {},
    stats: {
      studyHours: 0,
      courseCount: 0,
      orderCount: 0,
      collectCount: 0
    },
    orderStats: {
      unpaid: 0
    },
    couponCount: 0
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
    this.syncUserState()
  },

  /**
   * 点击登录
   */
  onLoginTap() {
    if (!this.data.isLogin) {
      app.login()
        .then(() => this.syncUserState())
        .catch(() => {})
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
    if (!this.checkLogin()) {
      return
    }
    wx.navigateTo({
      url: '/pages/my/list/index?mode=orders'
    })
  },

  /**
   * 点击我的收藏
   */
  onCollectTap() {
    if (!this.checkLogin()) {
      return
    }
    wx.navigateTo({
      url: '/pages/my/list/index?mode=favorites'
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
    if (!this.checkLogin()) {
      return
    }
    wx.navigateTo({
      url: `/pages/my/list/index?mode=orders&status=${status}`
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
    if (!this.checkLogin()) {
      return
    }
    wx.navigateTo({
      url: '/pages/my/list/index?mode=history'
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
    if (!this.checkLogin()) {
      return
    }
    wx.navigateTo({
      url: '/pages/my/list/index?mode=coupons'
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
          app.logout()
          this.syncUserState()
        }
      }
    })
  },

  syncUserState() {
    const userInfo = app.globalData.userInfo || {}
    const isLogin = !!app.globalData.isLogin
    this.setData({
      isLogin,
      userInfo: {
        id: userInfo.userId || '',
        nickname: userInfo.nickname || userInfo.username || '智学云用户',
        avatar: userInfo.avatar || ''
      }
    })
    if (isLogin) {
      this.loadDashboardData()
    } else {
      this.setData({
        stats: {
          studyHours: 0,
          courseCount: 0,
          orderCount: 0,
          collectCount: 0
        },
        orderStats: {
          unpaid: 0
        },
        couponCount: 0
      })
    }
  },

  checkLogin() {
    if (this.data.isLogin) {
      return true
    }
    wx.showModal({
      title: '提示',
      content: '请先登录后再查看',
      confirmText: '去登录',
      success: (res) => {
        if (res.confirm) {
          app.login()
            .then(() => this.syncUserState())
            .catch(() => {})
        }
      }
    })
    return false
  },

  async loadDashboardData() {
    try {
      const [orderRes, favoriteRes, studyRes, couponRes] = await Promise.all([
        getOrderList({ pageNum: 1, pageSize: 100 }).catch(() => ({ code: 200, data: [] })),
        getFavoriteList({ pageNum: 1, pageSize: 100 }).catch(() => ({ code: 200, data: [] })),
        getStudyRecords({ pageNum: 1, pageSize: 100 }).catch(() => ({ code: 200, data: [] })),
        getAvailableCouponList().catch(() => ({ code: 200, data: [] }))
      ])

      const orders = orderRes.data?.list || orderRes.data?.records || orderRes.data || []
      const favorites = favoriteRes.data?.list || favoriteRes.data?.records || favoriteRes.data || []
      const studies = studyRes.data?.list || studyRes.data?.records || studyRes.data || []
      const coupons = couponRes.data || []

      const totalStudySeconds = studies.reduce((sum, item) => sum + Number(item.studyDuration || item.duration || 0), 0)
      const uniqueCourses = new Set(
        studies
          .map((item) => item.courseId || item.id)
          .filter(Boolean)
      )

      this.setData({
        stats: {
          studyHours: Math.round((totalStudySeconds / 3600) * 10) / 10,
          courseCount: uniqueCourses.size,
          orderCount: orders.length,
          collectCount: favorites.length
        },
        orderStats: {
          unpaid: orders.filter((item) => Number(item.status) === 0).length
        },
        couponCount: coupons.length
      })
    } catch (error) {
      console.error('加载我的页统计失败:', error)
    }
  }
})
