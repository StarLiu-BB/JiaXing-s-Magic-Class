import http from 'node:http'
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { askStream } from '@/api/chat'

const CONV = '2112366523950714882'
const MSG = '2112366524101709826'
const CARD = {
  courseId: '2007', title: 'Java 并发编程实战', coverUrl: '', price: 99, purchased: false, previewable: true,
  lessons: [{ lessonId: '4024', chapterId: '3017', title: 'ConcurrentHashMap 原理', duration: 1080, chapterIndex: 2, lessonIndex: 2, playable: false }]
}

/** 真实 HTTP 服务：覆盖 fetch + ReadableStream 的真实读取路径，而不是只测解析函数 */
let server
let baseUrl
let handler
let lastRequest

beforeAll(async () => {
  server = http.createServer((req, res) => {
    let raw = ''
    req.on('data', (chunk) => { raw += chunk })
    req.on('end', () => {
      lastRequest = { method: req.method, url: req.url, auth: req.headers.authorization, accept: req.headers.accept, body: raw ? JSON.parse(raw) : null }
      handler(req, res)
    })
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  baseUrl = `http://127.0.0.1:${server.address().port}`
})

afterAll(() => new Promise((resolve) => {
  // fetch 会复用长连接，不主动断开的话 close 要等到连接空闲超时
  server.closeAllConnections()
  server.close(resolve)
}))

/** 以 Spring 的格式（冒号后无空格）写一个事件 */
function sse(res) {
  res.writeHead(200, { 'Content-Type': 'text/event-stream;charset=UTF-8', 'Cache-Control': 'no-cache' })
  return (event, data) => res.write(`event:${event}\ndata:${JSON.stringify(data)}\n\n`)
}

function json(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(body))
}

const ask = (body, options = {}) => askStream(body, { baseUrl, token: 't-1', ...options })

describe('askStream', () => {
  it('依次回调 meta 与 delta，以 done 的内容结束；请求带令牌与正确的 Accept', async () => {
    handler = (req, res) => {
      const send = sse(res)
      send('meta', { conversationId: CONV, messageId: MSG, title: 'HashMap 为什么线程不安全' })
      send('delta', { text: 'Hash' })
      send('delta', { text: 'Map' })
      send('done', { messageId: MSG, sources: [{ title: 'Java 集合与泛型 · HashMap 深入' }], notice: null, recommendations: [CARD] })
      res.end()
    }
    const onMeta = vi.fn()
    const deltas = []

    const result = await ask({ question: 'HashMap 为什么线程不安全', courseId: '2007' }, { onMeta, onDelta: (t) => deltas.push(t) })

    expect(lastRequest).toEqual({
      method: 'POST', url: '/ai/conversations/messages', auth: 'Bearer t-1', accept: 'text/event-stream, application/json',
      body: { question: 'HashMap 为什么线程不安全', courseId: '2007' }
    })
    expect(onMeta).toHaveBeenCalledWith({ conversationId: CONV, messageId: MSG, title: 'HashMap 为什么线程不安全' })
    expect(deltas).toEqual(['Hash', 'Map'])
    expect(result).toEqual({ kind: 'done', data: { messageId: MSG, sources: [{ title: 'Java 集合与泛型 · HashMap 深入' }], notice: null, recommendations: [CARD] } })
  })

  it('流开始前的业务错误是 HTTP 200 + JSON，以 body.code 为准', async () => {
    handler = (req, res) => json(res, 200, { code: 409, msg: '上一个回答还在生成' })
    expect(await ask({ question: 'x' })).toEqual({ kind: 'rejected', code: 409, msg: '上一个回答还在生成' })
  })

  it('网关鉴权失败是 HTTP 401', async () => {
    handler = (req, res) => json(res, 401, { code: 401, msg: '令牌无效或已过期' })
    expect(await ask({ question: 'x' })).toEqual({ kind: 'rejected', code: 401, msg: '令牌无效或已过期' })
  })

  it('error 事件结束：返回错误码与说明，此前的文字照常回调', async () => {
    handler = (req, res) => {
      const send = sse(res)
      send('meta', { conversationId: CONV, messageId: MSG, title: 't' })
      send('delta', { text: '半句' })
      send('error', { code: 504, msg: '生成超时，已中断' })
      res.end()
    }
    const deltas = []
    expect(await ask({ question: 'x' }, { onDelta: (t) => deltas.push(t) })).toEqual({ kind: 'error', code: 504, msg: '生成超时，已中断' })
    expect(deltas).toEqual(['半句'])
  })

  it.each([
    ['服务端正常关闭但没有 done/error', (res) => res.end()],
    ['连接被重置', (res) => res.destroy()]
  ])('%s：视为连接中断', async (_, close) => {
    handler = (req, res) => {
      sse(res)('delta', { text: '半句' })
      setTimeout(() => close(res), 20)
    }
    expect(await ask({ question: 'x' })).toEqual({ kind: 'interrupted' })
  })

  it('调用方中止：返回 aborted，服务端感知到连接关闭', async () => {
    let serverSawClose = false
    handler = (req, res) => {
      res.on('close', () => { serverSawClose = true })
      sse(res)('meta', { conversationId: CONV, messageId: MSG, title: 't' })
    }
    const controller = new AbortController()

    const result = await ask({ question: 'x' }, { signal: controller.signal, onMeta: () => controller.abort() })

    expect(result).toEqual({ kind: 'aborted' })
    await vi.waitFor(() => expect(serverSawClose).toBe(true))
  })

  it('done 内容无法解析：不能当成正常结束，视为连接中断', async () => {
    handler = (req, res) => {
      res.writeHead(200, { 'Content-Type': 'text/event-stream;charset=UTF-8' })
      res.write('event:meta\ndata:{"conversationId":"1","messageId":"2","title":"t"}\n\n')
      res.write('event:done\ndata:{"messageId":"2","sour')
      res.end()
    }
    expect(await ask({ question: 'x' })).toEqual({ kind: 'interrupted' })
  })

  it('meta 缺少会话或消息 ID：不回调 meta，视为连接中断', async () => {
    handler = (req, res) => {
      res.writeHead(200, { 'Content-Type': 'text/event-stream;charset=UTF-8' })
      res.write('event:meta\ndata:{"title":"t"}\n\n')
      res.write('event:delta\ndata:{"text":"半句"}\n\n')
    }
    const onMeta = vi.fn()
    expect(await ask({ question: 'x' }, { onMeta })).toEqual({ kind: 'interrupted' })
    expect(onMeta).not.toHaveBeenCalled()
  })

  it('读取拒绝原因时被中止：返回 aborted，而不是拒绝', async () => {
    const controller = new AbortController()
    handler = (req, res) => {
      res.writeHead(200, { 'Content-Type': 'application/json' })
      res.write('{"code":409,')
      setTimeout(() => controller.abort(), 20)
    }
    expect(await ask({ question: 'x' }, { signal: controller.signal })).toEqual({ kind: 'aborted' })
  })

  it('连不上服务：视为连接中断', async () => {
    expect(await askStream({ question: 'x' }, { baseUrl: 'http://127.0.0.1:1', token: 't' })).toEqual({ kind: 'interrupted' })
  })
})
