import { readSse } from './sse'

const BASE = import.meta.env.VITE_APP_BASE_API || ''
// 写成 url 字面量，scripts/local/contract-check.sh 才能核对网关是否有这条路由
const ASK = { url: '/ai/conversations/messages', method: 'POST' }
const MODEL_UNAVAILABLE = 'AI 服务暂不可用，请稍后再试'

/**
 * 提问并读取流式回答（POST /ai/conversations/messages）。
 *
 * 不用浏览器自带的 EventSource：它只能发 GET、带不了 Authorization 头，而问题可能长达 500 字。
 * 永远 resolve、不 reject，用 kind 区分结束方式：
 * - done：正常结束；data 为 done 事件内容（messageId、sources、notice、recommendations）
 * - error：流开始后失败（error 事件）；code 为 503 / 504，msg 可直接展示
 * - rejected：流开始前被拒绝。业务错误是 HTTP 200 + body.code（400 / 404 / 409 / 500），
 *   以 body.code 为准；网关鉴权失败是 HTTP 401
 * - aborted：调用方通过 signal 中止（停止按钮、切换会话、离开页面）
 * - interrupted：连接失败，或流在 done / error 之前就结束了
 *
 * @param {{ conversationId?: string, question: string, courseId?: string }} body
 * @param {{ onMeta?: (meta: object) => void, onDelta?: (text: string) => void, signal?: AbortSignal,
 *           token?: string, baseUrl?: string }} options
 */
export async function askStream(body, { onMeta, onDelta, signal, token, baseUrl = BASE } = {}) {
  let response
  try {
    response = await fetch(`${baseUrl}${ASK.url}`, {
      method: ASK.method,
      headers: {
        'Content-Type': 'application/json',
        // 必须同时接受 JSON：流开始前的错误按 JSON 返回，只写 text/event-stream 会被服务端判为不可接受
        Accept: 'text/event-stream, application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(body),
      signal
    })
  } catch {
    return ended(signal)
  }

  if (!(response.headers.get('content-type') || '').includes('text/event-stream')) {
    return rejected(response, signal)
  }

  try {
    for await (const { event, data } of readSse(response.body)) {
      const payload = parseJson(data)
      if (event === 'meta') {
        // 没有会话 / 消息 ID 就无法建立会话，继续读只会让界面停在一个没有 ID 的回答上
        if (!payload?.conversationId || !payload?.messageId) {
          break
        }
        onMeta?.(payload)
      } else if (event === 'delta') {
        onDelta?.(payload?.text ?? '')
      } else if (event === 'done') {
        // 内容被截断（例如连接在 done 写到一半时断开）时不能当成正常结束，否则资料与推荐会静默丢失
        return payload ? { kind: 'done', data: payload } : ended(signal)
      } else if (event === 'error') {
        return { kind: 'error', code: payload?.code ?? 503, msg: payload?.msg || MODEL_UNAVAILABLE }
      }
    }
  } catch {
    // 读取中途出错：中止或断线，下面统一判断
  }
  return ended(signal)
}

function ended(signal) {
  return signal?.aborted ? { kind: 'aborted' } : { kind: 'interrupted' }
}

async function rejected(response, signal) {
  const body = await response.json().catch(() => null)
  // 读取拒绝原因时被中止（切换会话、离开页面）：调用方已不关心这次结果，不能按拒绝处理
  if (signal?.aborted) {
    return { kind: 'aborted' }
  }
  if (response.status === 401 || body?.code === 401) {
    return { kind: 'rejected', code: 401, msg: body?.msg || '登录已失效，请重新登录' }
  }
  const code = body && body.code !== 200 ? body.code : response.status >= 400 ? response.status : 500
  return { kind: 'rejected', code, msg: body?.msg || `请求失败（${response.status}）` }
}

function parseJson(text) {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}
