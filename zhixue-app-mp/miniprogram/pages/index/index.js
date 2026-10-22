// pages/index/index.js
const {
  getBannerList,
  getCategoryList,
  getHotCourseList,
  getLatestCourseList
} = require('../../api/course')
const { withCategoryIcon } = require('../../utils/category-icons')

Page({
  /**
   * 页面的初始数据
   */
  data: {
    bannerList: [],
    categoryList: [],
    hotCourseList: [],
    latestCourseList: [],
    loading: false
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad(options) {
    this.loadData()
  },

  /**
   * 生命周期函数--监听页面显示
   */
  onShow() {
    
  },

  /**
   * 点击搜索
   */
  onSeckillTap() {
    wx.navigateTo({ url: '/pages/seckill/index' })
  },

  onSearchTap() {
    wx.navigateTo({
      url: '/pages/search/index'
    })
  },

  /**
   * 加载数据
   */
  async loadData() {
    this.setData({ loading: true })
    
    try {
      // 每块单独容错：某一块失败不影响其他块；但失败必须留痕，
      // 否则「接口全挂」与「确实没有数据」在界面上完全一样（都显示「暂无数据」）
      const failed = []
      const soft = (name, promise) => promise.catch((error) => {
        failed.push(name)
        console.warn(`[index] ${name} 加载失败:`, error && (error.message || error.errMsg || error))
        return { code: 200, data: [] }
      })
      const [bannerRes, categoryRes, hotRes, latestRes] = await Promise.all([
        soft('banner', getBannerList()),
        soft('category', getCategoryList()),
        soft('hot', getHotCourseList({ limit: 6 })),
        soft('latest', getLatestCourseList({ limit: 10 }))
      ])

      this.setData({
        bannerList: bannerRes.data || [],
        categoryList: (categoryRes.data || []).map(withCategoryIcon),
        hotCourseList: hotRes.data || [],
        latestCourseList: latestRes.data || [],
        loadFailed: failed
      })
      // 四块全部失败多半是网络或网关问题，给学生一个可重试的提示
      if (failed.length === 4) {
        wx.showToast({ title: '网络异常，请下拉重试', icon: 'none' })
      }
    } catch (error) {
      console.error('加载数据失败:', error)
      wx.showToast({
        title: '加载失败',
        icon: 'none'
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  /**
   * 轮播图点击
   */
  onBannerTap(e) {
    const { index } = e.currentTarget.dataset
    const banner = this.data.bannerList[index]
    
    if (banner && banner.linkId) {
      wx.navigateTo({
        url: `/pages/course/detail/index?id=${banner.linkId}`
      })
    }
  },

  /**
   * 分类点击
   */
  onCategoryTap(e) {
    const { id } = e.currentTarget.dataset
    getApp().globalData.pendingCategoryId = String(id)
    wx.switchTab({
      url: '/pages/category/index'
    })
  },

  /**
   * 课程点击
   */
  onCourseTap(e) {
    const { id } = e.currentTarget.dataset
    wx.navigateTo({
      url: `/pages/course/detail/index?id=${id}`
    })
  },

  /**
   * 查看更多热门课程
   */
  onMoreHotTap() {
    wx.switchTab({
      url: '/pages/category/index'
    })
  },

  /**
   * 下拉刷新
   */
  onPullDownRefresh() {
    this.loadData().finally(() => {
      wx.stopPullDownRefresh()
    })
  }
})
