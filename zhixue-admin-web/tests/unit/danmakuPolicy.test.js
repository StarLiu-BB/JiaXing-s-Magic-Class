import { beforeEach, expect, it, vi } from 'vitest'
import Comment from '@/views/interaction/comment/index.vue'
const mocks = vi.hoisted(() => ({
  policy: vi.fn(), list: vi.fn(async () => ({ list: [], total: 0 })), audit: vi.fn(),
  confirm: vi.fn(async () => {}), prompt: vi.fn(async () => ({ value: '原因' }))
}))
vi.mock('vue', async original => ({ ...await original(), useSSRContext: () => ({ modules: new Set() }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn() }, ElMessageBox: { confirm: mocks.confirm, prompt: mocks.prompt } }))
vi.mock('@/api/interaction/danmaku', () => ({ listDanmakuPage: mocks.list, auditDanmaku: mocks.audit, getDanmakuPolicy: mocks.policy }))
beforeEach(() => { vi.clearAllMocks(); mocks.policy.mockResolvedValue({ data: { failOpen: false } }) })
const page = async () => {
  const p = Comment.setup({}, { expose() {} })
  await Promise.resolve(); await Promise.resolve()
  return p
}
it('从服务端配置展示从严或放行策略，而不是声称故障已通过', async () => {
  const p = await page(); await p.fetchPolicy()
  expect(p.policyNotice.value).toContain('不广播')
  mocks.policy.mockResolvedValue({ data: { failOpen: true } })
  await p.fetchPolicy(); expect(p.policyNotice.value).toContain('实时展示')
})
it('获取失败或旧服务端没有策略字段时明确未知并可重试', async () => {
  const p = await page(); mocks.policy.mockRejectedValue(new Error('offline'))
  await p.fetchPolicy(); expect(p.policyError.value).toBe(true); expect(p.policyNotice.value).toContain('无法确认')
  mocks.policy.mockResolvedValue({ data: {} }); await p.fetchPolicy(); expect(p.policyError.value).toBe(true)
})
it('待复核原因可展示，通过提示只恢复历史不重复广播', async () => {
  const p = await page()
  expect(p.statusOf({ auditStatus: 0 }).text).toBe('待人工复核')
  await p.handleApprove({ id: '2112366523950714882', content: '内容' })
  expect(mocks.confirm.mock.calls[0][0]).toContain('不会重新广播')
  expect(mocks.audit).toHaveBeenCalledWith('2112366523950714882', 1)
})
