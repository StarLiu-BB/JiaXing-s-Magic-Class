/**
 * 订单相关API
 */
const { post, get } = require('./request')

/**
 * 创建订单
 * @param {object} data 订单数据
 */
function createOrder(data) {
  return post('/order/create', data)
}

/**
 * 获取订单列表
 * @param {object} params 查询参数
 */
function getOrderList(params = {}) {
  return get('/order/list', params)
}

/**
 * 获取订单详情
 * @param {number} orderId 订单ID
 */
function getOrderDetail(orderId) {
  return get(`/order/${orderId}`)
}

module.exports = {
  createOrder,
  getOrderList,
  getOrderDetail
}

