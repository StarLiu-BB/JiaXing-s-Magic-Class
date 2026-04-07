// pages/course/detail/index.js
const app = getApp()
const {
  getCourseDetail,
  getCourseChapters,
  getCourseReviews,
  favoriteCourse,
  unfavoriteCourse
} = require('../../../api/course')
const { createOrder } = require('../../../api/order')
const { upsertFavoriteCache, removeFavoriteCache } = require('../../../api/user')

Page({
  /**
   * 页面的初始数据
   */
  data: {
    courseId: null,
    courseInfo: null,
    chapters: [],
    reviews: [],
    activeTab: 'intro', // intro-简介, chapters-目录, reviews-评价
    isFavorite: false,
    loading: false
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad(options) {
    const courseId = options.id
    if (!courseId) {
      wx.showToast({
        title: '课程不存在',
        icon: 'none'
      })
      setTimeout(() => {
        wx.navigateBack()
      }, 1500)
      return
    }

    this.setData({ courseId })
    this.loadCourseDetail()
  },

  /**
   * 加载课程详情
   */
  async loadCourseDetail() {
    this.setData({ loading: true })

    try {
      const [detailRes, chaptersRes] = await Promise.all([
        getCourseDetail(this.data.courseId),
        getCourseChapters(this.data.courseId)
      ])

      if (detailRes.code === 200) {
        const detail = detailRes.data || {}
        this.setData({
          courseInfo: detail,
          chapters: chaptersRes.data || [],
          isFavorite: detail.isFavorite || false
        })
      }
    } catch (error) {
      console.error('加载课程详情失败:', error)
      wx.showToast({
        title: '加载失败',
        icon: 'none'
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  /**
   * Tab切换
   */
  onTabChange(e) {
    const { tab } = e.currentTarget.dataset
    this.setData({ activeTab: tab })

    // 切换到评价时加载评价数据
    if (tab === 'reviews' && this.data.reviews.length === 0) {
      this.loadReviews()
    }
  },

  /**
   * 加载评价列表
   */
  async loadReviews() {
    try {
      const res = await getCourseReviews(this.data.courseId, {
        pageNum: 1,
        pageSize: 10
      })

      if (res.code === 200) {
        this.setData({
          reviews: res.data || []
        })
      }
    } catch (error) {
      console.error('加载评价失败:', error)
    }
  },

  /**
   * 收藏/取消收藏
   */
  async onFavoriteTap() {
    if (!app.globalData.isLogin) {
      wx.showModal({
        title: '提示',
        content: '请先登录',
        success: (res) => {
          if (res.confirm) {
            wx.navigateTo({
              url: '/pages/my/index'
            })
          }
        }
      })
      return
    }

    try {
      if (this.data.isFavorite) {
        await unfavoriteCourse(this.data.courseId)
        removeFavoriteCache(this.data.courseId)
        this.setData({ isFavorite: false })
        wx.showToast({
          title: '已取消收藏',
          icon: 'success'
        })
      } else {
        await favoriteCourse(this.data.courseId)
        upsertFavoriteCache({
          id: this.data.courseId,
          courseId: this.data.courseId,
          title: this.data.courseInfo?.title,
          cover: this.data.courseInfo?.cover || this.data.courseInfo?.coverUrl,
          price: this.data.courseInfo?.price
        })
        this.setData({ isFavorite: true })
        wx.showToast({
          title: '收藏成功',
          icon: 'success'
        })
      }
    } catch (error) {
      console.error('收藏操作失败:', error)
    }
  },

  /**
   * 立即购买
   */
  async onBuyTap() {
    if (!app.globalData.isLogin) {
      wx.showModal({
        title: '提示',
        content: '请先登录',
        success: (res) => {
          if (res.confirm) {
            wx.navigateTo({
              url: '/pages/my/index'
            })
          }
        }
      })
      return
    }

    try {
      wx.showLoading({ title: '创建订单中...' })
      const userInfo = app.globalData.userInfo || {}
      const userId = userInfo.userId || userInfo.id
      if (!userId) {
        throw new Error('用户信息不完整，请重新登录')
      }
      const price = Number(this.data.courseInfo?.price || 0)
      
      const res = await createOrder({
        userId,
        productId: Number(this.data.courseId),
        productType: 1,
        amount: price
      })

      wx.hideLoading()

      if (res.code === 200) {
        const order = res.data || {}
        const highlight = order.orderNo || order.id || order.orderId || ''
        wx.showToast({
          title: '订单创建成功',
          icon: 'success'
        })
        wx.navigateTo({
          url: `/pages/my/list/index?mode=orders&highlight=${highlight}`
        })
      }
    } catch (error) {
      wx.hideLoading()
      console.error('创建订单失败:', error)
      wx.showToast({
        title: error.message || '创建订单失败',
        icon: 'none'
      })
    }
  },

  /**
   * 开始学习
   */
  onStudyTap() {
    if (!app.globalData.isLogin) {
      wx.showModal({
        title: '提示',
        content: '请先登录',
        success: (res) => {
          if (res.confirm) {
            wx.navigateTo({
              url: '/pages/my/index'
            })
          }
        }
      })
      return
    }

    const canStudy = !!(
      this.data.courseInfo?.isPurchased ||
      this.data.courseInfo?.canStudy ||
      Number(this.data.courseInfo?.price || 0) === 0
    )
    if (!canStudy) {
      wx.showModal({
        title: '提示',
        content: '该课程尚未购买，可先试看或立即购买。',
        confirmText: '立即购买',
        cancelText: '试看首节',
        success: (res) => {
          if (res.confirm) {
            this.onBuyTap()
          } else {
            const firstChapter = (this.data.chapters || [])[0]
            if (firstChapter) {
              this.onChapterTap({ currentTarget: { dataset: { chapter: firstChapter } } })
            }
          }
        }
      })
      return
    }

    // 跳转到播放页
    wx.navigateTo({
      url: `/pages/course/player/index?courseId=${this.data.courseId}`
    })
  },

  /**
   * 章节点击
   */
  onChapterTap(e) {
    const { chapter } = e.currentTarget.dataset
    wx.navigateTo({
      url: `/pages/course/player/index?courseId=${this.data.courseId}&chapterId=${chapter.id}`
    })
  }
})
