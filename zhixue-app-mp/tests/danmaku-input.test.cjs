const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

function setup(sendResult = true, connected = true) {
  let definition
  const sent = []
  const notices = []
  const root = path.resolve(__dirname, '../miniprogram/pages/course/player')
  vm.runInNewContext(fs.readFileSync(path.join(root, 'index.js'), 'utf8'), {
    Page(options) { definition = options },
    getApp: () => ({ globalData: {} }),
    require: () => ({}),
    wx: { showToast(options) { notices.push(options.title) } },
    console, setTimeout
  })
  const page = {
    data: { ...structuredClone(definition.data), currentTime: 12.5 },
    wsManager: connected ? { sendDanmaku(text, time) { sent.push({ text, time }); return sendResult } } : null,
    setData(patch) { Object.assign(this.data, structuredClone(patch)) }
  }
  for (const [key, value] of Object.entries(definition)) {
    if (typeof value === 'function') page[key] = value.bind(page)
  }
  const template = fs.readFileSync(path.join(root, 'index.wxml'), 'utf8')
  const input = template.match(/<input\s[\s\S]*?class="danmaku-input"[\s\S]*?\/>/)[0]
  const confirmHandler = input.match(/bindconfirm="(\w+)"/)[1]
  return { page, sent, notices, input, confirm: value => page[confirmHandler]({ detail: { value } }) }
}

test('回车读取input confirm的value发送，传入视频时间且不本地重复回显', () => {
  const { page, sent, notices, confirm } = setup()
  confirm('  回车弹幕  ')
  assert.deepEqual(sent, [{ text: '回车弹幕', time: 12.5 }])
  assert.deepEqual(notices, [])
  assert.equal(page.data.danmakuList.length, 0)
})

test('输入与页面草稿绑定，发送成功后清空输入框', () => {
  const { page, input, confirm } = setup()
  const change = input.match(/bindinput="(\w+)"/)
  assert.ok(change, 'input必须绑定输入事件')
  assert.match(input, /value="\{\{danmakuDraft\}\}"/)
  page[change[1]]({ detail: { value: '待发送内容' } })
  assert.equal(page.data.danmakuDraft, '待发送内容')
  confirm('待发送内容')
  assert.equal(page.data.danmakuDraft, '')
})

test('空白内容不发送并提示请输入内容', () => {
  const { sent, notices, confirm } = setup()
  confirm('  \n  ')
  assert.equal(sent.length, 0)
  assert.deepEqual(notices, ['请输入弹幕内容'])
})

test('连接未建立保留输入并提示，不误报空内容', () => {
  const { page, notices, sent, confirm } = setup(true, false)
  page.data.danmakuDraft = '不能丢的内容'
  confirm('不能丢的内容')
  assert.equal(sent.length, 0)
  assert.equal(page.data.danmakuDraft, '不能丢的内容')
  assert.deepEqual(notices, ['连接未建立'])
})

test('发送接口拒绝时保留输入，不能当作发送成功', () => {
  const { page, notices, confirm } = setup(false)
  page.data.danmakuDraft = '断线时的内容'
  confirm('断线时的内容')
  assert.equal(page.data.danmakuDraft, '断线时的内容')
  assert.deepEqual(notices, ['发送失败，连接已断开'])
})
