/**
 * 收银台：对页面屏蔽具体支付渠道。
 *
 * 当前仅接入「模拟收银台」（本地联调，不产生真实扣款），付款确认后由服务端
 * 走与真实渠道相同的验签入账流程。接入微信支付后，只需在此改为调用
 * wx.requestPayment（参数取自 /pay 的返回），页面代码无需改动。
 */
const { payOrder, confirmMockPay } = require('../api/order')

const PAY_CHANNEL = 'mock'

function askConfirm(amount) {
  return new Promise((resolve) => {
    wx.showModal({
      title: '模拟收银台',
      content: `应付金额 ¥${amount}\n本地联调环境，不会产生真实扣款`,
      confirmText: '确认支付',
      cancelText: '暂不支付',
      success: (res) => resolve(!!res.confirm),
      fail: () => resolve(false)
    })
  })
}

/**
 * 为订单完成收银。
 * @param {{orderNo: string, amount?: number}} order 订单
 * @returns {Promise<boolean>} true=支付成功；false=用户放弃支付（订单保留，可稍后继续）
 */
async function checkout(order) {
  if (!order || !order.orderNo) {
    throw new Error('订单信息不完整')
  }
  // 先让服务端校验订单可支付（本人、待支付、未过期、未重复购买），并以服务端金额为准
  const payRes = await payOrder(order.orderNo, PAY_CHANNEL)
  const pay = (payRes && payRes.data) || {}
  const amount = Number(pay.amount != null ? pay.amount : order.amount || 0).toFixed(2)

  const confirmed = await askConfirm(amount)
  if (!confirmed) {
    return false
  }
  await confirmMockPay(order.orderNo)
  return true
}

module.exports = {
  checkout
}
