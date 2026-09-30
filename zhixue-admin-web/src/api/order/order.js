/**
 * 订单管理 API
 *
 * 权限（后端强制校验）：列表/详情/统计 order:list，取消 order:cancel，退款 order:refund。
 * 不具备 order:list 的用户调用列表时，后端只返回其本人订单。
 */
import request from '@/utils/request'

/**
 * 订单列表（分页），返回 { records, total, pages }
 * @param {Object} query 查询参数
 * @param {number} query.pageNum 页码
 * @param {number} query.pageSize 每页数量
 * @param {string} [query.orderNo] 订单号（精确匹配）
 * @param {number} [query.userId] 下单用户ID
 * @param {number} [query.status] 订单状态（0-待支付，1-已支付，2-已取消，3-已退款）
 */
export function listOrder(query) {
  return request({
    url: '/order/list',
    method: 'get',
    params: query
  })
}

/**
 * 订单详情
 * @param {number|string} orderId 订单ID
 */
export function getOrder(orderId) {
  return request({
    url: `/order/${orderId}`,
    method: 'get'
  })
}

/**
 * 取消待支付订单
 * @param {number|string} orderId 订单ID
 * @param {string} [reason] 取消原因
 */
export function cancelOrder(orderId, reason) {
  return request({
    url: `/order/${orderId}/cancel`,
    method: 'put',
    data: { reason }
  })
}

/**
 * 退款（仅已支付订单）。退款后该用户的课程观看权限随即失效。
 * @param {number|string} orderId 订单ID
 * @param {string} reason 退款原因
 */
export function refundOrder(orderId, reason) {
  return request({
    url: `/order/${orderId}/refund`,
    method: 'put',
    data: { reason }
  })
}

/**
 * 订单统计
 * @param {Object} [query] 查询参数
 * @param {string} [query.startTime] 开始时间 yyyy-MM-dd HH:mm:ss
 * @param {string} [query.endTime] 结束时间 yyyy-MM-dd HH:mm:ss
 */
export function getOrderStatistics(query) {
  return request({
    url: '/order/statistics',
    method: 'get',
    params: query
  })
}
