// pages/study/index.js
Page({
  /**
   * 页面的初始数据
   */
  data: {
    studyStats: {
      totalHours: 0,
      totalCourses: 0,
      todayHours: 0,
      continuousDays: 0,
      completedCourses: 0
    },
    weekDays: ['日', '一', '二', '三', '四', '五', '六'],
    calendarDays: [],
    recentCourses: [],
    studyPlans: [],
    rankList: [],
    loading: false
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad(options) {
    this.generateCalendar()
  },

  /**
   * 生命周期函数--监听页面显示
   */
  onShow() {
    // 可以在这里刷新数据
  },

  /**
   * 生成日历数据
   */
  generateCalendar() {
    const days = []
    const today = new Date()
    const currentMonth = today.getMonth()
    const currentYear = today.getFullYear()
    const currentDay = today.getDate()

    // 获取当月第一天是星期几
    const firstDay = new Date(currentYear, currentMonth, 1).getDay()
    // 获取当月天数
    const daysInMonth = new Date(currentYear, currentMonth + 1, 0).getDate()
    // 获取上月天数
    const daysInPrevMonth = new Date(currentYear, currentMonth, 0).getDate()

    // 填充上月日期
    for (let i = firstDay - 1; i >= 0; i--) {
      days.push({
        day: daysInPrevMonth - i,
        isCurrentMonth: false,
        isToday: false,
        hasStudy: false,
        date: ''
      })
    }

    // 填充当月日期
    for (let i = 1; i <= daysInMonth; i++) {
      days.push({
        day: i,
        isCurrentMonth: true,
        isToday: i === currentDay,
        hasStudy: false,
        date: `${currentYear}-${currentMonth + 1}-${i}`
      })
    }

    // 填充下月日期
    const remainingDays = 42 - days.length
    for (let i = 1; i <= remainingDays; i++) {
      days.push({
        day: i,
        isCurrentMonth: false,
        isToday: false,
        hasStudy: false,
        date: ''
      })
    }

    this.setData({
      calendarDays: days
    })
  },

  /**
   * 点击日历
   */
  onCalendarTap() {
    wx.showToast({
      title: '查看学习详情',
      icon: 'none'
    })
  },

  /**
   * 点击全部课程
   */
  onAllCourseTap() {
    wx.switchTab({
      url: '/pages/category/index'
    })
  },

  /**
   * 点击课程
   */
  onCourseTap(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/course/detail/index?id=${id}`
    })
  },

  /**
   * 继续学习
   */
  onContinueTap(e) {
    e.stopPropagation()
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: `/pages/course/player/index?courseId=${id}`
    })
  },

  /**
   * 去学习
   */
  onGoStudyTap() {
    wx.switchTab({
      url: '/pages/category/index'
    })
  },

  /**
   * 点击计划管理
   */
  onPlanTap() {
    wx.showToast({
      title: '学习计划管理',
      icon: 'none'
    })
  },

  /**
   * 点击排行榜
   */
  onRankTap() {
    wx.showToast({
      title: '查看完整排行榜',
      icon: 'none'
    })
  }
})
