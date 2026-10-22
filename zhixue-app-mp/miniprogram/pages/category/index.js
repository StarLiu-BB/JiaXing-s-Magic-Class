const { getCategoryList, getCoursePage } = require('../../api/course')
const PAGE_SIZE = 10

Page({
  data: {
    categoryList: [{ id: '0', name: '全部' }], currentCategory: 0,
    subCategoryList: [{ id: '0', name: '全部' }], currentSubCategory: 0,
    categoryId: null, sortType: 'default', priceSort: 'asc', courseList: [],
    allCategories: [], loading: false, hasMore: false, pageNum: 0, loadError: ''
  },

  onLoad() {
    this._disposed = false
    this._readyPromise = this.loadInitialData()
    return this._readyPromise
  },

  onShow() {
    if (!this._ready) return this._readyPromise
    if (this.consumePendingCategory()) return this.fetchCourses(true)
  },

  onUnload() { this._disposed = true },

  loadInitialData() {
    if (this._initialPromise) return this._initialPromise
    this._initialPromise = this.initializeCategories().finally(() => { this._initialPromise = null })
    return this._initialPromise
  },

  async initializeCategories() {
    try {
      const res = await getCategoryList()
      if (res.code !== 200) throw new Error(res.msg || '分类加载失败')
      if (this._disposed) return
      const categories = Array.isArray(res.data) ? res.data : []
      this.setData({
        allCategories: categories,
        categoryList: [{ id: '0', name: '全部' }, ...categories.filter(item => !item.parentId || String(item.parentId) === '0')]
      })
      this._ready = true
      this.consumePendingCategory()
      this.updateSubCategories(this.data.currentCategory)
      await this.fetchCourses(true)
    } catch (error) {
      if (!this._disposed) this.setData({ loadError: '分类加载失败，点击重试' })
      console.error('加载分类失败:', error)
    }
  },

  consumePendingCategory() {
    const app = getApp()
    const pending = app.globalData.pendingCategoryId
    if (pending === undefined || pending === null) return false
    app.globalData.pendingCategoryId = null
    const categoryId = String(pending)
    const categories = this.data.allCategories
    const selected = categories.find(item => String(item.id) === categoryId)
    const parentId = selected && selected.parentId && String(selected.parentId) !== '0' ? String(selected.parentId) : categoryId
    const index = this.data.categoryList.findIndex(item => String(item.id) === parentId)
    this.setData({ currentCategory: index >= 0 ? index : 0, currentSubCategory: 0, categoryId: categoryId === '0' ? null : categoryId })
    this.updateSubCategories(this.data.currentCategory)
    const childIndex = this.data.subCategoryList.findIndex(item => String(item.id) === categoryId)
    if (childIndex > 0) this.setData({ currentSubCategory: childIndex })
    return true
  },

  updateSubCategories(index) {
    const selected = this.data.categoryList[index]
    const children = index > 0 && selected
      ? this.data.allCategories.filter(item => String(item.parentId) === String(selected.id)) : []
    this.setData({ subCategoryList: [{ id: '0', name: '全部' }, ...children] })
  },

  onCategorySelect(event) {
    const index = Number(event.currentTarget.dataset.index)
    const selected = this.data.categoryList[index]
    if (!selected) return
    this.setData({ currentCategory: index, currentSubCategory: 0, categoryId: index === 0 ? null : String(selected.id) })
    this.updateSubCategories(index)
    return this.fetchCourses(true)
  },

  onSubCategorySelect(event) {
    const index = Number(event.currentTarget.dataset.index)
    const selected = this.data.subCategoryList[index]
    if (!selected) return
    const parent = this.data.categoryList[this.data.currentCategory]
    this.setData({ currentSubCategory: index, categoryId: index > 0 ? String(selected.id) : this.data.currentCategory > 0 ? String(parent.id) : null })
    return this.fetchCourses(true)
  },

  onSortTap(event) {
    const type = event.currentTarget.dataset.type
    if (!['default', 'price', 'new'].includes(type)) return
    const priceSort = type === 'price' && this.data.sortType === 'price' && this.data.priceSort === 'asc' ? 'desc' : 'asc'
    this.setData({ sortType: type, priceSort })
    return this.fetchCourses(true)
  },

  currentSort() {
    if (this.data.sortType === 'price') return this.data.priceSort === 'asc' ? 'price_asc' : 'price_desc'
    return this.data.sortType === 'new' ? 'new' : ''
  },

  fetchCourses(reset = false) {
    if (this._disposed) return Promise.resolve()
    if (this._fetchPromise) {
      if (reset) this._pendingReset = true
      return this._fetchPromise
    }
    this._fetchPromise = (async () => {
      let restart = reset
      do {
        this._pendingReset = false
        if (restart) this.setData({ courseList: [], pageNum: 0, hasMore: false })
        const pageNum = restart ? 1 : this.data.pageNum + 1
        this.setData({ loading: true, loadError: '' })
        try {
          const res = await getCoursePage({ pageNum, pageSize: PAGE_SIZE, categoryId: this.data.categoryId || undefined, sort: this.currentSort() })
          if (this._disposed) return
          if (this._pendingReset) continue
          if (res.code !== 200 || !res.data || !Array.isArray(res.data.records)) throw new Error(res.msg || '课程数据无效')
          const list = restart ? res.data.records : this.data.courseList.concat(res.data.records)
          this.setData({
            courseList: list, pageNum,
            hasMore: Number.isFinite(Number(res.data.total)) ? pageNum * PAGE_SIZE < Number(res.data.total) : res.data.records.length === PAGE_SIZE
          })
        } catch (error) {
          if (!this._disposed && !this._pendingReset) {
            this.setData({ loadError: '课程加载失败，点击重试' })
            console.error('加载课程失败:', error)
          }
        } finally {
          restart = this._pendingReset
        }
      } while (restart && !this._disposed)
    })().finally(() => {
      this._fetchPromise = null
      if (!this._disposed) this.setData({ loading: false })
    })
    return this._fetchPromise
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) return this.fetchCourses(false)
  },
  onRetry() { return this._ready ? this.fetchCourses(this.data.pageNum === 0) : this.loadInitialData() },
  onSearchTap() { wx.navigateTo({ url: '/pages/search/index' }) },
  onCourseTap(event) { wx.navigateTo({ url: `/pages/course/detail/index?id=${event.currentTarget.dataset.id}` }) }
})
