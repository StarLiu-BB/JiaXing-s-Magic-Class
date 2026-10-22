const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

const root = path.resolve(__dirname, '../miniprogram')

function setup({ loggedIn = false } = {}) {
  const calls = []
  const apiCalls = []
  const storage = new Map(loggedIn ? [['zhixue_token', 'test-token']] : [])
  const app = { globalData: { isLogin: loggedIn } }
  let modal
  const wx = {
    switchTab: options => calls.push(['switchTab', options]),
    navigateTo: options => calls.push(['navigateTo', options]),
    showModal: options => { modal = options },
    getStorageSync: key => storage.get(key),
    showToast() {}
  }
  const cache = new Map()
  function loadModule(relative) {
    const file = path.resolve(root, relative)
    if (cache.has(file)) return cache.get(file)
    const module = { exports: {} }
    vm.runInNewContext(fs.readFileSync(file, 'utf8'), {
      module, exports: module.exports, wx, console,
      require: name => loadModule(path.relative(root, path.resolve(path.dirname(file), `${name}.js`)))
    }, { filename: file })
    cache.set(file, module.exports)
    return module.exports
  }
  function page(relative) {
    let definition
    const file = path.resolve(root, relative)
    vm.runInNewContext(fs.readFileSync(file, 'utf8'), {
      wx, console, getApp: () => app, Page: value => { definition = value },
      require: name => {
        if (name.includes('/utils/nav')) return loadModule('utils/nav.js')
        return new Proxy({}, { get: (_, method) => (...args) => {
          apiCalls.push([name, method, args])
          return Promise.resolve({ code: 200, data: {} })
        } })
      }
    }, { filename: file })
    const instance = {
      data: structuredClone(definition.data),
      setData: patch => Object.assign(instance.data, structuredClone(patch))
    }
    for (const [key, value] of Object.entries(definition)) {
      if (typeof value === 'function') instance[key] = value.bind(instance)
    }
    return instance
  }
  return { calls, apiCalls, loadModule, page, confirm: (confirm = true) => modal.success({ confirm }) }
}

for (const method of ['onFavoriteTap', 'onBuyTap', 'onStudyTap']) {
  test(`未登录${method}确认后使用 Tab 导航且不调用业务 API`, async () => {
    const env = setup()
    const page = env.page('pages/course/detail/index.js')
    await page[method]()
    env.confirm()
    assert.deepEqual(env.calls.map(([kind, options]) => [kind, options.url]), [['switchTab', '/pages/my/index']])
    assert.equal(env.apiCalls.length, 0)
  })
}

test('未登录秒杀确认后使用 Tab 导航且不签发令牌', async () => {
  const env = setup()
  const page = env.page('pages/seckill/index.js')
  page.data.activities = [{ id: '63001', canBuy: true }]
  await page.onSeckillTap({ currentTarget: { dataset: { id: '63001' } } })
  env.confirm()
  assert.deepEqual(env.calls.map(([kind, options]) => [kind, options.url]), [['switchTab', '/pages/my/index']])
  assert.equal(env.apiCalls.length, 0)
})

test('取消登录确认不跳转', async () => {
  const env = setup()
  await env.page('pages/course/detail/index.js').onBuyTap()
  env.confirm(false)
  assert.equal(env.calls.length, 0)
})

test('通用登录检查确认后正确打开默认 Tab 或自定义普通页', () => {
  const env = setup()
  const auth = env.loadModule('utils/auth.js')
  assert.equal(auth.checkLoginAndRedirect(), false)
  env.confirm()
  assert.equal(env.calls[0][0], 'switchTab')
  assert.equal(env.calls[0][1].url, '/pages/my/index')
  const target = '/pages/course/detail/index?id=2112366523950714882'
  assert.equal(auth.checkLoginAndRedirect(target), false)
  env.confirm()
  assert.equal(env.calls[1][0], 'navigateTo')
  assert.equal(env.calls[1][1].url, target)
})

test('已登录时通用检查不显示登录提示或跳转', () => {
  const env = setup({ loggedIn: true })
  assert.equal(env.loadModule('utils/auth.js').checkLoginAndRedirect(), true)
  assert.equal(env.calls.length, 0)
})

test('Tab 列表与 app.json 一致，导航保留回调且不允许 options 覆盖目标', () => {
  const env = setup()
  const nav = env.loadModule('utils/nav.js')
  const tabs = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8')).tabBar.list.map(item => `/${item.pagePath}`)
  assert.deepEqual([...nav.TAB_PAGES].sort(), tabs.sort())
  const success = () => {}
  nav.openPage('/pages/category/index?categoryId=100', { success, url: '/pages/my/index' })
  assert.equal(env.calls[0][0], 'switchTab')
  assert.equal(env.calls[0][1].url, '/pages/category/index')
  assert.equal(env.calls[0][1].success, success)
  nav.openPage('/pages/course/detail/index?id=2007', { success })
  assert.equal(env.calls[1][0], 'navigateTo')
  assert.equal(env.calls[1][1].url, '/pages/course/detail/index?id=2007')
})

test('我的页状态 2 与订单列表一致显示已取消', () => {
  const wxml = fs.readFileSync(path.join(root, 'pages/my/index.wxml'), 'utf8')
  const label = wxml.match(/data-status="2"[\s\S]*?<text class="order-label">([^<]+)<\/text>/)
  assert.ok(label, '保留状态 2 的订单入口')
  assert.equal(label[1], '已取消')
})
