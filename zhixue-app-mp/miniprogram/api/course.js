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
  return get(`/course/detail/${courseId}`)
}

/**
 * 获取课程章节列表
 * @param {number} courseId 课程ID
 */
function getCourseChapters(courseId) {
  return get(`/course/${courseId}/chapters`)
}

/**
 * 获取公开课程分页
 * @param {object} params 查询参数
 */
function getCoursePage(params = {}) {
  return get('/course/list', params)
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
 * 切换课程收藏状态。
 *
 * 后端是 toggle 语义：POST /course/interaction/favorite/{courseId}
 * 返回 data 为切换后的状态（true=已收藏）。
 * 原先的 /course/{id}/favorite 在后端并不存在，且 CourseController 无此映射。
 * @param {number} courseId 课程ID
 */
function toggleFavoriteCourse(courseId) {
  const { post } = require('./request')
  return post(`/course/interaction/favorite/${courseId}`)
}

/**
 * 查询课程互动状态（是否已点赞/收藏）。
 * @param {number} courseId 课程ID
 */
function getInteractionStatus(courseId) {
  return get(`/course/interaction/status/${courseId}`)
}

module.exports = {
  getBannerList,
  getCategoryList,
  getHotCourseList,
  getLatestCourseList,
  getCoursePage,
  getCourseDetail,
  getCourseChapters,
  getCourseReviews,
  toggleFavoriteCourse,
  getInteractionStatus
}
