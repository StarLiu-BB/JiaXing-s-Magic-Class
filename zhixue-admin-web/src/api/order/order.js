/**
 * 订单管理 API
 */
import request from '@/utils/request'

/**
 * 订单列表（分页）
 * @param {Object} query 查询参数
 * @param {number} query.pageNum 页码
 * @param {number} query.pageSize 每页数量
 * @param {string} query.orderNo 订单号
 * @param {number} query.status 订单状态（0-待支付，1-已支付，2-已取消，3-已退款）
 * @param {string} query.startTime 开始时间
 * @param {string} query.endTime 结束时间
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
 * @param {number} orderId 订单ID
 */
export function getOrder(orderId) {
  return request({
    url: `/order/${orderId}`,
    method: 'get'
  })
}

/**
 * 创建订单
 * @param {Object} data 订单数据
 * @param {number} data.courseId 课程ID
 * @param {number} data.couponId 优惠券ID（可选）
 */
export function createOrder(data) {
  return request({
    url: '/order',
    method: 'post',
    data
  })
}

/**
 * 取消订单
 * @param {number} orderId 订单ID
 */
export function cancelOrder(orderId) {
  return request({
    url: `/order/${orderId}/cancel`,
    method: 'put'
  })
}

/**
 * 退款
 * @param {number} orderId 订单ID
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
 * @param {Object} query 查询参数
 * @param {string} query.startTime 开始时间
 * @param {string} query.endTime 结束时间
 */
export function getOrderStatistics(query) {
  return request({
    url: '/order/statistics',
    method: 'get',
    params: query
  })
}

