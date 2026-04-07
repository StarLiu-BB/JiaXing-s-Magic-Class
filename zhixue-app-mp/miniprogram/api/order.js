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
  try {
    return await get('/order/list', params)
  } catch (error) {
    const cache = readOrderCache()
    const statusFilter = params.status
    let list = [...cache]
    if (statusFilter !== undefined && statusFilter !== null && statusFilter !== '') {
      list = list.filter((item) => String(item.status) === String(statusFilter))
    }
    return {
      code: 200,
      data: {
        list,
        records: list,
        total: list.length
      }
    }
  }
}

/**
 * 获取订单详情
 * @param {number} orderId 订单ID
 */
async function getOrderDetail(orderId) {
  try {
    return await get('/order/detail', { orderNo: orderId })
  } catch (error) {
    const row = readOrderCache().find(
      (item) => String(item.orderNo || item.id || item.orderId) === String(orderId)
    )
    return { code: 200, data: row || null }
  }
}

module.exports = {
  createOrder,
  getOrderList,
  getOrderDetail,
  readOrderCache,
  saveOrderToCache
}
