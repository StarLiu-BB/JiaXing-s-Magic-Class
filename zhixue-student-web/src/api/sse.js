/**
 * 把 SSE 字节流解析成事件。
 *
 * 兼容 Spring 输出的 `event:x` / `data:y`（冒号后没有空格）与标准写法；兼容 \r\n；
 * 事件或多字节字符被拆在多个网络块里；多行 data（按 \n 拼接）；注释行（以冒号开头）。
 * 流结束时没有空行收尾的最后一个事件也会产出。
 *
 * @param {ReadableStream<Uint8Array>} stream
 * @returns {AsyncGenerator<{ event: string, data: string }>}
 */
export async function* readSse(stream) {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      let boundary = findBoundary(buffer)
      while (boundary) {
        const event = parseBlock(buffer.slice(0, boundary.index))
        buffer = buffer.slice(boundary.index + boundary.length)
        if (event) {
          yield event
        }
        boundary = findBoundary(buffer)
      }
    }
    const tail = parseBlock(buffer + decoder.decode())
    if (tail) {
      yield tail
    }
  } finally {
    // 调用方提前结束（如收到 done 后 return）时释放连接
    reader.cancel().catch(() => {})
  }
}

function findBoundary(text) {
  const match = /\r\n\r\n|\n\n|\r\r/.exec(text)
  return match ? { index: match.index, length: match[0].length } : null
}

/** 解析一个事件块；没有 data 行的块（如心跳注释）返回 null */
export function parseBlock(block) {
  let event = 'message'
  const data = []
  for (const line of block.split(/\r\n|\n|\r/)) {
    if (!line || line.startsWith(':')) {
      continue
    }
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }
    if (field === 'event') {
      event = value
    } else if (field === 'data') {
      data.push(value)
    }
  }
  return data.length ? { event, data: data.join('\n') } : null
}
