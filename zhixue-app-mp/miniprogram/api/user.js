/**
 * 用户相关API
 */
const { get } = require('./request')
const FAVORITE_CACHE_KEY = 'ZX_FAVORITE_CACHE'

function readFavoriteCache() {
  return wx.getStorageSync(FAVORITE_CACHE_KEY) || []
}

function writeFavoriteCache(list) {
  wx.setStorageSync(FAVORITE_CACHE_KEY, Array.isArray(list) ? list : [])
}

function upsertFavoriteCache(item) {
  const list = readFavoriteCache()
  const id = item?.id || item?.courseId
  if (!id) {
    return
  }
  const next = [
    {
      ...item,
      id
    },
    ...list.filter((row) => String(row.id || row.courseId) !== String(id))
  ]
  writeFavoriteCache(next.slice(0, 200))
}

function removeFavoriteCache(courseId) {
  const list = readFavoriteCache()
  writeFavoriteCache(list.filter((row) => String(row.id || row.courseId) !== String(courseId)))
}

/** 后端尚未提供学习进度相关接口时抛出的标记错误，便于页面做"功能开发中"提示 */
const ERR_NOT_IMPLEMENTED = 'STUDY_PROGRESS_API_NOT_IMPLEMENTED'

/**
 * 获取学习记录。
 *
 * 注意：后端当前没有任何学习记录接口（无对应表与端点），
 * 此处显式抛错而不是返回空数据伪装成功——否则页面会把"功能缺失"
 * 显示成"暂无学习记录"，掩盖真实问题。
 */
function getStudyRecords() {
  return Promise.reject(new Error(ERR_NOT_IMPLEMENTED))
}

/**
 * 获取收藏列表。
 * 后端路径：GET /course/interaction/favorites（网关该路由无 StripPrefix）
 */
function getFavoriteList(params = {}) {
  return get('/course/interaction/favorites', params)
}

/**
 * 更新学习进度。
 * 后端当前无对应接口，显式失败，不再静默吞掉。
 */
function updateStudyProgress() {
  return Promise.reject(new Error(ERR_NOT_IMPLEMENTED))
}

module.exports = {
  ERR_NOT_IMPLEMENTED,
  getStudyRecords,
  getFavoriteList,
  updateStudyProgress,
  readFavoriteCache,
  writeFavoriteCache,
  upsertFavoriteCache,
  removeFavoriteCache
}
