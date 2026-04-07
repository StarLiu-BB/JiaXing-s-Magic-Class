// pages/index/index.js
const {
  getBannerList,
  getCategoryList,
  getHotCourseList,
  getLatestCourseList
} = require('../../api/course')

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
      // 并行加载所有数据
      const [bannerRes, categoryRes, hotRes, latestRes] = await Promise.all([
        getBannerList().catch(() => ({ code: 200, data: [] })),
        getCategoryList().catch(() => ({ code: 200, data: [] })),
        getHotCourseList({ limit: 6 }).catch(() => ({ code: 200, data: [] })),
        getLatestCourseList({ limit: 10 }).catch(() => ({ code: 200, data: [] }))
      ])

      this.setData({
        bannerList: bannerRes.data || [],
        categoryList: categoryRes.data || [],
        hotCourseList: hotRes.data || [],
        latestCourseList: latestRes.data || []
      })
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
