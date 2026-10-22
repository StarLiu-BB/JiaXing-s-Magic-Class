const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function setup({ courses, categories } = {}) {
  const calls = []
  const app = { globalData: {} }
  let definition
  const courseApi = {
    getCategoryList: categories || (async () => ({ code: 200, data: [
      { id: '100', parentId: '0', name: '编程' },
      { id: '2112366523950714882', parentId: '100', name: 'Java' }
    ] })),
    getCoursePage: params => { calls.push(structuredClone(params)); return courses ? courses(params) : Promise.resolve({ code: 200, data: { records: [], total: 0 } }) }
  }
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../miniprogram/pages/category/index.js'), 'utf8'), {
    require: () => courseApi, Page: value => { definition = value }, getApp: () => app,
    wx: { showToast() {}, navigateTo() {} }, console
  })
  const page = { data: structuredClone(definition.data), setData(patch) { Object.assign(this.data, structuredClone(patch)) } }
  for (const [key, value] of Object.entries(definition)) if (typeof value === 'function') page[key] = value.bind(page)
  return { page, calls, app }
}

test('首页分类等扁平分类就绪后消费，字符串 parentId 0 和长 ID 不丢失', async () => {
  const ready = deferred()
  const { page, calls, app } = setup({ categories: () => ready.promise })
  app.globalData.pendingCategoryId = '2112366523950714882'
  const loading = page.onLoad({})
  const showing = page.onShow()
  ready.resolve({ code: 200, data: [{ id: '100', parentId: '0', name: '编程' }, { id: '2112366523950714882', parentId: '100', name: 'Java' }] })
  await loading; await showing
  assert.equal(page.data.categoryList.length, 2)
  assert.equal(page.data.currentCategory, 1)
  assert.equal(page.data.currentSubCategory, 1)
  assert.equal(calls.length, 1)
  assert.equal(calls[0].categoryId, '2112366523950714882')
  assert.equal(app.globalData.pendingCategoryId, null)
})

test('服务端第一页带分类排序，触底追加下一页', async () => {
  const { page, calls } = setup({ courses: async params => ({ code: 200, data: { records: [{ id: String(params.pageNum) }], total: 12 } }) })
  page.setData({ categoryId: '100', sortType: 'price', priceSort: 'asc' })
  await page.fetchCourses(true)
  assert.equal(calls[0].pageSize, 10)
  assert.equal(calls[0].categoryId, '100')
  assert.equal(calls[0].sort, 'price_asc')
  assert.equal(page.data.hasMore, true)
  await page.onReachBottom()
  assert.equal(calls[1].pageNum, 2)
  assert.deepEqual(page.data.courseList.map(row => row.id), ['1', '2'])
  assert.equal(page.data.hasMore, false)
  await page.onReachBottom()
  assert.equal(calls.length, 2)
})

test('切换筛选时串行请求最新条件，旧结果不得覆盖', async () => {
  const first = deferred()
  let n = 0
  const { page, calls } = setup({ courses: async () => ++n === 1 ? first.promise : { code: 200, data: { records: [{ id: 'latest' }], total: 1 } } })
  const loading = page.fetchCourses(true)
  page.setData({ categoryId: '200' })
  const reset = page.fetchCourses(true)
  page.setData({ categoryId: '300' })
  page.fetchCourses(true)
  assert.equal(calls.length, 1)
  first.resolve({ code: 200, data: { records: [{ id: 'stale' }], total: 100 } })
  await loading; await reset
  assert.equal(calls.length, 2)
  assert.equal(calls[1].categoryId, '300')
  assert.deepEqual(page.data.courseList.map(row => row.id), ['latest'])
  assert.equal(page.data.loading, false)
})

test('加载更多失败保留列表和页码，可再次触底重试', async () => {
  let n = 0
  const { page } = setup({ courses: async params => {
    if (++n === 2) throw new Error('network')
    return { code: 200, data: { records: [{ id: String(params.pageNum) }], total: 11 } }
  } })
  await page.fetchCourses(true)
  await page.onReachBottom()
  assert.equal(page.data.pageNum, 1)
  assert.deepEqual(page.data.courseList.map(row => row.id), ['1'])
  assert.ok(page.data.loadError)
  await page.onReachBottom()
  assert.equal(page.data.pageNum, 2)
  assert.equal(page.data.loadError, '')
})

test('列表业务失败不伪装成空列表成功', async () => {
  const { page } = setup({ courses: async () => ({ code: 503, msg: '繁忙' }) })
  await page.fetchCourses(true)
  assert.ok(page.data.loadError)
  assert.equal(page.data.pageNum, 0)
})

test('分类选择和第一次价格排序发起服务端重置', async () => {
  const { page, calls } = setup()
  await page.onLoad({})
  await page.onCategorySelect({ currentTarget: { dataset: { index: 1 } } })
  assert.equal(calls.at(-1).categoryId, '100')
  await page.onSortTap({ currentTarget: { dataset: { type: 'price' } } })
  assert.equal(calls.at(-1).sort, 'price_asc')
})

test('连续重试分类合并为一次加载，不能被旧失败覆盖', async () => {
  const retry = deferred()
  let count = 0
  const { page } = setup({ categories: () => ++count === 1 ? Promise.reject(new Error('offline')) : retry.promise })
  await page.onLoad({})
  assert.ok(page.data.loadError)
  const a = page.onRetry()
  const b = page.onRetry()
  assert.equal(count, 2)
  retry.resolve({ code: 200, data: [{ id: '100', parentId: '0', name: '编程' }] })
  await a; await b
  assert.equal(page.data.loadError, '')
  assert.equal(page.data.categoryList.length, 2)
})

test('课程容器触底绑定分页，移除不存在的销量排序', () => {
  const wxml = fs.readFileSync(path.resolve(__dirname, '../miniprogram/pages/category/index.wxml'), 'utf8')
  assert.ok(/<scroll-view class="course-content"[^>]*bindscrolltolower="onReachBottom"/.test(wxml))
  assert.ok(!wxml.includes('data-type="sales"'))
})
