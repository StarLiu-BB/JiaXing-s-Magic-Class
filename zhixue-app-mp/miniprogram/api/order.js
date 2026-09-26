/**
 * 订单相关API
 */
const { post, get } = require('./request')
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
 * 创建订单
 * @param {object} data 订单数据
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

module.exports = {
  createOrder,
  getOrderList,
  getOrderDetail,
  readOrderCache,
  saveOrderToCache
}
