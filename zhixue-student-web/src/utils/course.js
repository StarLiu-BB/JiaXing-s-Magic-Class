const ORDER_LAST = Number.MAX_SAFE_INTEGER

/** 按 orderNum 排序；相同时按 ID（数字或雪花 ID 字符串）排，保证顺序稳定 */
function byOrder(a, b) {
  const diff = (a.orderNum ?? ORDER_LAST) - (b.orderNum ?? ORDER_LAST)
  return diff || String(a.id).localeCompare(String(b.id), undefined, { numeric: true })
}

/** 课时编号「章.课」，与推荐卡片里的 chapterIndex.lessonIndex 同一规则 */
export function lessonNo(chapterNo, lessonIndex) {
  return `${chapterNo}.${lessonIndex}`
}

/**
 * 把 GET /course/{id}/chapters 的结果整理成目录：章节按顺序、课时按章内顺序编号，ID 一律转成字符串。
 * playable 以服务端的 locked 为准（服务端已按「已购 / 免费 / 试看前 N 节」判定，并清掉了无权课时的视频地址）。
 */
export function buildLessonRows(chapters) {
  return [...(chapters || [])].sort(byOrder).map((chapter, ci) => ({
    chapterId: String(chapter.id),
    chapterNo: ci + 1,
    chapterTitle: chapter.title,
    lessons: [...(chapter.lessons || [])].sort(byOrder).map((lesson, li) => ({
      id: String(lesson.id),
      no: lessonNo(ci + 1, li + 1),
      title: lesson.title,
      duration: lesson.duration,
      durationText: formatDuration(lesson.duration),
      playable: !lesson.locked,
      preview: !!lesson.preview,
      videoUrl: lesson.videoUrl || null
    }))
  }))
}

export function formatDuration(seconds) {
  if (!seconds) {
    return ''
  }
  return `${Math.max(1, Math.round(seconds / 60))} 分钟`
}

export function formatPrice(price) {
  const value = Number(price)
  if (!price || !Number.isFinite(value) || value <= 0) {
    return '免费'
  }
  return `¥${value.toFixed(2).replace(/\.00$/, '')}`
}

/** 推荐卡片右上角的状态：免费 / 已购 / 价格（+ 可试看） */
export function courseBadge(card) {
  if (!(Number(card.price) > 0)) {
    return { text: '免费', tone: 'success' }
  }
  if (card.purchased) {
    return { text: '已购', tone: 'success' }
  }
  return { text: formatPrice(card.price), tone: 'price', preview: !!card.previewable }
}
