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
    // 轮播图数据
    bannerList: [
      {
        id: 1,
        imageUrl: 'https://picsum.photos/750/320?random=1',
        linkType: 'course',
        linkId: 1
      },
      {
        id: 2,
        imageUrl: 'https://picsum.photos/750/320?random=2',
        linkType: 'course',
        linkId: 2
      },
      {
        id: 3,
        imageUrl: 'https://picsum.photos/750/320?random=3',
        linkType: 'course',
        linkId: 3
      }
    ],
    // 分类数据
    categoryList: [
      { id: 1, name: '编程开发', icon: '/images/category/code.png' },
      { id: 2, name: '人工智能', icon: '/images/category/ai.png' },
      { id: 3, name: '前端开发', icon: '/images/category/web.png' },
      { id: 4, name: '后端开发', icon: '/images/category/server.png' },
      { id: 5, name: '移动开发', icon: '/images/category/mobile.png' },
      { id: 6, name: '数据库', icon: '/images/category/database.png' },
      { id: 7, name: '云计算', icon: '/images/category/cloud.png' },
      { id: 8, name: '大数据', icon: '/images/category/bigdata.png' }
    ],
    // 热门课程
    hotCourseList: [
      {
        id: 1,
        title: 'Java 从入门到精通',
        cover: 'https://picsum.photos/280/160?random=10',
        price: 199,
        sales: 1250
      },
      {
        id: 2,
        title: 'Spring Boot 实战教程',
        cover: 'https://picsum.photos/280/160?random=11',
        price: 299,
        sales: 890
      },
      {
        id: 3,
        title: 'Python 数据分析',
        cover: 'https://picsum.photos/280/160?random=12',
        price: 249,
        sales: 2100
      },
      {
        id: 4,
        title: 'Vue.js 前端开发',
        cover: 'https://picsum.photos/280/160?random=13',
        price: 159,
        sales: 1560
      }
    ],
    // 最新课程
    latestCourseList: [
      {
        id: 5,
        title: 'React 进阶教程',
        description: '深入理解React原理，掌握高级用法',
        cover: 'https://picsum.photos/240/140?random=20',
        teacher: '陈老师',
        price: 279
      },
      {
        id: 6,
        title: 'Node.js 全栈开发',
        description: '从后端到前端的全栈技术栈',
        cover: 'https://picsum.photos/240/140?random=21',
        teacher: '赵老师',
        price: 329
      },
      {
        id: 7,
        title: 'Docker 容器化部署',
        description: '现代化应用部署方案',
        cover: 'https://picsum.photos/240/140?random=22',
        teacher: '孙老师',
        price: 199
      }
    ],
    loading: false
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad(options) {
    // 如果有API，可以调用loadData()加载真实数据
    // this.loadData()
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
    wx.showToast({
      title: '搜索功能开发中',
      icon: 'none'
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
