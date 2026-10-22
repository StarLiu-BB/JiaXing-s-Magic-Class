const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

function setup(overrides = {}) {
  let definition
  const streams = []
  const app = { globalData: { isLogin: true, token: 'test', userInfo: { userId: '1003' } } }
  const conversations = {
    listConversations: async () => ({ records: [], total: 0 }),
    getConversation: async id => ({ id, courseId: '2007', title: '历史' }),
    getMessages: async () => [],
    renameConversation: async () => {},
    deleteConversation: async () => {},
    ...overrides
  }
  const startStream = (body, callbacks) => {
    let resolve
    const promise = new Promise(done => { resolve = done })
    const stream = { body, callbacks, resolve, promise, abort() { resolve({ kind: 'aborted' }) } }
    streams.push(stream)
    return stream
  }
  const mocks = {
    '../../../api/ai-stream': { startStream },
    '../../../api/conversations': conversations,
    '../../../config': { getBaseUrl: overrides.getBaseUrl || (() => 'http://127.0.0.1:19001') },
    '../../../utils/auth': { getToken: () => 'test', removeToken() {} }
  }
  const storage = new Map()
  let modal
  const wx = { request() {}, showToast() {}, showModal(options) { modal = options }, setNavigationBarTitle() {}, switchTab() {}, navigateTo() {},
    setStorageSync(key, value) { storage.set(key, structuredClone(value)) },
    getStorageSync(key) { return storage.get(key) },
    removeStorageSync(key) { storage.delete(key) }
  }
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../miniprogram/pages/ai/chat/index.js'), 'utf8'), {
    require(name) { if (!mocks[name]) throw Error(`Unexpected import: ${name}`); return mocks[name] },
    Page(options) { definition = options }, getApp: () => app, wx, setTimeout, clearTimeout, Date, console
  })
  const page = { data: structuredClone(definition.data), setData(patch) { Object.assign(this.data, structuredClone(patch)) } }
  for (const [key, method] of Object.entries(definition)) if (typeof method === 'function') page[key] = method.bind(page)
  page.onLoad({ courseId: '2007', title: '并发' })
  return { page, streams, app, storage, confirmModal: () => modal.success({ confirm: true }) }
}

function tick() { return new Promise(done => setTimeout(done, 0)) }

test('首问锁定会话范围，delta填入已有气泡，done展示推荐，追问发送同一会话ID', async () => {
  const { page, streams } = setup()
  page.data.input = '线程池'
  const sending = page.onSend()
  streams[0].callbacks.onMeta({ conversationId: '9001', messageId: '9002', title: '线程池' })
  streams[0].callbacks.onDelta('完整中文')
  streams[0].resolve({ kind: 'done', data: { messageId: '9002', sources: [], recommendations: [{ courseId: '2007', title: '并发' }] } })
  await sending
  assert.equal(page.data.messages[1].content, '完整中文')
  assert.equal(page.data.messages[1].status, 'done')
  assert.equal(page.data.messages[1].recommendations.length, 1)
  assert.equal(page.data.conversationId, '9001')
  page.data.input = '追问'
  const follow = page.onSend()
  assert.equal(streams[1].body.conversationId, '9001')
  assert.equal(streams[1].body.courseId, undefined)
  streams[1].resolve({ kind: 'done', data: {} })
  await follow
})

test('停止保留部分文字且没有推荐，离开页面后迟到meta不改当前会话', async () => {
  const { page, streams } = setup()
  page.data.input = '问题'
  const sending = page.onSend()
  streams[0].callbacks.onDelta('半句')
  page.onStop()
  await sending
  assert.equal(page.data.messages[1].content, '半句')
  assert.equal(page.data.messages[1].status, 'stopped')
  assert.equal(page.data.messages[1].recommendations.length, 0)
  page.data.input = '新问题'
  const second = page.onSend()
  page.onHide()
  streams[1].callbacks.onMeta({ conversationId: '错误旧会话', messageId: '3' })
  await second
  assert.equal(page.data.conversationId, null)
})

test('409撤回未保存问题并恢复输入，没有自动重发', async () => {
  const { page, streams } = setup()
  page.data.input = '被拒问题'
  const sending = page.onSend()
  streams[0].resolve({ kind: 'rejected', code: 409, msg: '上一个回答还在生成' })
  await sending
  assert.equal(page.data.messages.length, 0)
  assert.equal(page.data.input, '被拒问题')
  assert.equal(page.data.loading, false)
  assert.equal(streams.length, 1)
  page.onUnload()
  await tick()
})

test('停止打开历史后旧消息响应不能覆盖新对话', async () => {
  let resolve
  const { page } = setup({ getMessages: () => new Promise(done => { resolve = done }) })
  const opening = page.onOpenConversation({ currentTarget: { dataset: { id: 'old' } } })
  page.onStop()
  resolve([{ id: '1', role: 'user', content: '旧历史' }])
  await opening
  assert.equal(page.data.conversationId, null)
  assert.equal(page.data.messages.length, 0)
})

test('页面隐藏时历史列表请求结束，加载标记不会永久锁住', async () => {
  let resolve
  const { page } = setup({ listConversations: () => new Promise(done => { resolve = done }) })
  const loading = page.loadHistory()
  page.onHide()
  resolve({ records: [], total: 0 })
  await loading
  assert.equal(page.data.historyLoading, false)
})

test('401登录跳转前保存最小草稿，同一账号新页面恢复但不自动发送', async () => {
  const { page, streams, app, storage } = setup()
  page.data.input = '不能丢的提问'
  const sending = page.onSend()
  streams[0].resolve({ kind: 'rejected', code: 401, msg: '登录过期' })
  await sending
  assert.equal(storage.get('zhixue_ai_draft').question, '不能丢的提问')
  app.globalData = { isLogin: true, userInfo: { userId: '1003' } }
  page.onLoad({})
  page.onShow()
  assert.equal(page.data.input, '不能丢的提问')
  assert.equal(page.data.courseId, '2007')
  assert.equal(streams.length, 1)
})

test('配置读取异常结束loading，不遗留无法停止的占位回答', async () => {
  const { page } = setup({ getBaseUrl() { throw new Error('配置未填写') } })
  page.data.input = '问题'
  await page.onSend()
  assert.equal(page.data.loading, false)
  assert.equal(page.data.messages[1].status, 'failed')
})

test('另一个账号不能恢复原账号的401草稿', () => {
  const { page, app, storage } = setup()
  storage.set('zhixue_ai_draft', { owner: 'other-user', question: '私有草稿', at: Date.now() })
  page.onShow()
  assert.equal(page.data.input, '')
  assert.equal(storage.has('zhixue_ai_draft'), false)
})

test('身份还未加载时不消费草稿，身份就绪后再恢复', () => {
  const { page, app, storage } = setup()
  app.globalData.userInfo = null
  storage.set('zhixue_ai_draft', { owner: '1003', question: '等待身份的草稿', at: Date.now() })
  page.restoreDraft()
  assert.equal(storage.has('zhixue_ai_draft'), true)
  app.globalData.userInfo = { userId: '1003' }
  page.restoreDraft()
  assert.equal(page.data.input, '等待身份的草稿')
})

test('发送前固定账号ID，401到达前userInfo被清理也不丢归属', async () => {
  const { page, streams, app, storage } = setup()
  page.data.input = '账号草稿'
  const sending = page.onSend()
  app.globalData.userInfo = null
  streams[0].resolve({ kind: 'rejected', code: 401, msg: '登录过期' })
  await sending
  assert.equal(storage.get('zhixue_ai_draft').owner, '1003')
})

test('删除A回包不能取消期间新打开的B', async () => {
  let deleted
  let messages
  const { page, confirmModal } = setup({
    deleteConversation: () => new Promise(done => { deleted = done }),
    getMessages: () => new Promise(done => { messages = done })
  })
  page.data.conversationId = 'A'
  page.onDeleteConversation({ currentTarget: { dataset: { id: 'A' } } })
  const deleting = confirmModal()
  const opening = page.onOpenConversation({ currentTarget: { dataset: { id: 'B' } } })
  deleted()
  await deleting
  messages([])
  await opening
  assert.equal(page.data.conversationId, 'B')
})

test('生成完成但保存失败时保留回答并显示未保存提示', async () => {
  const { page, streams } = setup()
  page.data.input = '问题'
  const sending = page.onSend()
  streams[0].callbacks.onDelta('完整回答')
  streams[0].resolve({ kind: 'done', data: { saved: false } })
  await sending
  assert.equal(page.data.messages[1].content, '完整回答')
  assert.equal(page.data.messages[1].saved, false)
  assert.match(page.data.messages[1].saveNotice, /未保存/)
})

for (const [name, data, saved, notice] of [
  ['保存未知原样展示服务端提示', { saved: null, saveNotice: '保存状态尚未确认' }, null, '保存状态尚未确认'],
  ['保存未知缺少提示时有兜底', { saved: null }, null, '回答已生成，保存状态尚未确认。请稍后刷新历史核对或先复制保留。'],
  ['保存成功不显示过期警告', { saved: true, saveNotice: '过期警告' }, true, ''],
  ['旧服务端缺少保存字段时不误报', {}, null, '']
]) {
  test(name, async () => {
    const { page, streams } = setup()
    page.data.input = '问题'
    const sending = page.onSend()
    streams[0].resolve({ kind: 'done', data })
    await sending
    assert.equal(page.data.messages[1].saved, saved)
    assert.equal(page.data.messages[1].saveNotice, notice)
  })
}

test('回答气泡有保存提示展示绑定', () => {
  const wxml = fs.readFileSync(path.resolve(__dirname, '../miniprogram/pages/ai/chat/index.wxml'), 'utf8')
  assert.match(wxml, /class="save-warning"[^>]*wx:if="\{\{item\.saveNotice\}\}"[^>]*>\{\{item\.saveNotice\}\}/)
})
