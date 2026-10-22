const { startStream } = require('../../../api/ai-stream')
const { listConversations, getConversation, getMessages, renameConversation, deleteConversation } = require('../../../api/conversations')
const { getBaseUrl } = require('../../../config')
const { getToken, removeToken } = require('../../../utils/auth')
const app = getApp()
const STATUS = { 1: 'done', 2: 'stopped', 3: 'failed' }

Page({
  data: {
    courseId: null, courseTitle: '', conversationId: null,
    input: '', messages: [], loading: false, cooldown: false,
    scrollTarget: '', suggestions: [], conversations: [],
    historyOpen: false, historyLoading: false, historyPage: 0, historyHasMore: false
  },

  onLoad(options) {
    this._generation = 0
    this._messages = []
    this._openTicket = 0
    this._historyTicket = 0
    this._visible = true
    let title = options.title || ''
    try { title = decodeURIComponent(title) } catch {}
    this.setData({
      courseId: options.courseId || null, courseTitle: title,
      suggestions: options.courseId
        ? ['线程池核心参数怎么设置？', '线程安全问题的根源是什么？', 'ConcurrentHashMap 如何保证线程安全？']
        : ['HashMap 为什么线程不安全？', '线程池核心参数怎么设置？', 'String 为什么是不可变的？']
    })
    wx.setNavigationBarTitle({ title: 'AI 课程助教' })
  },

  onShow() {
    this._visible = true
    if (app.globalData.isLogin) {
      this.restoreDraft()
      if (!app.globalData.userInfo?.userId) {
        app.getUserInfo().then(() => { if (this._visible) this.restoreDraft() })
      }
    }
    if (!app.globalData.isLogin) {
      wx.showModal({ title: '提示', content: 'AI 答疑需要先登录', confirmText: '去登录', success: res => {
        if (res.confirm) wx.switchTab({ url: '/pages/my/index' })
        else wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/index/index' }) })
      } })
    } else this.loadHistory()
  },
  restoreDraft() {
    try {
      const draft = wx.getStorageSync('zhixue_ai_draft')
      if (!draft) return
      const owner = String(app.globalData.userInfo?.userId || '')
      if (!owner) return
      wx.removeStorageSync('zhixue_ai_draft')
      if (draft.owner !== owner || Date.now() - draft.at > 1800000) return
      this.setData({ input: draft.question, courseId: draft.courseId, courseTitle: draft.courseTitle, conversationId: draft.conversationId })
      if (draft.conversationId) this.onOpenConversation({ currentTarget: { dataset: { id: draft.conversationId } } })
    } catch { wx.showToast({ title: '草稿恢复失败', icon: 'none' }) }
  },
  onHide() { this._visible = false; this.detach(); this._historyTicket++; this.setData({ historyLoading: false }) },
  onUnload() { this._visible = false; this.detach(); this._historyTicket++; this.setData({ historyLoading: false }) },
  onInput(event) { this.setData({ input: event.detail.value }) },
  onSuggestionTap(event) { this.setData({ input: event.currentTarget.dataset.text }); return this.onSend() },

  flush() {
    clearTimeout(this._flushTimer)
    this._flushTimer = null
    this.setData({ messages: this._messages.slice(), scrollTarget: 'bottom' })
  },
  queueFlush() {
    if (!this._flushTimer) this._flushTimer = setTimeout(() => { if (this._visible) this.flush() }, 80)
  },
  detach() {
    this._openTicket++
    this._generation++
    this._stream?.abort()
    this._stream = null
    clearTimeout(this._flushTimer)
    this._flushTimer = null
    clearTimeout(this._cooldownTimer)
    this._cooldownTimer = null
    const answer = this._messages[this._messages.length - 1]
    if (answer?.status === 'streaming') answer.status = 'stopped'
    this.setData({ loading: false, cooldown: false, messages: this._messages.slice() })
  },
  onStop() { this.detach() },

  async onSend(text) {
    const question = (typeof text === 'string' ? text : this.data.input || '').trim()
    if (!question || this.data.loading || this.data.cooldown) return
    if (!app.globalData.isLogin) { wx.switchTab({ url: '/pages/my/index' }); return }
    if (question.length > 500) { wx.showToast({ title: '问题不能超过500字', icon: 'none' }); return }
    const generation = ++this._generation
    const owner = String(app.globalData.userInfo?.userId || '')
    const stamp = Date.now()
    const answer = { id: `a-${stamp}-${generation}`, role: 'assistant', content: '', status: 'streaming', question, sources: [], recommendations: [] }
    const user = { id: `u-${stamp}-${generation}`, role: 'user', content: question }
    const body = { question }
    if (this.data.conversationId) body.conversationId = this.data.conversationId
    else if (this.data.courseId) body.courseId = this.data.courseId
    this._messages.push(user, answer)
    this.setData({ input: '', messages: this._messages.slice(), loading: true, scrollTarget: 'bottom' })
    try {
      const stream = startStream(body, {
        request: options => wx.request(options), baseUrl: getBaseUrl(), token: getToken(),
        onMeta: meta => {
          if (generation !== this._generation || !this._visible) return
          answer.serverId = String(meta.messageId)
          this.setData({ conversationId: String(meta.conversationId) })
        },
        onDelta: piece => {
          if (generation !== this._generation || !this._visible) return
          answer.content += piece
          this.queueFlush()
        }
      })
      this._stream = stream
      const result = await stream.promise
      if (generation !== this._generation || !this._visible) return
      this._stream = null
      if (result.kind === 'done') {
        const saved = typeof result.data.saved === 'boolean' ? result.data.saved : null
        const saveNotice = saved === false
          ? result.data.saveNotice || '回答已生成，但未保存到历史。请先复制保留。'
          : result.data.saved === null
            ? result.data.saveNotice || '回答已生成，保存状态尚未确认。请稍后刷新历史核对或先复制保留。'
            : ''
        Object.assign(answer, {
          status: 'done', sources: result.data.sources || [], notice: result.data.notice || '',
          recommendations: (result.data.recommendations || []).slice(0, 3), saved, saveNotice,
          cost: ((Date.now() - stamp) / 1000).toFixed(1)
        })
      } else if (result.kind === 'rejected') {
        this._messages = this._messages.filter(message => message !== user && message !== answer)
        this.setData({ input: question })
        if (result.code === 401) {
          try {
            wx.setStorageSync('zhixue_ai_draft', { owner, question, courseId: this.data.courseId, courseTitle: this.data.courseTitle, conversationId: this.data.conversationId, at: Date.now() })
          } catch { wx.showToast({ title: '草稿保存失败，请复制问题', icon: 'none' }) }
          removeToken()
          app.globalData.token = ''
          app.globalData.isLogin = false
          app.globalData.userInfo = null
          wx.switchTab({ url: '/pages/my/index' })
        } else if (result.code === 404) {
          this._messages = []
          this.setData({ conversationId: null, courseId: null, courseTitle: '' })
          this.loadHistory()
        } else if (result.code === 409) {
          this.setData({ cooldown: true })
          this._cooldownTimer = setTimeout(() => { this._cooldownTimer = null; if (this._visible) this.setData({ cooldown: false }) }, 3000)
        }
        wx.showToast({ title: result.msg || '发送失败，请重试', icon: 'none' })
      } else {
        answer.status = result.kind === 'aborted' ? 'stopped' : result.kind === 'error' ? 'failed' : 'interrupted'
        answer.error = result.msg || '连接中断，请重试'
      }
      if (result.kind === 'done') this.loadHistory()
    } catch {
      if (generation === this._generation && this._visible) {
        answer.status = 'failed'
        answer.error = '发送失败，请重试'
        this._stream?.abort()
      }
    } finally {
      if (generation === this._generation && this._visible) {
        this._stream = null
        this.setData({ loading: false })
        this.flush()
      }
    }
  },
  onRetry(event) { return this.onSend(event.currentTarget.dataset.question) },
  onClearScope() { if (!this.data.conversationId) this.setData({ courseId: null, courseTitle: '' }) },
  onNew() {
    this.detach()
    this._openTicket++
    this._messages = []
    this.setData({ conversationId: null, courseId: null, courseTitle: '', messages: [], historyOpen: false })
  },
  onToggleHistory() { this.setData({ historyOpen: !this.data.historyOpen }); if (this.data.historyOpen) this.loadHistory() },

  async loadHistory(more = false) {
    if (this.data.historyLoading || !this._visible) return
    const ticket = ++this._historyTicket
    this.setData({ historyLoading: true })
    const page = more ? this.data.historyPage + 1 : 1
    try {
      const data = await listConversations(page)
      if (!this._visible || ticket !== this._historyTicket) return
      this.setData({ conversations: more ? this.data.conversations.concat(data.records || []) : data.records || [], historyPage: page, historyHasMore: page * 20 < Number(data.total || 0) })
    } catch { if (this._visible && ticket === this._historyTicket) wx.showToast({ title: '最近会话加载失败', icon: 'none' }) }
    finally { if (ticket === this._historyTicket) this.setData({ historyLoading: false }) }
  },
  onMoreHistory() { return this.loadHistory(true) },
  async onOpenConversation(event) {
    this.detach()
    const ticket = ++this._openTicket
    const id = String(event.currentTarget.dataset.id)
    this.setData({ historyOpen: false, loading: true })
    try {
      const [conversation, rows] = await Promise.all([getConversation(id), getMessages(id)])
      if (ticket !== this._openTicket || !this._visible) return
      let question = ''
      const messages = rows.map((row, index) => {
        if (row.role === 'user') {
          question = row.content
          return { ...row, id: String(row.id), unanswered: !rows[index + 1] || rows[index + 1].role !== 'assistant' }
        }
        return { ...row, id: String(row.id), question, status: STATUS[row.status] || 'done', sources: row.sources || [], recommendations: (row.recommendations || []).slice(0, 3), error: '这次回答没有完成' }
      })
      this._messages = messages
      this.setData({ conversationId: id, courseId: conversation.courseId ? String(conversation.courseId) : null, courseTitle: conversation.courseId ? '指定课程' : '', messages })
      this.flush()
    } catch { if (ticket === this._openTicket && this._visible) wx.showToast({ title: '会话加载失败或已删除', icon: 'none' }) }
    finally { if (ticket === this._openTicket && this._visible) this.setData({ loading: false }) }
  },
  onRenameConversation(event) {
    const { id, title } = event.currentTarget.dataset
    wx.showModal({ title: '重命名对话', editable: true, content: title, success: async res => {
      if (!res.confirm) return
      const next = (res.content || '').trim()
      if (!next || next.length > 64) { wx.showToast({ title: '标题为1至64字', icon: 'none' }); return }
      try { await renameConversation(String(id), next); this.loadHistory() } catch {}
    } })
  },
  onDeleteConversation(event) {
    const id = String(event.currentTarget.dataset.id)
    wx.showModal({ title: '删除对话', content: '删除后无法恢复，确定删除？', success: async res => {
      if (!res.confirm) return
      const ticket = this._openTicket
      try {
        await deleteConversation(id)
        if (!this._visible) return
        if (ticket === this._openTicket && this.data.conversationId === id) this.onNew()
        this.loadHistory()
      } catch { if (this._visible) wx.showToast({ title: '删除失败，请重试', icon: 'none' }) }
    } })
  },
  onCourseTap(event) { wx.navigateTo({ url: `/pages/course/detail/index?id=${event.currentTarget.dataset.id}` }) },
  onLessonTap(event) {
    const { courseId, lessonId, chapterId } = event.currentTarget.dataset
    wx.navigateTo({ url: `/pages/course/player/index?courseId=${courseId}&lessonId=${lessonId}&chapterId=${chapterId}` })
  }
})
