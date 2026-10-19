/**
 * AI 课程答疑
 * 从课程详情进入时带 courseId，只检索该课程资料；从「我的」进入则检索全部知识库。
 */
const { askCourse } = require('../../../api/ai')

const app = getApp()
const MAX_LENGTH = 500

Page({
  data: {
    courseId: null,
    courseTitle: '',
    input: '',
    messages: [],
    loading: false,
    scrollTarget: '',
    suggestions: []
  },

  onLoad(options) {
    const courseId = options.courseId || null
    const courseTitle = options.title ? decodeURIComponent(options.title) : ''
    this.setData({
      courseId,
      courseTitle,
      suggestions: courseId
        ? ['这门课主要讲什么？', '学这门课需要什么基础？', '课程里的重点难点有哪些？']
        : ['购买课程后怎么开始学习？', '网关在微服务里起什么作用？', '弹幕为什么没有显示出来？']
    })
    wx.setNavigationBarTitle({ title: courseTitle ? `问 AI · ${courseTitle}` : 'AI 课程助教' })
  },

  onShow() {
    if (!app.globalData.isLogin) {
      wx.showModal({
        title: '提示',
        content: 'AI 答疑需要先登录',
        confirmText: '去登录',
        success: (res) => {
          if (res.confirm) {
            wx.switchTab({ url: '/pages/my/index' })
          } else {
            wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/index/index' }) })
          }
        }
      })
    }
  },

  onInput(e) {
    this.setData({ input: e.detail.value })
  },

  onSuggestionTap(e) {
    this.setData({ input: e.currentTarget.dataset.text })
    this.onSend()
  },

  async onSend() {
    const question = (this.data.input || '').trim()
    if (!question || this.data.loading) {
      return
    }
    if (question.length > MAX_LENGTH) {
      wx.showToast({ title: `问题不能超过 ${MAX_LENGTH} 字`, icon: 'none' })
      return
    }
    this.pushMessage({ role: 'user', content: question })
    this.setData({ input: '', loading: true })
    const start = Date.now()
    try {
      const answer = await askCourse(question, this.data.courseId)
      this.pushMessage({
        role: 'assistant',
        content: answer || '没有得到回答，请换个问法再试。',
        cost: ((Date.now() - start) / 1000).toFixed(1)
      })
    } catch (error) {
      // 失败如实显示，不伪装成 AI 的回答
      this.pushMessage({
        role: 'assistant',
        failed: true,
        content: `没有拿到回答：${(error && (error.message || error.errMsg)) || '服务暂时不可用'}`
      })
    } finally {
      this.setData({ loading: false, scrollTarget: 'bottom' })
    }
  },

  pushMessage(msg) {
    const id = `m${this.data.messages.length}`
    // 小程序 text 组件不解析 HTML，直接展示原文，不存在注入风险；只去掉 Markdown 加粗符号
    const content = String(msg.content).replace(/\*\*(.*?)\*\*/g, '$1')
    this.setData({
      messages: this.data.messages.concat([{ ...msg, id, content }]),
      scrollTarget: id
    })
  }
})
