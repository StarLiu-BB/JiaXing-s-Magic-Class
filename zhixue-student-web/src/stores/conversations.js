import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { deleteConversation, listConversations, renameConversation } from '@/api/conversations'

/** 后端单页上限为 50 */
export const PAGE_SIZE = 50

const normalize = (c) => ({
  id: String(c.id),
  title: c.title || '新对话',
  courseId: c.courseId == null ? null : String(c.courseId),
  lastMessageTime: c.lastMessageTime || null
})

/** 侧边栏「最近」：按最后消息时间倒序，ID 一律为字符串 */
export const useConversationsStore = defineStore('conversations', () => {
  const items = ref([])
  const total = ref(0)
  const page = ref(0)
  const loading = ref(false)
  const error = ref('')
  let pending = null

  const hasMore = computed(() => items.value.length < total.value)

  function fetchPage(n) {
    loading.value = true
    error.value = ''
    pending = listConversations(n, PAGE_SIZE)
      .then((data) => {
        const incoming = (data?.records || []).map(normalize)
        if (n === 1) {
          items.value = incoming
        } else {
          const known = new Set(items.value.map((c) => c.id))
          items.value = [...items.value, ...incoming.filter((c) => !known.has(c.id))]
        }
        total.value = Number(data?.total || 0)
        page.value = n
      })
      .catch((e) => {
        error.value = e?.message || '加载失败'
        throw e
      })
      .finally(() => {
        loading.value = false
        pending = null
      })
    return pending
  }

  /** 重新加载第一页 */
  const load = () => pending || fetchPage(1)
  const loadMore = () => pending || (hasMore.value ? fetchPage(page.value + 1) : Promise.resolve())
  /** 还没加载过才加载；正在加载则等它完成 */
  const ensureLoaded = () => pending || (page.value ? Promise.resolve() : fetchPage(1))
  const find = (id) => items.value.find((c) => c.id === String(id)) || null

  /** 新建会话（meta 事件）：放到最前 */
  function upsert(conversation) {
    const existing = find(conversation.id)
    const next = normalize({ ...existing, ...conversation, lastMessageTime: new Date().toISOString() })
    items.value = [next, ...items.value.filter((c) => c.id !== next.id)]
    if (!existing) {
      total.value += 1
    }
  }

  /** 已有会话有新消息：移到最前；不在列表里（超出已加载的页）则忽略 */
  function bump(id) {
    const existing = find(id)
    if (!existing) {
      return
    }
    items.value = [{ ...existing, lastMessageTime: new Date().toISOString() }, ...items.value.filter((c) => c.id !== existing.id)]
  }

  async function rename(id, title) {
    const trimmed = (title || '').trim()
    await renameConversation(String(id), trimmed)
    const item = find(id)
    if (item) {
      item.title = trimmed
    }
  }

  async function remove(id) {
    await deleteConversation(String(id))
    const before = items.value.length
    items.value = items.value.filter((c) => c.id !== String(id))
    if (items.value.length < before) {
      total.value = Math.max(0, total.value - 1)
    }
  }

  /** 退出登录时清空 */
  function reset() {
    items.value = []
    total.value = 0
    page.value = 0
    error.value = ''
    pending = null
  }

  return { items, total, loading, error, hasMore, load, loadMore, ensureLoaded, find, upsert, bump, rename, remove, reset }
})
