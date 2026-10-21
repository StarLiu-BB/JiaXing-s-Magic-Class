import { reactive } from 'vue'

/** 与后端一致：去掉首尾空白后最多 500 字（按 UTF-16 长度计，与 Java String.length 相同） */
export const MAX_QUESTION = 500
/** 收到 409（另一个回答还在生成）后暂停发送的时长 */
export const COOLDOWN_MS = 3000
/**
 * 停止后服务端要等它自己发现连接已断开才释放「生成中」占位（最长等待首字的时间），
 * 超过这个时长后再收到 409 就不自动重试了，交给学生自己判断。
 */
export const SERVER_RELEASE_MS = 30000

const STATUS_BY_CODE = { 1: 'done', 2: 'stopped', 3: 'failed' }
let keySeq = 0
const nextKey = (prefix) => `${prefix}-local-${++keySeq}`

function assistantMessage(fields) {
  return {
    key: nextKey('a'),
    role: 'assistant',
    id: null,
    content: '',
    status: 'streaming',
    sources: [],
    notice: null,
    recommendations: [],
    error: null,
    question: '',
    ...fields
  }
}

/**
 * 把历史消息（GET /ai/conversations/{id}/messages）转成界面消息。
 * 学生的问题后面没有 AI 回答（例如 AI 服务重启），标记为 unanswered，界面显示「此问题未完成回答」。
 */
export function toMessages(list) {
  const messages = []
  let lastQuestion = ''
  ;(list || []).forEach((m, i) => {
    if (m.role === 'user') {
      lastQuestion = m.content
      const next = list[i + 1]
      messages.push({ key: `u-${m.id}`, role: 'user', content: m.content, unanswered: !next || next.role !== 'assistant' })
      return
    }
    const status = STATUS_BY_CODE[m.status] || 'done'
    messages.push(assistantMessage({
      key: `a-${m.id}`,
      id: String(m.id),
      content: m.content || '',
      status,
      sources: m.sources || [],
      recommendations: m.recommendations || [],
      error: status === 'failed' ? { code: 503, msg: '这次回答没有完成' } : null,
      question: lastQuestion
    }))
  })
  return messages
}

/**
 * 当前会话的消息与生成状态。外部依赖全部注入，不依赖组件与路由，便于单测：
 * - askStream(body, { onMeta, onDelta, signal })：见 api/chat.js
 * - loadMessages(conversationId)：历史消息
 * - onConversationCreated(meta) / onActivity(conversationId)：通知「最近」列表
 * - onUnauthorized() / onConversationGone() / notify(type, message)
 */
export function createChatSession(deps) {
  const state = reactive({
    conversationId: null,
    courseScope: null,
    scopeLocked: false,
    messages: [],
    draft: '',
    inputError: '',
    generating: false,
    loading: false,
    cooldown: false
  })
  let controller = null
  let openTicket = 0
  /** 刚停止的提问与时间：此时收到 409 说明服务端还没放开占位，冷却后自动重发一次 */
  let stopped = null
  let cooldownTimer = null

  /** 中止进行中的回答，并让它的后续回调不再改动当前状态 */
  function detach() {
    controller?.abort()
    controller = null
    state.generating = false
    stopped = null
    endCooldown()
  }

  function endCooldown() {
    clearTimeout(cooldownTimer)
    cooldownTimer = null
    state.cooldown = false
  }

  function setScope(scope) {
    if (state.conversationId) {
      return
    }
    state.courseScope = scope ? { id: String(scope.id), title: scope.title || '' } : null
    state.scopeLocked = false
  }

  function clearScope() {
    if (!state.scopeLocked) {
      state.courseScope = null
    }
  }

  function reset() {
    detach()
    openTicket++
    Object.assign(state, { conversationId: null, courseScope: null, scopeLocked: false, messages: [], inputError: '', loading: false })
  }

  async function open(conversationId, scope = null) {
    detach()
    const ticket = ++openTicket
    Object.assign(state, {
      conversationId: String(conversationId),
      courseScope: scope ? { id: String(scope.id), title: scope.title || '' } : null,
      scopeLocked: !!scope,
      messages: [],
      inputError: '',
      loading: true
    })
    try {
      const list = await deps.loadMessages(state.conversationId)
      if (ticket === openTicket) {
        state.messages = toMessages(list)
      }
    } catch (error) {
      if (ticket !== openTicket) {
        return
      }
      if (error?.code === 404) {
        deps.notify('warning', '会话不存在或已删除')
        reset()
        deps.onConversationGone?.()
      } else {
        deps.notify('error', error?.message || '加载对话失败')
      }
    } finally {
      if (ticket === openTicket) {
        state.loading = false
      }
    }
  }

  /**
   * text 不传时发送输入框里的内容；传了（推荐问题、重试）则不动输入框。
   * auto 为 true 时是停止后的自动重试：失败的问题不再放回输入框（学生已经看见它发出去过一次）。
   */
  async function send(text, auto = false) {
    if (state.generating || state.cooldown || state.loading) {
      return
    }
    const fromDraft = text === undefined
    const question = (fromDraft ? state.draft : text || '').trim()
    state.inputError = ''
    if (!question) {
      state.inputError = '请输入问题'
      return
    }
    if (question.length > MAX_QUESTION) {
      state.inputError = `问题不能超过 ${MAX_QUESTION} 字`
      return
    }

    const isNew = !state.conversationId
    const body = { question }
    if (isNew) {
      if (state.courseScope) {
        body.courseId = state.courseScope.id
      }
    } else {
      body.conversationId = state.conversationId
    }
    state.messages.push({ key: nextKey('u'), role: 'user', content: question, unanswered: false })
    state.messages.push(assistantMessage({ question }))
    const userMessage = state.messages[state.messages.length - 2]
    const answer = state.messages[state.messages.length - 1]
    if (fromDraft) {
      state.draft = ''
    }
    state.generating = true
    const mine = (controller = new AbortController())

    const result = await deps.askStream(body, {
      signal: mine.signal,
      onMeta(meta) {
        if (controller !== mine) {
          return
        }
        answer.id = String(meta.messageId)
        if (isNew) {
          state.conversationId = String(meta.conversationId)
          state.scopeLocked = !!state.courseScope
          deps.onConversationCreated?.(meta)
        } else {
          deps.onActivity?.(state.conversationId)
        }
      },
      onDelta(piece) {
        answer.content += piece
      }
    })

    if (controller !== mine) {
      // 已被 detach（切换会话、新对话、离开页面）：这两条消息已不在当前界面上，
      // 迟到的拒绝不能回填输入框、提示或重置新会话
      if (result.kind !== 'rejected') {
        settle(result, { answer, userMessage, question, fromDraft, auto })
      }
      return
    }
    controller = null
    state.generating = false
    settle(result, { answer, userMessage, question, fromDraft, auto })
  }

  function settle(result, context) {
    const { answer } = context
    if (result.kind === 'done') {
      Object.assign(answer, {
        status: 'done',
        id: result.data?.messageId ? String(result.data.messageId) : answer.id,
        sources: result.data?.sources || [],
        notice: result.data?.notice || null,
        recommendations: result.data?.recommendations || []
      })
    } else if (result.kind === 'error') {
      Object.assign(answer, { status: 'failed', error: { code: result.code, msg: result.msg } })
    } else if (result.kind === 'aborted') {
      answer.status = 'stopped'
    } else if (result.kind === 'interrupted') {
      Object.assign(answer, { status: 'interrupted', error: { code: 0, msg: '连接中断' } })
    } else if (result.kind === 'rejected') {
      rejected(result, context)
    }
  }

  /** 流开始前被拒绝：服务端没有保存这次提问，撤回界面上的两条消息，问题回到输入框 */
  function rejected({ code, msg }, { answer, userMessage, question, fromDraft, auto }) {
    state.messages = state.messages.filter((m) => m !== answer && m !== userMessage)
    if (fromDraft && !auto && !state.draft) {
      state.draft = question
    }
    if (code === 400) {
      state.inputError = msg || '问题格式不正确'
    } else if (code === 401) {
      deps.onUnauthorized?.()
    } else if (code === 404) {
      deps.notify('warning', '会话不存在或已删除')
      reset()
      deps.onConversationGone?.()
    } else if (code === 409) {
      conflict(question, msg)
    } else {
      deps.notify('error', '发送失败，请重试')
    }
  }

  /**
   * 409：另一个回答还在生成。刚点过停止时必然是服务端还没发现连接断了（它要等下一次写出才发现），
   * 冷却后自动重发一次，学生不用自己再点；其他情况只冷却，不自动重发。
   */
  function conflict(question, msg) {
    deps.notify('warning', msg || '上一个回答还在生成')
    const auto = stopped && stopped.question === question && Date.now() - stopped.at < SERVER_RELEASE_MS
    stopped = null
    state.cooldown = true
    clearTimeout(cooldownTimer)
    cooldownTimer = setTimeout(() => {
      cooldownTimer = null
      state.cooldown = false
      if (auto) {
        send(question, true)
      }
    }, COOLDOWN_MS)
  }

  function stop() {
    const last = state.messages[state.messages.length - 1]
    if (last?.role === 'assistant' && last.question) {
      stopped = { question: last.question, at: Date.now() }
    }
    controller?.abort()
  }

  // detach：离开页面或切换会话前调用，中止回答并取消冷却后的自动重发
  return { state, send, retry: (question) => send(question), stop, detach, open, reset, setScope, clearScope }
}
