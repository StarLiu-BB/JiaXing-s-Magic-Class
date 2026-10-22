import { beforeEach, expect, it, vi } from 'vitest'
import Coupon from '@/views/marketing/coupon/index.vue'
import Seckill from '@/views/marketing/seckill/index.vue'

const mocks = vi.hoisted(() => ({
  issue: vi.fn(async () => ({ data: 1 })), create: vi.fn(async () => ({})),
  list: vi.fn(async () => ({ list: [], total: 0 })),
  detail: vi.fn(async () => ({ data: { id: '1', productId: '2112366523950714882' } })),
  prompt: vi.fn(async () => ({ value: '2112366523950714882' }))
}))
vi.mock('vue', async original => ({ ...await original(), useSSRContext: () => ({ modules: new Set() }) }))
vi.mock('@/stores/modules/user', () => ({ useUserStore: () => ({ permissions: ['marketing:coupon:edit'] }) }))
vi.mock('element-plus', () => ({ ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }, ElMessageBox: { prompt: mocks.prompt } }))
vi.mock('@/api/marketing/coupon', () => ({
  addCoupon: vi.fn(), changeCouponStatus: vi.fn(), deleteCoupon: vi.fn(), getCoupon: vi.fn(),
  getCouponStatistics: vi.fn(), issueCoupon: mocks.issue, listCoupon: mocks.list, updateCoupon: vi.fn()
}))
vi.mock('@/api/marketing/seckill', () => ({
  addSeckill: mocks.create, changeSeckillStatus: vi.fn(), deleteSeckill: vi.fn(), getSeckill: mocks.detail,
  getSeckillStatistics: vi.fn(), listSeckill: mocks.list, preloadSeckill: vi.fn(), updateSeckill: vi.fn()
}))
beforeEach(() => vi.clearAllMocks())

it('发券真实 handler 下发字符串用户 ID', async () => {
  const p = Coupon.setup({}, { expose() {} })
  await p.handleIssue({ id: '1', name: '券' })
  expect(mocks.issue).toHaveBeenCalledWith('1', ['2112366523950714882'])
})

it('秒杀编辑回填和提交均保留字符串课程 ID', async () => {
  const p = Seckill.setup({}, { expose() {} })
  await p.openEditDialog({ id: '1' })
  expect(p.form.productId).toBe('2112366523950714882')
  p.openCreateDialog()
  p.form.productId = '2112366523950714882'
  p.form.timeRange = ['2026-10-22T01:00:00', '2026-10-23T01:00:00']
  p.formRef.value = { validate: async () => true }
  await p.submitForm()
  expect(mocks.create.mock.calls[0][0].productId).toBe('2112366523950714882')
})

it('绕过 UI 校验也不能提交超 Long 范围课程 ID', async () => {
  const p = Seckill.setup({}, { expose() {} })
  p.form.productId = '9223372036854775808'
  p.formRef.value = { validate: async () => true }
  await p.submitForm()
  expect(mocks.create).not.toHaveBeenCalled()
})
