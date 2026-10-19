const { get, post } = require('./request')

/**
 * 优惠券：一律以服务端为准。
 * 此前用本地缓存标记"已领取"：换设备或清缓存后已领的券又显示「领取」，一点就报"已达到领取上限"；
 * 且缓存按领取记录 ID 存，和券 ID 对不上，列表里会多出一张无名的"优惠券"。
 */

/** 当前可领取的优惠券（Coupon：name / discount / thresholdAmount / endTime / userLimit） */
function getAvailableCouponList() {
  return get('/marketing/coupon/available')
}

/** 我领到的券（CouponUser：couponId / status 0未使用 1已使用 2已过期 / expireTime） */
function getMyCoupons() {
  return get('/marketing/coupon/user/list')
}

/** 券详情（用于展示已下线活动里我领过的券） */
function getCoupon(couponId) {
  return get(`/marketing/coupon/${couponId}`, {}, { showLoading: false })
}

/** 领取：领取人以服务端登录态为准，无需传 userId */
function claimCoupon(couponId) {
  return post('/marketing/coupon/claim', { couponId })
}

/**
 * 进行中的秒杀活动（含剩余库存 remainingStock）
 */
function getOnlineSeckills() {
  return get('/marketing/seckill/online')
}

/**
 * 为当前登录用户领取一次性秒杀令牌（参与人以服务端登录态为准）
 * @param {number|string} activityId 活动ID
 */
function getSeckillToken(activityId) {
  return post(`/marketing/seckill/token?activityId=${activityId}`)
}

/**
 * 提交秒杀。成交后返回订单服务中的待支付订单 { orderNo, amount }，可直接进入收银台
 * @param {number|string} activityId 活动ID
 * @param {string} token 秒杀令牌
 */
function doSeckill(activityId, token) {
  return post('/marketing/seckill/do', { activityId, token })
}

module.exports = {
  getAvailableCouponList,
  getMyCoupons,
  getCoupon,
  claimCoupon,
  getOnlineSeckills,
  getSeckillToken,
  doSeckill
}
