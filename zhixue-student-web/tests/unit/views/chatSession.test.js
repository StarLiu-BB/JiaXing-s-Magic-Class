import { afterEach, describe, expect, it, vi } from 'vitest'
import { COOLDOWN_MS, MAX_QUESTION, SERVER_RELEASE_MS, createChatSession } from '@/views/chat/chatSession'

const CONV = '2112366523950714882'
const MSG = '2112366524101709826'
const CARD = {
  courseId: '2007', title: 'Java 并发编程实战', coverUrl: '', price: 99, purchased: false, previewable: true,
  lessons: [{ lessonId: '4024', chapterId: '3017', title: 'ConcurrentHashMap 原理', duration: 1080, chapterIndex: 2, lessonIndex: 2, playable: false }]
}

/** 可控的假 askStream：由测试决定何时回调 meta/delta、以什么结果结束；中止时与真实客户端一样返回 aborted */
function fakeAsk() {
  const calls = []
  const ask = vi.fn((body, options) => new Promise((resolve) => {
    options.signal?.addEventListener('abort', () => resolve({ kind: 'aborted' }))
    calls.push({ body, ...options, resolve })
  }))
  return { ask, calls, last: () => calls[calls.length - 1] }
}

function setup(overrides = {}) {
  const fake = fakeAsk()
  const deps = {
    askStream: fake.ask,
    loadMessages: vi.fn().mockResolvedValue([]),
    onConversationCreated: vi.fn(),
    onActivity: vi.fn(),
    onUnauthorized: vi.fn(),
    onConversationGone: vi.fn(),
    notify: vi.fn(),
    ...overrides
  }
  const session = createChatSession(deps)
  return { session, state: session.state, deps, fake }
}

afterEach(() => {
  vi.useRealTimers()
})

describe('新会话与追问', () => {
  it('首问带课程范围；meta 建立会话并锁定范围；delta 逐段追加；done 带出资料与推荐', async () => {
    const { session, state, deps, fake } = setup()
    session.setScope({ id: 2007, title: 'Java 并发编程实战' })
    state.draft = '  HashMap 为什么线程不安全  '

    const sending = session.send()
    expect(state.generating).toBe(true)
    expect(state.draft).toBe('')
    expect(fake.last().body).toEqual({ question: 'HashMap 为什么线程不安全', courseId: '2007' })

    fake.last().onMeta({ conversationId: CONV, messageId: MSG, title: 'HashMap 为什么线程不安全' })
    fake.last().onDelta('Hash')
    fake.last().onDelta('Map')
    expect(state.messages[1].content).toBe('HashMap')
    expect(state.conversationId).toBe(CONV)
    expect(state.scopeLocked).toBe(true)
    expect(deps.onConversationCreated).toHaveBeenCalledWith({ conversationId: CONV, messageId: MSG, title: 'HashMap 为什么线程不安全' })

    fake.last().resolve({ kind: 'done', data: { messageId: MSG, sources: [{ title: 'Java 集合与泛型 · HashMap 深入' }], notice: null, recommendations: [CARD] } })
    await sending
    expect(state.generating).toBe(false)
    expect(state.messages.map((m) => [m.role, m.content])).toEqual([['user', 'HashMap 为什么线程不安全'], ['assistant', 'HashMap']])
    expect(state.messages[1]).toMatchObject({ id: MSG, status: 'done', sources: [{ title: 'Java 集合与泛型 · HashMap 深入' }], recommendations: [CARD] })
  })

  it('追问带上会话 ID、不再带课程范围，并通知「最近」把它移到最前', async () => {
    const { session, state, deps, fake } = setup()
    session.setScope({ id: '2007', title: '' })
    const first = session.send('HashMap 为什么线程不安全')
    fake.last().onMeta({ conversationId: CONV, messageId: 'm1', title: 't' })
    fake.last().resolve({ kind: 'done', data: {} })
    await first

    const second = session.send('那 ConcurrentHashMap 呢')
    expect(fake.last().body).toEqual({ question: '那 ConcurrentHashMap 呢', conversationId: CONV })
    fake.last().onMeta({ conversationId: CONV, messageId: 'm2', title: 't' })
    fake.last().resolve({ kind: 'done', data: {} })
    await second

    expect(deps.onActivity).toHaveBeenCalledWith(CONV)
    expect(deps.onConversationCreated).toHaveBeenCalledOnce()
    expect(state.messages).toHaveLength(4)
  })

  it('生成中再次发送被忽略', () => {
    const { session, fake } = setup()
    session.send('第一个问题')
    session.send('第二个问题')
    expect(fake.ask).toHaveBeenCalledOnce()
  })

  it('会话建立后，限定课程的范围不能再清除', async () => {
    const { session, state, fake } = setup()
    session.setScope({ id: '2007', title: 'Java 并发编程实战' })
    const sending = session.send('线程池参数怎么设')
    fake.last().onMeta({ conversationId: CONV, messageId: MSG, title: 't' })
    session.clearScope()
    session.setScope(null)
    expect(state.courseScope).toEqual({ id: '2007', title: 'Java 并发编程实战' })
    fake.last().resolve({ kind: 'done', data: {} })
    await sending
  })
})

describe('输入校验（与后端一致：去掉首尾空白后 1–500 字）', () => {
  it.each([
    ['   ', '请输入问题'],
    ['问'.repeat(MAX_QUESTION + 1), `问题不能超过 ${MAX_QUESTION} 字`]
  ])('第 %# 种不合法输入', (text, error) => {
    const { session, state, fake } = setup()
    state.draft = text
    session.send()
    expect(state.inputError).toBe(error)
    expect(fake.ask).not.toHaveBeenCalled()
    expect(state.messages).toEqual([])
  })

  it('正好 500 字可以发送', () => {
    const { session, fake } = setup()
    session.send('问'.repeat(MAX_QUESTION))
    expect(fake.ask).toHaveBeenCalledOnce()
  })
})

describe('流开始后的结束方式', () => {
  it('点停止：保留已生成的文字，标记已停止，不出推荐', async () => {
    const { session, state, fake } = setup()
    const sending = session.send('讲讲线程池')
    fake.last().onDelta('线程池有 7 个参数')
    session.stop()
    await sending
    expect(fake.last().signal.aborted).toBe(true)
    expect(state.messages[1]).toMatchObject({ status: 'stopped', content: '线程池有 7 个参数', recommendations: [] })
    expect(state.generating).toBe(false)
  })

  it('error 事件：保留已生成的文字并给出原因', async () => {
    const { session, state, fake } = setup()
    const sending = session.send('讲讲线程池')
    fake.last().onDelta('半句')
    fake.last().resolve({ kind: 'error', code: 504, msg: '生成超时，已中断' })
    await sending
    expect(state.messages[1]).toMatchObject({ status: 'failed', content: '半句', error: { code: 504, msg: '生成超时，已中断' } })
  })

  it('连接中断', async () => {
    const { session, state, fake } = setup()
    const sending = session.send('讲讲线程池')
    fake.last().resolve({ kind: 'interrupted' })
    await sending
    expect(state.messages[1]).toMatchObject({ status: 'interrupted', error: { code: 0, msg: '连接中断' } })
  })
})

describe('流开始前被拒绝：撤回这次提问，问题回到输入框', () => {
  async function rejectWith(result, prepare) {
    const ctx = setup()
    await prepare?.(ctx)
    ctx.state.draft = 'HashMap 为什么线程不安全'
    const sending = ctx.session.send()
    ctx.fake.last().resolve({ kind: 'rejected', ...result })
    await sending
    return ctx
  }

  it('409：提示，并短暂禁止发送', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const { session, state, deps, fake } = await rejectWith({ code: 409, msg: '上一个回答还在生成' })
    expect(state.messages).toEqual([])
    expect(state.draft).toBe('HashMap 为什么线程不安全')
    expect(deps.notify).toHaveBeenCalledWith('warning', '上一个回答还在生成')
    expect(state.cooldown).toBe(true)
    session.send()
    expect(fake.ask).toHaveBeenCalledOnce()
    vi.advanceTimersByTime(COOLDOWN_MS)
    expect(state.cooldown).toBe(false)
  })

  it('401：交给登录失效处理，问题留在输入框（登录回来还在）', async () => {
    const { state, deps } = await rejectWith({ code: 401, msg: '令牌无效' })
    expect(deps.onUnauthorized).toHaveBeenCalledOnce()
    expect(state.draft).toBe('HashMap 为什么线程不安全')
  })

  it('404：会话已不存在，回到新对话', async () => {
    const { state, deps } = await rejectWith({ code: 404, msg: '会话不存在或已删除' }, ({ session }) => session.open(CONV))
    expect(deps.notify).toHaveBeenCalledWith('warning', '会话不存在或已删除')
    expect(deps.onConversationGone).toHaveBeenCalledOnce()
    expect(state.conversationId).toBe(null)
    expect(state.draft).toBe('HashMap 为什么线程不安全')
  })

  it('400：在输入框下方提示', async () => {
    const { state } = await rejectWith({ code: 400, msg: '问题不能超过 500 字' })
    expect(state.inputError).toBe('问题不能超过 500 字')
  })

  it('500：提示发送失败', async () => {
    const { deps } = await rejectWith({ code: 500, msg: '发送失败，请重试' })
    expect(deps.notify).toHaveBeenCalledWith('error', '发送失败，请重试')
  })

  it('重试与推荐问题不动输入框里正在写的草稿', async () => {
    const { session, state, fake } = setup()
    state.draft = '还没写完的草稿'
    const sending = session.retry('HashMap 为什么线程不安全')
    expect(fake.last().body.question).toBe('HashMap 为什么线程不安全')
    fake.last().resolve({ kind: 'rejected', code: 409, msg: '上一个回答还在生成' })
    await sending
    expect(state.draft).toBe('还没写完的草稿')
  })
})

describe('停止后的 409：自动重发一次', () => {
  /** 先发一题并点停止，让状态机记下「刚停止的提问」 */
  async function stopThenReject(ctx, opts = {}) {
    const sending = ctx.session.send('HashMap 为什么线程不安全')
    ctx.fake.last().onDelta('半句')
    ctx.session.stop()
    ctx.fake.last().resolve({ kind: 'aborted' })
    await sending
    vi.advanceTimersByTime(opts.elapse ?? 0)
    const retry = ctx.session.send('HashMap 为什么线程不安全')
    ctx.fake.last().resolve({ kind: 'rejected', code: 409, msg: '上一个回答还在生成' })
    await retry
    return ctx
  }

  it('冷却结束后自动重发，成功后正常显示', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const ctx = setup()
    await stopThenReject(ctx)
    expect(ctx.fake.ask).toHaveBeenCalledTimes(2)
    expect(ctx.state.cooldown).toBe(true)
    expect(ctx.state.draft).toBe('')

    vi.advanceTimersByTime(COOLDOWN_MS)
    expect(ctx.fake.ask).toHaveBeenCalledTimes(3)
    expect(ctx.fake.last().body.question).toBe('HashMap 为什么线程不安全')
    ctx.fake.last().resolve({ kind: 'done', data: {} })
    await Promise.resolve()
    await Promise.resolve()
    // 第一条是已停止的回答，自动重发产生第 3、4 条
    expect(ctx.state.messages.map((m) => m.role === 'assistant' && m.status)).toEqual([false, 'stopped', false, 'done'])
  })

  it('只自动重发一次：重发又被 409 就只冷却，不再无限重试', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const ctx = setup()
    await stopThenReject(ctx)
    vi.advanceTimersByTime(COOLDOWN_MS)
    expect(ctx.fake.ask).toHaveBeenCalledTimes(3)
    ctx.fake.last().resolve({ kind: 'rejected', code: 409, msg: '上一个回答还在生成' })
    await Promise.resolve()
    await Promise.resolve()
    vi.advanceTimersByTime(COOLDOWN_MS * 3)
    expect(ctx.fake.ask).toHaveBeenCalledTimes(3)
    expect(ctx.state.cooldown).toBe(false)
  })

  it('不是刚停止（已超过 30 秒）就只冷却', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const ctx = setup()
    await stopThenReject(ctx, { elapse: SERVER_RELEASE_MS + 1 })
    vi.advanceTimersByTime(COOLDOWN_MS)
    expect(ctx.fake.ask).toHaveBeenCalledTimes(2)
  })

  it('没点停止的 409（例如另一个标签页在生成）不自动重发', async () => {
    const { session, state, fake } = setup()
    const sending = session.send('HashMap 为什么线程不安全')
    fake.last().resolve({ kind: 'rejected', code: 409, msg: '上一个回答还在生成' })
    await sending
    expect(state.cooldown).toBe(true)
    expect(fake.ask).toHaveBeenCalledOnce()
  })
})

describe('打开历史会话', () => {
  const HISTORY = [
    { id: '1', role: 'user', content: 'Q1', status: 1 },
    { id: '2', role: 'assistant', content: 'A1', status: 1, sources: [{ title: 'S' }], recommendations: [CARD] },
    { id: '3', role: 'user', content: 'Q2', status: 1 },
    { id: '4', role: 'assistant', content: '半截', status: 2, sources: [], recommendations: [] },
    { id: '5', role: 'user', content: 'Q3', status: 1 },
    { id: '6', role: 'assistant', content: '', status: 3, sources: [], recommendations: [] },
    { id: '7', role: 'user', content: 'Q4', status: 1 }
  ]

  it('按服务端状态还原消息；没有回答的问题标为未完成；范围锁定', async () => {
    const { session, state, deps } = setup({ loadMessages: vi.fn().mockResolvedValue(HISTORY) })
    await session.open(CONV, { id: 2007, title: '' })
    expect(deps.loadMessages).toHaveBeenCalledWith(CONV)
    expect(state.messages.map((m) => (m.role === 'user' ? (m.unanswered ? 'unanswered' : 'user') : m.status))).toEqual([
      'user', 'done', 'user', 'stopped', 'user', 'failed', 'unanswered'
    ])
    expect(state.messages[1].recommendations).toEqual([CARD])
    expect(state.messages[5]).toMatchObject({ question: 'Q3', error: { code: 503 } })
    expect(state).toMatchObject({ conversationId: CONV, courseScope: { id: '2007', title: '' }, scopeLocked: true, loading: false })
  })

  it('切换会话会中止正在生成的回答，旧回答的结果不影响新会话', async () => {
    const { session, state, fake } = setup()
    const sending = session.send('第一个问题')
    await session.open(CONV)
    await sending
    expect(fake.last().signal.aborted).toBe(true)
    expect(state.generating).toBe(false)
    expect(state.messages).toEqual([])
  })

  it('会话不存在（404）：提示并回到新对话', async () => {
    const error = Object.assign(new Error('会话不存在或已删除'), { code: 404 })
    const { session, state, deps } = setup({ loadMessages: vi.fn().mockRejectedValue(error) })
    await session.open(CONV)
    expect(state.conversationId).toBe(null)
    expect(deps.onConversationGone).toHaveBeenCalledOnce()
  })
})
