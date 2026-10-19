const { getOrderList, cancelOrder } = require('../../../api/order')
const { checkout } = require('../../../utils/pay')
const { getFavoriteList, getStudyRecords, ERR_NOT_IMPLEMENTED } = require('../../../api/user')
const { getAvailableCouponList, getMyCoupons, getCoupon, claimCoupon } = require('../../../api/marketing')

const COUPON_STATUS = { 0: '未使用', 1: '已使用', 2: '已过期' }

const formatDate = (value) => (value ? String(value).replace('T', ' ').slice(0, 10) : '')

/** 可领取的券 + 我已领的券，按券合并；状态与到期时间全部以服务端为准 */
async function loadCoupons() {
  const [availableRes, mineRes] = await Promise.all([getAvailableCouponList(), getMyCoupons()])
  const available = availableRes.data || []
  const mine = mineRes.data || []
  const byCoupon = new Map(available.map((c) => [String(c.id), c]))
  // 已下线活动里我领过的券不在可领取列表中，单独取券面信息
  const missing = mine.filter((m) => !byCoupon.has(String(m.couponId)))
  const extra = await Promise.all(missing.map((m) => getCoupon(m.couponId).then((r) => r.data).catch(() => null)))
  extra.filter(Boolean).forEach((c) => byCoupon.set(String(c.id), c))

  const now = Date.now()
  const owned = new Map(mine.map((m) => [String(m.couponId), m]))
  return [...byCoupon.values()].map((c) => {
    const record = owned.get(String(c.id))
    let statusText = '可领取'
    if (record) {
      const expired = Number(record.status) === 0 && record.expireTime && new Date(record.expireTime).getTime() < now
      statusText = expired ? '已过期' : (COUPON_STATUS[record.status] || '已领取')
      if (statusText === '未使用' && record.expireTime) {
        statusText += ` · ${formatDate(record.expireTime)} 到期`
      }
    }
    return {
      id: c.id,
      name: c.name,
      discount: c.discount,
      thresholdText: Number(c.thresholdAmount) > 0 ? `满 ${c.thresholdAmount} 元可用` : '无门槛',
      claimed: !!record,
      statusText
    }
  }).sort((a, b) => Number(a.claimed) - Number(b.claimed))
}

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
        list = await loadCoupons()
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
      // 区分"后端能力缺失"与"请求失败"，避免把功能缺口显示成网络问题
      const notImplemented = String(error?.message || '').includes(ERR_NOT_IMPLEMENTED)
      wx.showToast({
        title: notImplemented ? '学习记录功能开发中' : '加载失败',
        icon: 'none'
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  async onPayOrder(e) {
    const { orderNo, amount } = e.currentTarget.dataset
    try {
      const paid = await checkout({ orderNo, amount })
      if (paid) {
        wx.showToast({ title: '支付成功', icon: 'success' })
      }
    } catch (error) {
      // 业务错误（如订单已过期）由 request 弹出服务端提示
      console.error('支付失败:', error)
    } finally {
      // 无论成败都以服务端最新状态刷新
      this.fetchList()
    }
  },

  onCancelOrder(e) {
    const { id } = e.currentTarget.dataset
    wx.showModal({
      title: '取消订单',
      content: '确定取消该订单吗？',
      success: async (res) => {
        if (!res.confirm) {
          return
        }
        try {
          await cancelOrder(id)
          wx.showToast({ title: '已取消', icon: 'success' })
        } catch (error) {
          console.error('取消订单失败:', error)
        } finally {
          this.fetchList()
        }
      }
    })
  },

  onOrderFilterTap(e) {
    this.setData({
      status: e.currentTarget.dataset.status || ''
    })
    this.fetchList()
  },

  async onClaimCoupon(e) {
    if (!getApp().globalData.isLogin) {
      wx.showToast({
        title: '请先登录',
        icon: 'none'
      })
      return
    }
    const couponId = e.currentTarget.dataset.id
    try {
      await claimCoupon(couponId)
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
