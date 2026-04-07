const { getCoursePage } = require('../../api/course')

Page({
  data: {
    keyword: '',
    loading: false,
    resultList: []
  },

  onLoad(options) {
    const keyword = (options.keyword || '').trim()
    this.setData({ keyword })
    if (keyword) {
      this.searchCourses()
    }
  },

  onKeywordInput(e) {
    this.setData({ keyword: e.detail.value })
  },

  onSearchConfirm() {
    this.searchCourses()
  },

  async searchCourses() {
    const keyword = (this.data.keyword || '').trim()
    if (!keyword) {
      wx.showToast({
        title: '请输入关键词',
        icon: 'none'
      })
      return
    }
    this.setData({ loading: true })
    try {
      const res = await getCoursePage({
        pageNum: 1,
        pageSize: 40,
        keyword,
        title: keyword
      })
      const rawList = res.data?.records || res.data?.list || []
      const lower = keyword.toLowerCase()
      const resultList = rawList.filter((item) => {
        const title = `${item.title || ''}`.toLowerCase()
        const teacher = `${item.teacher || item.teacherName || ''}`.toLowerCase()
        return title.includes(lower) || teacher.includes(lower)
      })
      this.setData({ resultList })
    } catch (error) {
      console.error('课程搜索失败:', error)
      this.setData({ resultList: [] })
      wx.showToast({
        title: '搜索失败',
        icon: 'none'
      })
    } finally {
      this.setData({ loading: false })
    }
  },

  onCourseTap(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/course/detail/index?id=${id}`
    })
  }
})
