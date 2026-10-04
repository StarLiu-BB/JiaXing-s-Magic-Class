/**
 * 章节管理 API
 */
import request from '@/utils/request'

/**
 * 章节列表
 * @param {number} courseId 课程ID
 */
export function listChapter(courseId) {
  return request({
    url: `/course/chapter/list/${courseId}`,
    method: 'get'
  })
}

/**
 * 章节详情
 * @param {number} chapterId 章节ID
 */
export function getChapter(chapterId) {
  return request({
    url: `/course/chapter/info/${chapterId}`,
    method: 'get'
  })
}

/**
 * 新增章节
 * @param {Object} data 章节数据
 * @param {number} data.courseId 课程ID
 * @param {string} data.title 章节标题
 * @param {number} data.sortOrder 排序
 */
export function addChapter(data) {
  return request({
    url: '/course/chapter',
    method: 'post',
    data
  })
}

/**
 * 修改章节
 * @param {Object} data 章节数据
 * @param {number} data.id 章节ID
 * @param {string} data.title 章节标题
 * @param {number} data.sortOrder 排序
 */
export function updateChapter(data) {
  return request({
    url: '/course/chapter',
    method: 'put',
    data
  })
}

/**
 * 删除章节
 * @param {number} chapterId 章节ID
 */
export function deleteChapter(chapterId) {
  return request({
    url: `/course/chapter/${chapterId}`,
    method: 'delete'
  })
}

/**
 * 课时列表
 * @param {number} chapterId 章节ID
 */
export function listLesson(chapterId) {
  return request({
    url: `/course/section/list/${chapterId}`,
    method: 'get'
  })
}

/**
 * 课时详情
 * @param {number} lessonId 课时ID
 */
export function getLesson(lessonId) {
  return request({
    url: `/course/section/info/${lessonId}`,
    method: 'get'
  })
}

/**
 * 新增课时（字段与后端 Section 实体一致；是否可试看由课程的"试看节数"统一决定，课时本身没有免费标记）
 * @param {Object} data 课时数据
 * @param {number} data.courseId 课程ID
 * @param {number} data.chapterId 章节ID
 * @param {string} data.title 课时标题
 * @param {number} data.duration 时长（秒）
 * @param {string} data.videoUrl 视频地址
 * @param {number} data.orderNum 排序
 * @param {number} data.status 0未发布 1已发布
 */
export function addLesson(data) {
  return request({
    url: '/course/section',
    method: 'post',
    data
  })
}

/**
 * 修改课时（字段同新增，另需 id）
 * @param {Object} data 课时数据
 */
export function updateLesson(data) {
  return request({
    url: '/course/section',
    method: 'put',
    data
  })
}

/**
 * 删除课时
 * @param {number} lessonId 课时ID
 */
export function deleteLesson(lessonId) {
  return request({
    url: `/course/section/${lessonId}`,
    method: 'delete'
  })
}
