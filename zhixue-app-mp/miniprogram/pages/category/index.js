// pages/category/index.js
const { getCategoryList, getCoursePage } = require('../../api/course')

Page({
  /**
   * 页面的初始数据
   */
  data: {
    categoryList: [{ id: 0, name: '全部' }],
    currentCategory: 0,
    subCategoryList: [{ id: 0, name: '全部' }],
    currentSubCategory: 0,
    sortType: 'default',
    priceSort: 'asc',
    courseList: [],
    rawCourseList: [],
    allCategories: [],
    loading: false,
    hasMore: false
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad(options) {
    this.loadInitialData()
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
   * 选择分类
   */
  onCategorySelect(e) {
    const index = e.currentTarget.dataset.index
    this.setData({
      currentCategory: index,
      currentSubCategory: 0
    })
    this.updateSubCategories(index)
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
    getCoursePage({ pageNum: 1, pageSize: 50 })
      .then((res) => {
        const records = Array.isArray(res.data?.records) ? res.data.records : []
        const filtered = this.filterCourses(records)
        this.setData({
          rawCourseList: filtered,
          courseList: this.sortCourses(filtered),
          hasMore: false
        })
      })
      .catch((error) => {
        console.error('加载课程列表失败:', error)
        wx.showToast({
          title: '加载失败',
          icon: 'none'
        })
        this.setData({
          rawCourseList: [],
          courseList: []
        })
      })
      .finally(() => {
        this.setData({ loading: false })
      })
  },

  async loadInitialData() {
    this.setData({ loading: true })
    try {
      const res = await getCategoryList()
      const categories = Array.isArray(res.data) ? res.data : []
      const topCategories = categories.filter(item => !item.parentId || item.parentId === 0)
      this.setData({
        allCategories: categories,
        categoryList: [{ id: 0, name: '全部' }, ...topCategories]
      })
      this.updateSubCategories(0)
      await this.loadCourseList()
    } catch (error) {
      console.error('加载分类失败:', error)
      wx.showToast({
        title: '分类加载失败',
        icon: 'none'
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  updateSubCategories(categoryIndex) {
    if (categoryIndex === 0) {
      this.setData({
        subCategoryList: [{ id: 0, name: '全部' }]
      })
      return
    }

    const selected = this.data.categoryList[categoryIndex]
    const children = this.data.allCategories.filter(item => item.parentId === selected.id)
    this.setData({
      subCategoryList: [{ id: 0, name: '全部' }, ...children]
    })
  },

  filterCourses(records) {
    const { currentCategory, currentSubCategory, categoryList, subCategoryList, allCategories } = this.data
    if (currentCategory === 0) {
      return records
    }

    const category = categoryList[currentCategory]
    const subCategory = subCategoryList[currentSubCategory]
    if (subCategory && subCategory.id) {
      return records.filter(item => item.categoryId === subCategory.id)
    }

    const childIds = allCategories
      .filter(item => item.parentId === category.id)
      .map(item => item.id)

    if (childIds.length === 0) {
      return records.filter(item => item.categoryId === category.id)
    }

    return records.filter(item => childIds.includes(item.categoryId))
  },

  sortCourses(records) {
    const list = [...records]
    const { sortType, priceSort } = this.data
    if (sortType === 'price') {
      return list.sort((a, b) => {
        const left = Number(a.price || 0)
        const right = Number(b.price || 0)
        return priceSort === 'asc' ? left - right : right - left
      })
    }
    if (sortType === 'sales') {
      return list.sort((a, b) => Number(b.sales || 0) - Number(a.sales || 0))
    }
    if (sortType === 'new') {
      return list.sort((a, b) => new Date(b.createTime || 0) - new Date(a.createTime || 0))
    }
    return list
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
