/**
 * 用户相关API
 */
const { get } = require('./request')

/**
 * 获取学习记录
 * @param {object} params 查询参数
 */
function getStudyRecords(params = {}) {
  return get('/user/study/records', params)
}

/**
 * 获取收藏列表
 * @param {object} params 查询参数
 */
function getFavoriteList(params = {}) {
  return get('/user/favorites', params)
}

/**
 * 更新学习进度
 * @param {object} data 学习进度数据
 */
function updateStudyProgress(data) {
  const { post } = require('./request')
  return post('/user/study/progress', data)
}

module.exports = {
  getStudyRecords,
  getFavoriteList,
  updateStudyProgress
}

