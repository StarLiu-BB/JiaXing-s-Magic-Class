// pages/seckill/index.js
const app = getApp()
const { getOnlineSeckills, getSeckillToken, doSeckill } = require('../../api/marketing')
const { getCourseDetail } = require('../../api/course')
const { checkout } = require('../../utils/pay')

/** 后端返回 LocalDateTime（如 2026-09-30T17:14:00），iOS 只能稳定解析 YYYY/MM/DD HH:mm:ss */
function parseTime(value) {
  if (!value) {
    return 0
  }
  return new Date(String(value).replace('T', ' ').replace(/-/g, '/').split('.')[0]).getTime()
}

function pad(n) {
  return n < 10 ? `0${n}` : `${n}`
}

function formatCountdown(ms) {
  const total = Math.max(0, Math.floor(ms / 1000))
  const h = Math.floor(total / 3600)
  const m = Math.floor((total % 3600) / 60)
  const s = total % 60
  return `${pad(h)}:${pad(m)}:${pad(s)}`
}

Page({
  data: {
    loading: false,
    activities: [],
    // 正在提交的活动 ID：防止重复点击导致重复提交
    submittingId: null
  },

  onShow() {
    this.loadActivities()
  },

  onHide() {
    this.stopTimer()
  },

  onUnload() {
    this.stopTimer()
  },

  onPullDownRefresh() {
    this.loadActivities().finally(() => wx.stopPullDownRefresh())
  },

  async loadActivities() {
    this.setData({ loading: true })
    try {
      const res = await getOnlineSeckills()
      const list = res.data || []
      // 活动只记录课程 ID，课程名、封面、原价以及"是否已拥有"取自课程服务
      const courses = await Promise.all(list.map((item) =>
        getCourseDetail(item.productId).then((r) => r.data).catch(() => null)
      ))
      const activities = list.map((item, index) => {
        const course = courses[index]
        return {
          ...item,
          startAt: parseTime(item.startTime),
          endAt: parseTime(item.endTime),
          courseTitle: course ? course.title : '课程暂不可用',
          cover: course ? (course.coverUrl || course.cover) : '',
          originalPrice: course ? course.price : null,
          owned: !!(course && course.canStudy),
          courseAvailable: !!course
        }
      })
      this.setData({ activities }, () => {
        this.refreshCountdown()
        this.startTimer()
      })
    } catch (error) {
      console.error('加载秒杀活动失败:', error)
      this.setData({ activities: [] })
    } finally {
      this.setData({ loading: false })
    }
  },

  startTimer() {
    this.stopTimer()
    this.timer = setInterval(() => this.refreshCountdown(), 1000)
  },

  stopTimer() {
    if (this.timer) {
      clearInterval(this.timer)
      this.timer = null
    }
  },

  refreshCountdown() {
    const now = Date.now()
    const activities = this.data.activities.map((item) => {
      let phase = 'ongoing'
      let countdown = formatCountdown(item.endAt - now)
      if (now < item.startAt) {
        phase = 'upcoming'
        countdown = formatCountdown(item.startAt - now)
      } else if (now >= item.endAt) {
        phase = 'ended'
        countdown = ''
      }
      const soldOut = item.remainingStock === 0
      return {
        ...item,
        phase,
        countdown,
        soldOut,
        canBuy: phase === 'ongoing' && !soldOut && !item.owned && item.courseAvailable
      }
    })
    this.setData({ activities })
  },

  async onSeckillTap(e) {
    const { id } = e.currentTarget.dataset
    const activity = this.data.activities.find((item) => String(item.id) === String(id))
    if (!activity || !activity.canBuy || this.data.submittingId) {
      return
    }
    if (!app.globalData.isLogin) {
      wx.showModal({
        title: '提示',
        content: '请先登录后参与秒杀',
        success: (res) => {
          if (res.confirm) {
            wx.navigateTo({ url: '/pages/my/index' })
          }
        }
      })
      return
    }

    this.setData({ submittingId: activity.id })
    let order = null
    try {
      wx.showLoading({ title: '抢购中...', mask: true })
      const tokenRes = await getSeckillToken(activity.id)
      const res = await doSeckill(activity.id, tokenRes.data)
      wx.hideLoading()
      order = res.data || {}

      const paid = await checkout(order)
      if (paid) {
        wx.showToast({ title: '支付成功', icon: 'success' })
        wx.navigateTo({ url: `/pages/course/detail/index?id=${activity.productId}` })
      } else {
        // 抢到的名额已锁定为待支付订单，超时未付将被取消
        wx.showToast({ title: '抢购成功，请在 30 分钟内完成支付', icon: 'none', duration: 2500 })
        wx.navigateTo({ url: `/pages/my/list/index?mode=orders&highlight=${order.orderNo}` })
      }
    } catch (error) {
      wx.hideLoading()
      // 已售罄 / 请勿重复抢购 / 已购买等业务提示由 request 统一弹出服务端文案
      console.error('秒杀失败:', error)
      if (order && order.orderNo) {
        wx.navigateTo({ url: `/pages/my/list/index?mode=orders&highlight=${order.orderNo}` })
      }
    } finally {
      this.setData({ submittingId: null })
      // 以服务端为准刷新剩余库存
      this.loadActivities()
    }
  }
})
