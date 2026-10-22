const test = require('node:test')
const assert = require('node:assert/strict')

function fakeRequest() {
  let config
  let chunk
  let headers
  let aborted = false
  return {
    request(options) {
      config = options
      return {
        onChunkReceived(fn) { chunk = fn },
        onHeadersReceived(fn) { headers = fn },
        abort() { aborted = true; options.fail({ errMsg: 'request:fail abort' }) }
      }
    },
    head(type = 'text/event-stream', statusCode = 200) { headers({ header: { 'Content-Type': type }, statusCode }) },
    send(text) { const data = Buffer.from(text); chunk({ data: data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength) }) },
    end(data) { config.success({ statusCode: 200, data }) },
    get aborted() { return aborted }
  }
}

function start(fake, options = {}) {
  return require('../miniprogram/api/ai-stream').startStream({ question: '问题' }, {
    request: fake.request,
    baseUrl: 'http://127.0.0.1:19001',
    token: 'test-token',
    ...options
  })
}

test('中文、emoji跨字节且事件任意拆块，不依赖TextDecoder', () => {
  const events = []
  const codec = require('../miniprogram/utils/sse-codec').createSseCodec(event => events.push(event))
  const bytes = Buffer.from('event:delta\r\ndata:{"text":"中文🙂"}\r\n\r\nevent:done\ndata:{}\n\n')
  for (const byte of bytes) codec.push(Uint8Array.of(byte).buffer)
  codec.finish()
  assert.equal(events[0].data.text, '中文🙂')
  assert.equal(events[1].event, 'done')
})

test('meta/delta/done正常结束，done后不再回调', async () => {
  const fake = fakeRequest()
  const pieces = []
  const stream = start(fake, { onDelta: text => pieces.push(text) })
  fake.head()
  fake.send('event:meta\ndata:{"conversationId":"1","messageId":"2"}\n\nevent:delta\ndata:{"text":"内容"}\n\nevent:done\ndata:{"messageId":"2"}\n\n')
  fake.send('event:delta\ndata:{"text":"迟到"}\n\n')
  assert.equal((await stream.promise).kind, 'done')
  assert.deepEqual(pieces, ['内容'])
})

test('HTTP200 JSON409不能当流式回答，返回真实拒绝码', async () => {
  const fake = fakeRequest()
  const stream = start(fake)
  fake.head('application/json')
  fake.send('{"code":409,"msg":"上一个回答还在生成"}')
  fake.end()
  assert.deepEqual(await stream.promise, { kind: 'rejected', code: 409, msg: '上一个回答还在生成' })
})

test('HTTP401优先于空响应体', async () => {
  const fake = fakeRequest()
  const stream = start(fake)
  fake.head('application/json', 401)
  fake.end()
  assert.equal((await stream.promise).code, 401)
})

test('停止会调用abort，且迟到分块不污染页面', async () => {
  const fake = fakeRequest()
  const onDelta = () => assert.fail('停止后不可回调')
  const stream = start(fake, { onDelta })
  fake.head()
  stream.abort()
  fake.send('event:delta\ndata:{"text":"迟到"}\n\n')
  assert.equal((await stream.promise).kind, 'aborted')
  assert.equal(fake.aborted, true)
})

test('流未收到终态便结束，视为连接中断', async () => {
  const fake = fakeRequest()
  const stream = start(fake)
  fake.head()
  fake.send('event:delta\ndata:{"text":"半句"}\n\n')
  fake.end()
  assert.equal((await stream.promise).kind, 'interrupted')
})

test('畸形done不能当成功，协议失败应关闭底层请求', async () => {
  const fake = fakeRequest()
  const stream = start(fake)
  fake.head()
  fake.send('event:done\ndata:{"recommendations":{}}\n\n')
  assert.equal((await stream.promise).kind, 'interrupted')
  assert.equal(fake.aborted, true)
})

test('结果结算后仍可幂等关闭底层传输，不改变done结果', async () => {
  const fake = fakeRequest()
  const stream = start(fake)
  fake.head()
  fake.send('event:done\ndata:{}\n\n')
  stream.abort()
  assert.equal((await stream.promise).kind, 'done')
  assert.equal(fake.aborted, true)
})

test('done收到时自动关闭请求，不要求页面保留句柄', async () => {
  const fake = fakeRequest()
  const stream = start(fake)
  fake.head()
  fake.send('event:done\ndata:{}\n\n')
  assert.equal((await stream.promise).kind, 'done')
  assert.equal(fake.aborted, true)
})

test('页面delta回调抛错也能结算并关闭连接', async () => {
  const fake = fakeRequest()
  const stream = start(fake, { onDelta() { throw new Error('页面已失效') } })
  fake.head()
  assert.doesNotThrow(() => fake.send('event:delta\ndata:{"text":"字"}\n\n'))
  assert.equal((await stream.promise).kind, 'interrupted')
  assert.equal(fake.aborted, true)
})

test('success中解析完整响应触发回调异常，也结算中断并关闭请求', async () => {
  const fake = fakeRequest()
  const stream = start(fake, { onDelta() { throw new Error('页面已失效') } })
  fake.head()
  const bytes = Buffer.from('event:delta\ndata:{"text":"字"}\n\n')
  const buffer = bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength)
  assert.doesNotThrow(() => fake.end(buffer))
  assert.equal((await stream.promise).kind, 'interrupted')
  assert.equal(fake.aborted, true)
})

test('success中刷新尾事件触发回调异常，也结算中断并关闭请求', async () => {
  const fake = fakeRequest()
  const stream = start(fake, { onDelta() { throw new Error('页面已失效') } })
  fake.head()
  fake.send('event:delta\ndata:{"text":"字"}')
  assert.doesNotThrow(() => fake.end())
  assert.equal((await stream.promise).kind, 'interrupted')
  assert.equal(fake.aborted, true)
})

test('HTTP401即使响应类型为SSE也不能调用业务回调或返回done', async () => {
  const fake = fakeRequest()
  const stream = start(fake, { onDelta() { assert.fail('鉴权失败不得交付内容') } })
  fake.head('text/event-stream', 401)
  fake.send('event:delta\ndata:{"text":"不可信"}\n\nevent:done\ndata:{}\n\n')
  fake.end()
  assert.equal((await stream.promise).kind, 'rejected')
  assert.equal((await stream.promise).code, 401)
})

test('未结束事件超过128KiB时立即中断并关闭连接', async () => {
  const fake = fakeRequest()
  const stream = start(fake)
  fake.head()
  fake.send('x'.repeat(128 * 1024 + 1))
  assert.equal(fake.aborted, true)
  assert.equal((await stream.promise).kind, 'interrupted')
})

test('已消费事件不永久保存全文，总响应超过1MiB也会中断', async () => {
  const codec = require('../miniprogram/utils/sse-codec').createSseCodec(() => {})
  const bytes = Buffer.from('event:delta\ndata:{"text":"内容"}\n\n')
  codec.push(bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength))
  assert.equal(codec.finish(), '')
  const fake = fakeRequest()
  const stream = start(fake)
  fake.head()
  const frame = 'event:delta\ndata:{"text":"' + 'x'.repeat(16000) + '"}\n\n'
  for (let i = 0; i < 70; i++) fake.send(frame)
  assert.equal(fake.aborted, true)
  assert.equal((await stream.promise).kind, 'interrupted')
})

test('部分回答后504返回error，不丢错误文案', async () => {
  const fake = fakeRequest()
  const stream = start(fake)
  fake.head()
  fake.send('event:error\ndata:{"code":504,"msg":"生成超时，已中断"}\n\n')
  assert.deepEqual(await stream.promise, { kind: 'error', code: 504, msg: '生成超时，已中断' })
})
