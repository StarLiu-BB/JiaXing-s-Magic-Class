// pages/study/index.js
Page({
  /**
   * 页面的初始数据
   */
  data: {
    // 学习统计
    studyStats: {
      totalHours: 128,
      totalCourses: 12,
      todayHours: 2.5,
      continuousDays: 7,
      completedCourses: 5
    },
    // 星期
    weekDays: ['日', '一', '二', '三', '四', '五', '六'],
    // 日历天数
    calendarDays: [],
    // 最近学习
    recentCourses: [
      {
        id: 1,
        title: 'Java 从入门到精通',
        cover: '',
        currentChapter: '第5章 面向对象编程',
        progress: 45,
        lastStudyTime: '今天'
      },
      {
        id: 2,
        title: 'Spring Boot 实战',
        cover: '',
        currentChapter: '第3章 数据库操作',
        progress: 30,
        lastStudyTime: '昨天'
      },
      {
        id: 3,
        title: 'Vue.js 前端开发',
        cover: '',
        currentChapter: '第2章 组件化开发',
        progress: 20,
        lastStudyTime: '2天前'
      }
    ],
    // 学习计划
    studyPlans: [
      {
        id: 1,
        title: '完成 Java 基础课程',
        description: '每天学习2小时，预计7天完成',
        status: 'ongoing'
      },
      {
        id: 2,
        title: 'Spring Boot 项目实战',
        description: '完成一个完整的项目',
        status: 'completed'
      }
    ],
    // 排行榜
    rankList: [
      { id: 1, nickname: '学霸小明', avatar: '', studyHours: 256 },
      { id: 2, nickname: '努力的小红', avatar: '', studyHours: 198 },
      { id: 3, nickname: '编程达人', avatar: '', studyHours: 175 },
      { id: 4, nickname: '学习狂人', avatar: '', studyHours: 142 },
      { id: 5, nickname: '代码工匠', avatar: '', studyHours: 128 }
    ],
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
        hasStudy: Math.random() > 0.5, // 模拟学习记录
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
      url: `/pages/course/player/index?id=${id}`
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
