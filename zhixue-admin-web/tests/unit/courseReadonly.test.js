import { beforeEach, expect, it, vi } from 'vitest'
import Publish from '@/views/course/publish/index.vue'
import routes from '@/router/modules/course'

const mocks = vi.hoisted(() => ({
  route: { meta: { readonly: true }, query: { id: '2007' } },
  store: { permissions: ['course:list', 'course:edit', 'course:publish'] },
  writes: vi.fn(), confirm: vi.fn(), read: vi.fn(async () => ({ data: [] })), course: vi.fn()
}))
vi.mock('vue', async importOriginal => ({ ...await importOriginal(), onMounted: vi.fn(), useSSRContext: () => ({ modules: new Set() }) }))
vi.mock('vue-router', () => ({ useRoute: () => mocks.route, useRouter: () => ({ replace: vi.fn(), push: vi.fn(), back: vi.fn() }) }))
vi.mock('@/stores/modules/user', () => ({ useUserStore: () => mocks.store }))
vi.mock('@/utils/auth', () => ({ getToken: () => '' }))
vi.mock('element-plus', () => ({ ElMessage: { warning: vi.fn(), error: vi.fn(), success: vi.fn() }, ElMessageBox: { confirm: mocks.confirm } }))
vi.mock('@/api/course/course', () => ({
  getCourse: mocks.course, getCategoryList: mocks.read,
  addCourse: mocks.writes, updateCourse: mocks.writes, publishCourse: mocks.writes
}))
vi.mock('@/api/course/chapter', () => ({
  listChapter: mocks.read, addChapter: mocks.writes, updateChapter: mocks.writes, deleteChapter: mocks.writes,
  addLesson: mocks.writes, updateLesson: mocks.writes, deleteLesson: mocks.writes
}))

beforeEach(() => {
  vi.clearAllMocks()
  mocks.route.meta = { readonly: true }
  mocks.store.permissions = ['course:list', 'course:edit', 'course:publish']
  mocks.course.mockResolvedValue({ data: { id: '2007', title: '测试课' } })
})
const page = () => Publish.setup({}, { expose() {} })

it('查看路由只要求查看权限，隐藏但可访问；编辑允许 edit 或 publish', () => {
  const view = routes.children.find(route => route.path === 'view')
  expect(view?.meta).toMatchObject({ permission: ['course:list'], readonly: true, hidden: true })
  expect(routes.children.find(route => route.path === 'publish').meta.permission).toContain('course:edit')
})

it('只读页全部翻步不保存、不要求有课时，写 handler 无效', async () => {
  const p = page()
  expect(p.readonly.value).toBe(true)
  await p.loadCourseData()
  await p.nextStep(); await p.nextStep(); await p.nextStep()
  expect(p.activeStep.value).toBe(3)
  await p.saveCourse()
  await p.submitNodeForm()
  await p.handleDeleteNode({ id: '1' })
  await p.handlePublish()
  p.handleAddChapter()
  expect(p.beforeCoverUpload({ type: 'image/png', size: 10 })).toBe(false)
  expect(mocks.writes).not.toHaveBeenCalled()
  expect(mocks.confirm).not.toHaveBeenCalled()
  expect(p.nodeDialogVisible.value).toBe(false)
})

it('只读用户仍可读取章节，不依赖 edit 或 chapter:list', async () => {
  mocks.store.permissions = ['course:list']
  const p = page()
  p.courseId.value = '2007'
  await p.loadChapters()
  expect(mocks.read).toHaveBeenCalledWith('2007')
})

it('首次已有课加载完成前及加载失败后不能上传，重试成功才解锁', async () => {
  mocks.route.meta = {}
  const p = page()
  expect(p.beforeCoverUpload({ type: 'image/png', size: 10 })).toBe(false)
  mocks.course.mockRejectedValueOnce(new Error('offline'))
  await p.loadCourseData()
  expect(p.loadError.value).toBeTruthy()
  await p.saveCourse()
  expect(mocks.writes).not.toHaveBeenCalled()
  mocks.course.mockResolvedValueOnce({ data: { id: '2007', title: '测试课' } })
  await p.loadCourseData()
  expect(p.loadError.value).toBe('')
  expect(p.beforeCoverUpload({ type: 'image/png', size: 10 })).toBe(true)
})

it('仅发布权限可阅而不可编辑，编辑权限不等于发布权限', () => {
  mocks.route.meta = {}
  mocks.store.permissions = ['course:publish']
  const publishing = page()
  expect(publishing.readonly.value).toBe(true)
  expect(publishing.canPublish.value).toBe(true)
  mocks.store.permissions = ['course:edit']
  const editing = page()
  expect(editing.readonly.value).toBe(false)
  expect(editing.canPublish.value).toBe(false)
})
