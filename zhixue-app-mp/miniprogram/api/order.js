/**
 * 订单相关API
 */
const { post, get, put } = require('./request')
const ORDER_CACHE_KEY = 'ZX_ORDER_CACHE'

function readOrderCache() {
  return wx.getStorageSync(ORDER_CACHE_KEY) || []
}

function writeOrderCache(list) {
  wx.setStorageSync(ORDER_CACHE_KEY, Array.isArray(list) ? list : [])
}

function saveOrderToCache(order) {
  if (!order) {
    return
  }
  const list = readOrderCache()
  const orderNo = order.orderNo || order.id || order.orderId
  const next = [
    {
      ...order,
      orderNo,
      updateTime: order.updateTime || new Date().toISOString()
    },
    ...list.filter((item) => String(item.orderNo || item.id) !== String(orderNo))
  ]
  writeOrderCache(next.slice(0, 200))
}

/**
 * 创建订单。只需传 productId：下单人取自登录态、金额由服务端核价，
 * 客户端传入的 userId / amount 会被服务端忽略。已有未过期的待支付订单时返回该订单。
 * @param {{productId: number}} data 订单数据
 */
async function createOrder(data) {
  const res = await post('/order/create', data)
  if (res && res.code === 200 && res.data) {
    saveOrderToCache(res.data)
  }
  return res
}

/**
 * 获取订单列表
 * @param {object} params 查询参数
 */
async function getOrderList(params = {}) {
  // 订单是资金相关数据，失败时绝不能回退本地缓存并伪装成 code:200——
  // 那会让用户把本机（可篡改的）数据当成服务端真实订单状态。
  return get('/order/list', params)
}

/**
 * 获取订单详情
 * @param {number} orderId 订单ID
 */
async function getOrderDetail(orderId) {
  // 同上：订单详情必须来自服务端，不做本地缓存伪装
  return get('/order/detail', { orderNo: orderId })
}

/**
 * 发起支付，返回收银台所需信息（渠道、应付金额）
 * @param {string} orderNo 订单号
 * @param {string} payChannel 支付渠道，当前仅支持 mock（模拟收银台）
 */
function payOrder(orderNo, payChannel) {
  return post('/pay', { orderNo, payChannel })
}

/**
 * 模拟收银台确认付款（本地联调，不产生真实扣款）
 * @param {string} orderNo 订单号
 */
function confirmMockPay(orderNo) {
  return post('/pay/mock/confirm', { orderNo })
}

/**
 * 取消待支付订单
 * @param {string|number} id 订单ID
 */
function cancelOrder(id) {
  return put(`/order/${id}/cancel`, { reason: '用户取消订单' })
}

module.exports = {
  createOrder,
  getOrderList,
  getOrderDetail,
  payOrder,
  confirmMockPay,
  cancelOrder,
  readOrderCache,
  saveOrderToCache
}
