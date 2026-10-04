/**
 * 弹幕 API
 *
 * 弹幕发送时由 AI 自动审核：auditStatus 1 通过（展示）/ 2 拒绝（不展示），AI 不可用时按通过处理。
 * 权限（后端强制校验）：分页 interaction:danmaku:list，人工复核 interaction:danmaku:audit。
 */
import request from '@/utils/request'

/**
 * 弹幕分页（最新在前），返回 { list, total }
 * @param {{pageNum: number, pageSize: number, roomId?: number|string, auditStatus?: number}} query 查询参数
 */
export function listDanmakuPage(query) {
  return request({
    url: '/danmaku/page',
    method: 'get',
    params: query
  }).then((res) => {
    const page = res?.data || {}
    return { list: page.records || [], total: Number(page.total || 0) }
  })
}

/**
 * 房间已通过的历史弹幕（播放页使用，需登录，无需额外权限）
 * @param {number|string} roomId 房间ID（即课程ID）
 * @param {number} limit 条数，1-200
 */
export function listDanmakuHistory(roomId, limit = 50) {
  return request({
    url: '/danmaku/history',
    method: 'get',
    params: { roomId, limit }
  })
}

/**
 * 人工复核
 * @param {number|string} id 弹幕ID
 * @param {1|2} auditStatus 1 通过 2 拒绝
 * @param {string} [auditRemark] 拒绝原因（拒绝时必填）
 */
export function auditDanmaku(id, auditStatus, auditRemark) {
  return request({
    url: `/danmaku/${id}/audit`,
    method: 'put',
    data: { auditStatus, auditRemark }
  })
}
