import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/conversations', () => ({
  listConversations: vi.fn(),
  renameConversation: vi.fn().mockResolvedValue(null),
  deleteConversation: vi.fn().mockResolvedValue(null)
}))

const api = await import('@/api/conversations')
const { PAGE_SIZE, useConversationsStore } = await import('@/stores/conversations')

// 种子数据里的小 ID 是数字，雪花 ID 由后端转成字符串返回（超出 JS 安全整数）
const PAGE_1 = {
  records: [
    { id: '9000000000000000002', title: '线程池参数怎么设', courseId: 2007, lastMessageTime: '2026-10-20T21:00:00' },
    { id: 9001, title: 'HashMap 为什么线程不安全', courseId: null, lastMessageTime: '2026-10-20T20:00:00' }
  ],
  total: 3
}
const PAGE_2 = {
  records: [
    { id: 9001, title: 'HashMap 为什么线程不安全', courseId: null, lastMessageTime: '2026-10-20T20:00:00' },
    { id: 9000, title: 'String 为什么不可变', courseId: null, lastMessageTime: '2026-10-19T20:00:00' }
  ],
  total: 3
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('useConversationsStore', () => {
  it('加载第一页：ID 一律转成字符串；并发调用只请求一次', async () => {
    api.listConversations.mockResolvedValue(PAGE_1)
    const store = useConversationsStore()
    await Promise.all([store.load(), store.ensureLoaded()])
    expect(api.listConversations).toHaveBeenCalledOnce()
    expect(api.listConversations).toHaveBeenCalledWith(1, PAGE_SIZE)
    expect(store.items.map((c) => [c.id, c.courseId])).toEqual([['9000000000000000002', '2007'], ['9001', null]])
    expect(store.hasMore).toBe(true)
  })

  it('加载更多：追加下一页并去掉重复', async () => {
    api.listConversations.mockResolvedValueOnce(PAGE_1).mockResolvedValueOnce(PAGE_2)
    const store = useConversationsStore()
    await store.load()
    await store.loadMore()
    expect(api.listConversations).toHaveBeenLastCalledWith(2, PAGE_SIZE)
    expect(store.items.map((c) => c.id)).toEqual(['9000000000000000002', '9001', '9000'])
    expect(store.hasMore).toBe(false)
  })

  it('新建会话放到最前；已有会话有新消息时移到最前', async () => {
    api.listConversations.mockResolvedValue(PAGE_1)
    const store = useConversationsStore()
    await store.load()
    store.upsert({ id: '9000000000000000009', title: 'volatile 和 synchronized 的区别', courseId: null })
    expect(store.items[0]).toMatchObject({ id: '9000000000000000009', title: 'volatile 和 synchronized 的区别' })
    expect(store.total).toBe(4)
    store.bump('9001')
    expect(store.items.map((c) => c.id)).toEqual(['9001', '9000000000000000009', '9000000000000000002'])
    store.bump('不存在的会话')
    expect(store.items).toHaveLength(3)
  })

  it('重命名去掉首尾空白；删除后从列表移除', async () => {
    api.listConversations.mockResolvedValue(PAGE_1)
    const store = useConversationsStore()
    await store.load()
    await store.rename('9001', '  并发笔记  ')
    expect(api.renameConversation).toHaveBeenCalledWith('9001', '并发笔记')
    expect(store.find('9001').title).toBe('并发笔记')
    await store.remove('9001')
    expect(api.deleteConversation).toHaveBeenCalledWith('9001')
    expect(store.items.map((c) => c.id)).toEqual(['9000000000000000002'])
    expect(store.total).toBe(2)
  })

  it('加载失败记录原因，可以重试', async () => {
    api.listConversations.mockRejectedValueOnce(new Error('网络连接失败，请检查网络')).mockResolvedValueOnce(PAGE_1)
    const store = useConversationsStore()
    await expect(store.load()).rejects.toThrow('网络连接失败')
    expect(store.error).toBe('网络连接失败，请检查网络')
    await store.load()
    expect(store.error).toBe('')
    expect(store.items).toHaveLength(2)
  })
})
