const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

function setup() {
  let definition
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../miniprogram/pages/course/player/index.js'), 'utf8'), {
    Page(options) { definition = options },
    getApp: () => ({ globalData: {} }),
    require: () => ({}), console, wx: {}, setTimeout
  })
  let promptCount = 0
  const lessons = [
    { id: '4021', preview: true, locked: false, videoUrl: 'intro.mp4' },
    { id: '4022', preview: true, locked: false, videoUrl: 'safety.mp4' },
    { id: '4023', preview: false, locked: true }
  ]
  const page = {
    data: { chapters: [{ id: 'chapter', lessons }], chapterId: 'chapter', lessonId: '4022', isPurchased: false },
    setData(patch) { Object.assign(this.data, patch) },
    promptPurchase() { promptCount++ }
  }
  page.canPlayLesson = definition.canPlayLesson.bind(page)
  page.setCurrentLesson = definition.setCurrentLesson.bind(page)
  return { page, promptCount: () => promptCount }
}

test('推荐精准课时导航选中指定lessonId，不是章节第一节', () => {
  const { page } = setup()
  page.setCurrentLesson()
  assert.equal(page.data.currentLesson.id, '4022')
  assert.equal(page.data.videoUrl, 'safety.mp4')
})

test('指定课时被锁定时提示购买，不擅自跳去其他试看课时', () => {
  const { page, promptCount } = setup()
  page.data.lessonId = '4023'
  page.setCurrentLesson()
  assert.equal(promptCount(), 1)
  assert.equal(page.data.videoUrl, undefined)
})
