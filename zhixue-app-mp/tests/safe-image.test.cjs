const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const vm = require('node:vm')
const path = require('node:path')

function createImage(src, fallback = '/images/course-default.png') {
  let definition
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../miniprogram/components/safe-image/index.js'), 'utf8'), {
    Component(options) { definition = options }
  })
  const image = {
    properties: { src, fallback },
    data: { ...definition.data },
    updates: [],
    setData(patch) { this.updates.push(patch); Object.assign(this.data, patch) }
  }
  for (const [name, method] of Object.entries(definition.methods)) image[name] = method.bind(image)
  image.updateSource()
  return image
}

test('空地址和字符串 null/undefined 使用指定的真实兜底图片', () => {
  for (const src of ['', null, undefined, 'null', 'undefined', '   ']) {
    const image = createImage(src, '/images/avatar-default.png')
    assert.equal(image.data.resolvedSrc, '/images/avatar-default.png')
  }
})

test('非空图片失败只回退一次，兜底失败不重复加载', () => {
  const image = createImage('https://example.com/missing.jpg')
  assert.equal(image.data.resolvedSrc, 'https://example.com/missing.jpg')
  image.onError()
  assert.equal(image.data.resolvedSrc, '/images/course-default.png')
  const count = image.updates.length
  image.onError()
  image.onError()
  assert.equal(image.updates.length, count)
})

test('失败回退后更换图片地址，恢复新地址加载', () => {
  const image = createImage('https://example.com/old.jpg')
  image.onError()
  image.properties.src = 'https://example.com/new.jpg'
  image.updateSource()
  assert.equal(image.data.resolvedSrc, 'https://example.com/new.jpg')
})

test('旧图片迟到的失败事件不能把新地址覆盖成兜底', () => {
  const image = createImage('https://example.com/old.jpg')
  const oldGeneration = image.data.generation
  image.properties.src = 'https://example.com/new.jpg'
  image.updateSource()
  image.onError({ currentTarget: { dataset: { generation: oldGeneration } } })
  assert.equal(image.data.resolvedSrc, 'https://example.com/new.jpg')
})
