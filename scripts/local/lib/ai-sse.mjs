// 本地脚本共用：读取 AI 对话的 SSE 流。
// Spring 输出形如 "event:delta\ndata:{...}\n\n"（冒号后没有空格），带空格的写法也兼容。

/** 逐个产出 { event, data }；data 能按 JSON 解析时为对象，否则为原文 */
export async function* readSse(response) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let match
    while ((match = /\r?\n\r?\n/.exec(buffer))) {
      const block = buffer.slice(0, match.index)
      buffer = buffer.slice(match.index + match[0].length)
      const event = parseBlock(block)
      if (event) yield event
    }
  }
  const tail = parseBlock(buffer + decoder.decode())
  if (tail) yield tail
}

function parseBlock(block) {
  let event = 'message'
  const data = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  if (data.length === 0) return null
  const raw = data.join('\n')
  try {
    return { event, data: JSON.parse(raw) }
  } catch {
    return { event, data: raw }
  }
}

/**
 * 提问并读完整个流。每个事件带 at（距发出请求的毫秒数），用于检查首字与推荐的时延。
 * 流前错误（响应不是 text/event-stream）时 events 为空、error 为响应 JSON（以 code 为准）。
 */
export async function askStream(gateway, token, body, timeoutMs = 150000) {
  const startedAt = Date.now()
  const response = await fetch(`${gateway}/ai/conversations/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs)
  })
  const type = response.headers.get('content-type') || ''
  if (!type.includes('text/event-stream')) {
    const error = await response.json().catch(() => ({ code: response.status }))
    return { events: [], headers: response.headers, error }
  }
  const events = []
  for await (const event of readSse(response)) events.push({ ...event, at: Date.now() - startedAt })
  return { events, headers: response.headers }
}

/** 学生端本地联调：微信沙箱登录（映射到 student 账号），不需要验证码 */
export async function studentToken(gateway, code = `script-${Date.now()}`) {
  const response = await fetch(`${gateway}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ loginType: 'wechat', wechatCode: code })
  })
  const json = await response.json()
  if (!json.data?.token) {
    throw new Error(`学生登录失败：${json.msg}（local.env 需设置 ZHIXUE_AUTH_WECHAT_MODE=sandbox）`)
  }
  return json.data.token
}
