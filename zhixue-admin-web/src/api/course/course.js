/**
 * 课程管理 API
 */
import request from '@/utils/request'

/**
 * 课程列表（分页）
 * @param {Object} query 查询参数
 * @param {number} query.pageNum 页码
 * @param {number} query.pageSize 每页数量
 * @param {string} query.title 课程标题
 * @param {number} query.categoryId 分类ID
 * @param {number} query.status 状态（0-草稿，1-已发布，2-已下架）
 * @param {string} query.teacherName 讲师名称
 */
export function listCourse(query) {
  return request({
    url: '/course/list',
    method: 'get',
    params: query
  })
}

/**
 * 课程详情
 * @param {number} courseId 课程ID
 */
export function getCourse(courseId) {
  return request({
    url: `/course/${courseId}`,
    method: 'get'
  })
}

/**
 * 新增课程
 * @param {Object} data 课程数据
 * @param {string} data.title 课程标题
 * @param {string} data.cover 课程封面
 * @param {number} data.categoryId 分类ID
 * @param {number} data.price 价格
 * @param {number} data.originalPrice 原价
 * @param {number} data.teacherId 讲师ID
 * @param {string} data.description 课程简介
 * @param {Array} data.objectives 学习目标
 * @param {Array} data.suitable 适合人群
 * @param {number} data.status 状态
 */
export function addCourse(data) {
  return request({
    url: '/course',
    method: 'post',
    data
  })
}

/**
 * 修改课程
 * @param {Object} data 课程数据
 * @param {number} data.id 课程ID
 * @param {string} data.title 课程标题
 * @param {string} data.cover 课程封面
 * @param {number} data.categoryId 分类ID
 * @param {number} data.price 价格
 * @param {number} data.originalPrice 原价
 * @param {number} data.teacherId 讲师ID
 * @param {string} data.description 课程简介
 * @param {Array} data.objectives 学习目标
 * @param {Array} data.suitable 适合人群
 * @param {number} data.status 状态
 */
export function updateCourse(data) {
  return request({
    url: '/course',
    method: 'put',
    data
  })
}

/**
 * 删除课程
 * @param {string|Array} courseIds 课程ID，支持单个ID或ID数组
 */
export function deleteCourse(courseIds) {
  return request({
    url: `/course/${courseIds}`,
    method: 'delete'
  })
}

/**
 * 发布课程
 * @param {number} courseId 课程ID
 */
export function publishCourse(courseId) {
  return request({
    url: `/course/${courseId}/publish`,
    method: 'put'
  })
}

/**
 * 下架课程
 * @param {number} courseId 课程ID
 */
export function offlineCourse(courseId) {
  return request({
    url: `/course/${courseId}/offline`,
    method: 'put'
  })
}

/**
 * 获取分类列表
 */
export function getCategoryList() {
  return request({
    url: '/course/category/list',
    method: 'get'
  })
}

