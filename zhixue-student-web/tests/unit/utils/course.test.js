import { describe, expect, it } from 'vitest'
import { buildLessonRows, courseBadge, formatDuration, formatPrice } from '@/utils/course'

// 与 GET /course/2007/chapters 的实际返回同构：种子课 ID 是数字，新建课程的雪花 ID 是字符串
const CHAPTERS = [
  {
    id: 3017, title: '锁与并发容器', orderNum: 2,
    lessons: [
      { id: 4024, title: 'ConcurrentHashMap 原理', duration: 1080, orderNum: 2, locked: true, preview: false, videoUrl: null },
      { id: 4023, title: 'synchronized 与 ReentrantLock', duration: 1260, orderNum: 1, locked: true, preview: false, videoUrl: null }
    ]
  },
  {
    id: '2112366524013629441', title: '线程基础', orderNum: 1,
    lessons: [
      { id: '2112366524101709826', title: '线程的创建与生命周期', duration: 900, orderNum: 1, locked: false, preview: true, videoUrl: 'https://v.example.com/1.mp4' }
    ]
  }
]

describe('buildLessonRows', () => {
  it('按章节顺序与章内顺序编号，ID 一律为字符串', () => {
    const rows = buildLessonRows(CHAPTERS)
    expect(rows.map((c) => [c.chapterNo, c.chapterTitle, c.chapterId])).toEqual([
      [1, '线程基础', '2112366524013629441'],
      [2, '锁与并发容器', '3017']
    ])
    expect(rows.flatMap((c) => c.lessons.map((l) => [l.no, l.id, l.playable]))).toEqual([
      ['1.1', '2112366524101709826', true],
      ['2.1', '4023', false],
      ['2.2', '4024', false]
    ])
    expect(rows[0].lessons[0]).toMatchObject({ preview: true, videoUrl: 'https://v.example.com/1.mp4', durationText: '15 分钟' })
  })

  it('没有章节或章节没有课时都不报错', () => {
    expect(buildLessonRows(null)).toEqual([])
    expect(buildLessonRows([{ id: 1, title: '空章', orderNum: 1 }])[0].lessons).toEqual([])
  })
})

describe('显示格式', () => {
  it('时长按分钟取整，不足一分钟算一分钟', () => {
    expect(formatDuration(1080)).toBe('18 分钟')
    expect(formatDuration(20)).toBe('1 分钟')
    expect(formatDuration(null)).toBe('')
  })

  it('价格去掉多余的 .00，0 或缺失为免费', () => {
    expect(formatPrice(99)).toBe('¥99')
    expect(formatPrice('59.50')).toBe('¥59.50')
    expect(formatPrice(0)).toBe('免费')
    expect(formatPrice(null)).toBe('免费')
  })

  it('卡片状态：免费课不显示「已购」；已购不再显示价格', () => {
    expect(courseBadge({ price: 0, purchased: true })).toEqual({ text: '免费', tone: 'success' })
    expect(courseBadge({ price: 99, purchased: true })).toEqual({ text: '已购', tone: 'success' })
    expect(courseBadge({ price: 99, purchased: false, previewable: true })).toEqual({ text: '¥99', tone: 'price', preview: true })
  })
})
