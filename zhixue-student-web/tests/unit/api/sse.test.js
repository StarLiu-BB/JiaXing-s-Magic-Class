import { describe, expect, it } from 'vitest'
import { readSse } from '@/api/sse'

const encoder = new TextEncoder()

function streamOf(chunks) {
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(typeof chunk === 'string' ? encoder.encode(chunk) : chunk))
      controller.close()
    }
  })
}

async function collect(stream) {
  const events = []
  for await (const event of readSse(stream)) {
    events.push(event)
  }
  return events
}

describe('readSse', () => {
  it('Spring 的写法（冒号后无空格），事件被拆在多个网络块里', async () => {
    const events = await collect(streamOf([
      'event:me',
      'ta\ndata:{"conversationId":"21123665239507148',
      '82"}\n',
      '\nevent:delta\ndata:{"text":"Hash"}\n\n'
    ]))
    expect(events).toEqual([
      { event: 'meta', data: '{"conversationId":"2112366523950714882"}' },
      { event: 'delta', data: '{"text":"Hash"}' }
    ])
  })

  it('标准写法、CRLF 换行、注释行与多行 data', async () => {
    const events = await collect(streamOf(['event: delta\r\n', ': keepalive\r\n', 'data: 第一行\r\ndata: 第二行\r\n\r\n']))
    expect(events).toEqual([{ event: 'delta', data: '第一行\n第二行' }])
  })

  it('CRLF 的空行被拆在两个块之间', async () => {
    const events = await collect(streamOf(['event:done\r\ndata:{}\r\n\r', '\nevent:x\r\ndata:1\r\n\r\n']))
    expect(events).toEqual([{ event: 'done', data: '{}' }, { event: 'x', data: '1' }])
  })

  it('多字节字符被拆在两个块里', async () => {
    const bytes = encoder.encode('event:delta\ndata:{"text":"线程安全"}\n\n')
    const cut = bytes.indexOf(0xe7) + 1 // 切在「线」字的第一个字节之后
    const events = await collect(streamOf([bytes.slice(0, cut), bytes.slice(cut)]))
    expect(events).toEqual([{ event: 'delta', data: '{"text":"线程安全"}' }])
  })

  it('没有 event 行的块按 message 处理；没有 data 的块被忽略', async () => {
    const events = await collect(streamOf(['event:ping\n\n', 'data:hello\n\n']))
    expect(events).toEqual([{ event: 'message', data: 'hello' }])
  })

  it('流结束时没有空行收尾的最后一个事件也会产出', async () => {
    expect(await collect(streamOf(['event:done\ndata:{}']))).toEqual([{ event: 'done', data: '{}' }])
  })
})
