/**
 * 课程相关API
 */
const { get } = require('./request')

/**
 * 获取轮播图列表
 */
function getBannerList() {
  return get('/course/banner/list')
}

/**
 * 获取分类列表
 */
function getCategoryList() {
  return get('/course/category/list')
}

/**
 * 获取热门课程
 */
function getHotCourseList(params = {}) {
  return get('/course/hot/list', params)
}

/**
 * 获取最新课程
 */
function getLatestCourseList(params = {}) {
  return get('/course/latest/list', params)
}

/**
 * 获取课程详情
 * @param {number} courseId 课程ID
 */
function getCourseDetail(courseId) {
  return get(`/course/${courseId}`)
}

/**
 * 获取课程章节列表
 * @param {number} courseId 课程ID
 */
function getCourseChapters(courseId) {
  return get(`/course/${courseId}/chapters`)
}

/**
 * 获取课程评价列表
 * @param {number} courseId 课程ID
 * @param {object} params 查询参数
 */
function getCourseReviews(courseId, params = {}) {
  return get(`/course/${courseId}/reviews`, params)
}

/**
 * 收藏课程
 * @param {number} courseId 课程ID
 */
function favoriteCourse(courseId) {
  const { post } = require('./request')
  return post(`/course/${courseId}/favorite`)
}

/**
 * 取消收藏
 * @param {number} courseId 课程ID
 */
function unfavoriteCourse(courseId) {
  const { del } = require('./request')
  return del(`/course/${courseId}/favorite`)
}

module.exports = {
  getBannerList,
  getCategoryList,
  getHotCourseList,
  getLatestCourseList,
  getCourseDetail,
  getCourseChapters,
  getCourseReviews,
  favoriteCourse,
  unfavoriteCourse
}

