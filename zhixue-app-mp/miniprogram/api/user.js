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

/**
 * 获取学习记录
 * @param {object} params 查询参数
 */
function getStudyRecords(params = {}) {
  return get('/user/study/records', params)
    .catch(() => get('/course/study/records', params))
    .catch(() => ({
      code: 200,
      data: {
        list: [],
        records: []
      }
    }))
}

/**
 * 获取收藏列表
 * @param {object} params 查询参数
 */
function getFavoriteList(params = {}) {
  return get('/user/favorites', params)
    .catch(() => get('/interaction/course/interaction/favorites', params))
    .catch(() => {
      const list = readFavoriteCache()
      return {
        code: 200,
        data: {
          list,
          records: list,
          total: list.length
        }
      }
    })
}

/**
 * 更新学习进度
 * @param {object} data 学习进度数据
 */
function updateStudyProgress(data) {
  const { post } = require('./request')
  return post('/user/study/progress', data)
    .catch(() => post('/course/study/progress', data))
}

module.exports = {
  getStudyRecords,
  getFavoriteList,
  updateStudyProgress,
  readFavoriteCache,
  writeFavoriteCache,
  upsertFavoriteCache,
  removeFavoriteCache
}
