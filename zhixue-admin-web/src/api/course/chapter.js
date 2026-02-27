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
    url: `/course/${courseId}/chapters`,
    method: 'get'
  })
}

/**
 * 章节详情
 * @param {number} chapterId 章节ID
 */
export function getChapter(chapterId) {
  return request({
    url: `/course/chapter/${chapterId}`,
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
    url: `/course/chapter/${chapterId}/lessons`,
    method: 'get'
  })
}

/**
 * 课时详情
 * @param {number} lessonId 课时ID
 */
export function getLesson(lessonId) {
  return request({
    url: `/course/lesson/${lessonId}`,
    method: 'get'
  })
}

/**
 * 新增课时
 * @param {Object} data 课时数据
 * @param {number} data.chapterId 章节ID
 * @param {string} data.title 课时标题
 * @param {number} data.duration 时长（分钟）
 * @param {string} data.videoUrl 视频地址
 * @param {boolean} data.isFree 是否免费
 * @param {number} data.sortOrder 排序
 * @param {string} data.description 课时描述
 */
export function addLesson(data) {
  return request({
    url: '/course/lesson',
    method: 'post',
    data
  })
}

/**
 * 修改课时
 * @param {Object} data 课时数据
 * @param {number} data.id 课时ID
 * @param {string} data.title 课时标题
 * @param {number} data.duration 时长（分钟）
 * @param {string} data.videoUrl 视频地址
 * @param {boolean} data.isFree 是否免费
 * @param {number} data.sortOrder 排序
 * @param {string} data.description 课时描述
 */
export function updateLesson(data) {
  return request({
    url: '/course/lesson',
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
    url: `/course/lesson/${lessonId}`,
    method: 'delete'
  })
}

