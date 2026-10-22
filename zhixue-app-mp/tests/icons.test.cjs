const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const root = path.resolve(__dirname, '../miniprogram')

function pngInfo(file) {
  const bytes = fs.readFileSync(path.join(root, file))
  assert.deepEqual(bytes.subarray(0, 8), Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
  return { width: bytes.readUInt32BE(16), height: bytes.readUInt32BE(20), bytes: bytes.length }
}

test('四个原生导航都有不同的普通和选中图标，PNG 尺寸符合导航要求', () => {
  const config = JSON.parse(fs.readFileSync(path.join(root, 'app.json'), 'utf8'))
  assert.equal(config.tabBar.list.length, 4)
  for (const item of config.tabBar.list) {
    assert.ok(item.iconPath, `${item.text} 缺少普通图标`)
    assert.ok(item.selectedIconPath, `${item.text} 缺少选中图标`)
    assert.notEqual(item.iconPath, item.selectedIconPath)
    for (const file of [item.iconPath, item.selectedIconPath]) {
      const info = pngInfo(file)
      assert.equal(info.width, 81)
      assert.equal(info.height, 81)
      assert.ok(info.bytes < 40000)
    }
    assert.notDeepEqual(fs.readFileSync(path.join(root, item.iconPath)), fs.readFileSync(path.join(root, item.selectedIconPath)))
  }
})

test('分类根据 ID 映射，不依赖排序或中文名称，未知分类不会空白', () => {
  const { withCategoryIcon } = require('../miniprogram/utils/category-icons')
  const expected = { 100: 'code', 110: 'java', 120: 'frontend', 200: 'growth', 210: 'teaching' }
  for (const id of [210, 100, 200, 120, 110]) {
    const category = { id: String(id), name: '重新命名', parentId: 0 }
    const result = withCategoryIcon(category)
    assert.equal(result.icon, `/images/categories/${expected[id]}.png`)
    assert.equal(result.name, '重新命名')
    assert.equal(pngInfo(result.icon).width, 96)
    assert.equal(category.icon, undefined)
  }
  assert.equal(withCategoryIcon({ id: 999 }).icon, '/images/category-default.png')
  assert.equal(pngInfo(withCategoryIcon({ id: 999 }).icon).width, 96)
})

function filesIn(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name)
    return entry.isDirectory() ? filesIn(file) : [file]
  })
}

test('所有模板中的本地图片资源都存在，没有未接入的 iconfont', () => {
  for (const file of filesIn(root).filter(file => file.endsWith('.wxml'))) {
    const source = fs.readFileSync(file, 'utf8')
    assert.ok(!source.includes('iconfont'), `${path.relative(root, file)} 仍使用未接入的图标字体`)
    for (const match of source.matchAll(/\/images\/[\w/-]+\.png/g)) {
      assert.ok(fs.existsSync(path.join(root, match[0])), `${match[0]} 在 ${path.relative(root, file)} 中引用但不存在`)
    }
  }
})
