const { getOrderList } = require('../../../api/order')
const { getFavoriteList, getStudyRecords } = require('../../../api/user')
const { getAvailableCouponList, claimCoupon, readCouponCache } = require('../../../api/marketing')

const MODE_CONFIG = {
  orders: {
    title: '我的订单'
  },
  favorites: {
    title: '我的收藏'
  },
  coupons: {
    title: '我的优惠券'
  },
  history: {
    title: '学习记录'
  }
}

Page({
  data: {
    mode: 'orders',
    title: '我的列表',
    status: '',
    highlight: '',
    loading: false,
    list: []
  },

  onLoad(options) {
    const mode = MODE_CONFIG[options.mode] ? options.mode : 'orders'
    const title = MODE_CONFIG[mode].title
    wx.setNavigationBarTitle({ title })
    this.setData({
      mode,
      title,
      status: options.status || '',
      highlight: options.highlight || ''
    })
    this.fetchList()
  },

  onPullDownRefresh() {
    this.fetchList().finally(() => wx.stopPullDownRefresh())
  },

  async fetchList() {
    this.setData({ loading: true })
    try {
      let list = []
      const { mode, status } = this.data
      if (mode === 'orders') {
        const res = await getOrderList({
          pageNum: 1,
          pageSize: 100,
          status
        })
        list = res.data?.list || res.data?.records || res.data || []
      } else if (mode === 'favorites') {
        const res = await getFavoriteList({
          pageNum: 1,
          pageSize: 100
        })
        list = res.data?.list || res.data?.records || res.data || []
      } else if (mode === 'coupons') {
        const remote = await getAvailableCouponList()
        const remoteList = remote.data || []
        const localList = readCouponCache()
        const map = new Map()
        ;[...localList, ...remoteList].forEach((item) => {
          const id = item.id || item.couponId
          if (id) {
            map.set(String(id), { ...item, id })
          }
        })
        list = [...map.values()]
      } else if (mode === 'history') {
        const res = await getStudyRecords({ pageNum: 1, pageSize: 100 })
        list = res.data?.list || res.data?.records || res.data || []
      }
      const highlight = this.data.highlight
      if (mode === 'orders' && highlight) {
        list = list.map((item) => ({
          ...item,
          isHighlight: String(item.orderNo || item.id || item.orderId) === String(highlight)
        }))
      }
      this.setData({ list })
    } catch (error) {
      console.error('列表加载失败:', error)
      this.setData({ list: [] })
      wx.showToast({
        title: '加载失败',
        icon: 'none'
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  onOrderFilterTap(e) {
    this.setData({
      status: e.currentTarget.dataset.status || ''
    })
    this.fetchList()
  },

  async onClaimCoupon(e) {
    const app = getApp()
    const user = app.globalData.userInfo || {}
    const userId = user.userId || user.id
    if (!userId) {
      wx.showToast({
        title: '请先登录',
        icon: 'none'
      })
      return
    }
    const couponId = e.currentTarget.dataset.id
    try {
      await claimCoupon(couponId, userId)
      wx.showToast({
        title: '领取成功',
        icon: 'success'
      })
      this.fetchList()
    } catch (error) {
      console.error('领取优惠券失败:', error)
    }
  },

  onCourseTap(e) {
    const id = e.currentTarget.dataset.id
    if (!id) {
      return
    }
    wx.navigateTo({
      url: `/pages/course/detail/index?id=${id}`
    })
  }
})
