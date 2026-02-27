// pages/category/index.js
Page({
  /**
   * 页面的初始数据
   */
  data: {
    // 分类列表
    categoryList: [
      { id: 1, name: '全部' },
      { id: 2, name: '编程开发' },
      { id: 3, name: '人工智能' },
      { id: 4, name: '前端开发' },
      { id: 5, name: '后端开发' },
      { id: 6, name: '移动开发' },
      { id: 7, name: '数据库' },
      { id: 8, name: '云计算' },
      { id: 9, name: '大数据' },
      { id: 10, name: '网络安全' }
    ],
    // 当前选中分类
    currentCategory: 0,
    // 子分类列表
    subCategoryList: [
      { id: 101, name: '全部' },
      { id: 102, name: 'Java' },
      { id: 103, name: 'Python' },
      { id: 104, name: 'Go' },
      { id: 105, name: 'C++' }
    ],
    // 当前选中子分类
    currentSubCategory: 0,
    // 排序类型
    sortType: 'default',
    // 价格排序方向
    priceSort: 'asc',
    // 课程列表
    courseList: [
      {
        id: 1,
        title: 'Java 从入门到精通',
        description: '零基础学习Java编程，从入门到实战',
        cover: '',
        teacher: '张老师',
        price: 199,
        originalPrice: 299,
        sales: 1250
      },
      {
        id: 2,
        title: 'Spring Boot 实战教程',
        description: '企业级应用开发实战',
        cover: '',
        teacher: '李老师',
        price: 299,
        originalPrice: 399,
        sales: 890
      },
      {
        id: 3,
        title: 'Python 数据分析',
        description: '数据科学与机器学习入门',
        cover: '',
        teacher: '王老师',
        price: 249,
        originalPrice: 349,
        sales: 2100
      },
      {
        id: 4,
        title: 'Vue.js 前端开发',
        description: '现代化前端框架实战',
        cover: '',
        teacher: '刘老师',
        price: 159,
        originalPrice: 259,
        sales: 1560
      },
      {
        id: 5,
        title: 'React 进阶教程',
        description: '深入理解React原理',
        cover: '',
        teacher: '陈老师',
        price: 279,
        originalPrice: 379,
        sales: 780
      }
    ],
    loading: false,
    hasMore: true
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
   * 点击搜索
   */
  onSearchTap() {
    wx.showToast({
      title: '搜索功能',
      icon: 'none'
    })
  },

  /**
   * 选择分类
   */
  onCategorySelect(e) {
    const index = e.currentTarget.dataset.index
    this.setData({
      currentCategory: index,
      currentSubCategory: 0
    })
    this.loadCourseList()
  },

  /**
   * 选择子分类
   */
  onSubCategorySelect(e) {
    const index = e.currentTarget.dataset.index
    this.setData({
      currentSubCategory: index
    })
    this.loadCourseList()
  },

  /**
   * 排序切换
   */
  onSortTap(e) {
    const type = e.currentTarget.dataset.type
    if (type === 'price') {
      this.setData({
        sortType: type,
        priceSort: this.data.priceSort === 'asc' ? 'desc' : 'asc'
      })
    } else {
      this.setData({
        sortType: type
      })
    }
    this.loadCourseList()
  },

  /**
   * 加载课程列表
   */
  loadCourseList() {
    this.setData({ loading: true })
    // 模拟加载
    setTimeout(() => {
      this.setData({ loading: false })
    }, 500)
  },

  /**
   * 点击课程
   */
  onCourseTap(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/course/detail/index?id=${id}`
    })
  }
})
