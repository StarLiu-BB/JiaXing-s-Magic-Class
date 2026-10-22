const { createSseCodec } = require('../utils/sse-codec')

function startStream(body, { request, baseUrl, token, onMeta, onDelta }) {
  let settled = false
  let status = 200
  let eventStream = false
  let receivedChunk = false
  let task
  let closed = false
  function close() {
    if (!closed && task) { closed = true; task.abort() }
  }
  function invalid() { finish({ kind: 'interrupted' }); close() }
  let resolve
  const promise = new Promise(done => { resolve = done })
  function finish(result) {
    if (!settled) {
      settled = true
      resolve(result)
      close()
    }
  }
  const codec = createSseCodec(({ event, data }) => {
    if (settled || !eventStream) return
    if (event === 'meta') {
      if (!data?.conversationId || !data?.messageId) { invalid(); return }
      onMeta?.(data)
    } else if (event === 'delta') {
      if (typeof data?.text === 'string') onDelta?.(data.text)
    } else if (event === 'done') {
      const valid = data && typeof data === 'object' && !Array.isArray(data)
        && (data.sources == null || Array.isArray(data.sources))
        && (data.recommendations == null || Array.isArray(data.recommendations))
      if (!valid) { invalid(); return }
      finish({ kind: 'done', data })
    } else if (event === 'error') {
      finish({ kind: 'error', code: data?.code || 503, msg: data?.msg || 'AI 服务暂不可用，请稍后再试' })
    }
  }, { captureRaw: () => !eventStream })
  try {
    task = request({
      url: `${baseUrl.replace(/\/$/, '')}/ai/conversations/messages`,
      method: 'POST',
      data: body,
      header: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      enableChunked: true,
      responseType: 'arraybuffer',
      timeout: 150000,
      success(response) {
        if (settled) return
        let raw
        try {
          if (!receivedChunk && response.data instanceof ArrayBuffer) codec.push(response.data)
          raw = codec.finish()
        } catch {
          invalid()
          return
        }
        if (settled) return
        if (eventStream) { finish({ kind: 'interrupted' }); return }
        let data
        try { data = typeof response.data === 'object' && !(response.data instanceof ArrayBuffer) ? response.data : JSON.parse(raw || response.data) } catch { data = null }
        const code = status === 401 || response.statusCode === 401 ? 401 : data?.code || (status >= 400 ? status : 500)
        finish({ kind: 'rejected', code, msg: data?.msg || (code === 401 ? '登录已过期，请重新登录' : '发送失败，请重试') })
      },
      fail() { finish({ kind: 'interrupted' }) }
    })
    if (!task.onChunkReceived || !task.onHeadersReceived) {
      finish({ kind: 'error', code: 0, msg: '当前微信版本不支持流式答疑，请升级微信' })
      task.abort()
    } else {
      task.onHeadersReceived(response => {
        status = response.statusCode || 200
        const key = Object.keys(response.header || {}).find(key => key.toLowerCase() === 'content-type')
        eventStream = status >= 200 && status < 300 && String(response.header?.[key] || '').includes('text/event-stream')
      })
      task.onChunkReceived(({ data }) => {
        if (settled) return
        receivedChunk = true
        try { codec.push(data) } catch { invalid() }
      })
    }
  } catch { finish({ kind: 'interrupted' }) }
  return {
    promise,
    abort() {
      if (!settled) finish({ kind: 'aborted' })
      close()
    }
  }
}

module.exports = { startStream }
