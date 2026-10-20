import request from '@/utils/request'

/** 下单只传课程 ID：下单人取自登录态，金额由服务端核价；已有未过期的待支付订单时返回该订单 */
export const createOrder = (productId) => request({ url: '/order/create', method: 'post', data: { productId: String(productId) } })

/** 发起支付（模拟收银台）：服务端校验订单可支付，返回以服务端为准的 amount */
export const payOrder = (orderNo) => request({ url: '/pay', method: 'post', data: { orderNo, payChannel: 'mock' } })

/** 模拟收银台确认付款（本地联调，不产生真实扣款） */
export const confirmMockPay = (orderNo) => request({ url: '/pay/mock/confirm', method: 'post', data: { orderNo } })
