const { get, post } = require('./request')

const COUPON_CACHE_KEY = 'ZX_COUPON_CACHE'

function readCouponCache() {
  return wx.getStorageSync(COUPON_CACHE_KEY) || []
}

function writeCouponCache(list) {
  wx.setStorageSync(COUPON_CACHE_KEY, Array.isArray(list) ? list : [])
}

function saveClaimedCoupon(coupon) {
  const id = coupon?.id || coupon?.couponId
  if (!id) {
    return
  }
  const list = readCouponCache()
  const next = [
    {
      ...coupon,
      id,
      claimed: true,
      claimedTime: coupon.claimedTime || new Date().toISOString()
    },
    ...list.filter((item) => String(item.id || item.couponId) !== String(id))
  ]
  writeCouponCache(next.slice(0, 200))
}

async function getAvailableCouponList() {
  // 不再在失败时回退本地缓存并伪装成 code:200：
  // 优惠券可用性由服务端判定，本机缓存可能已过期或被篡改。
  return get('/marketing/coupon/available')
}

async function claimCoupon(couponId, userId) {
  const res = await post('/marketing/coupon/claim', { couponId, userId })
  if (res?.code === 200) {
    saveClaimedCoupon(res?.data || { id: couponId, couponId, userId })
  }
  return res
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
  claimCoupon,
  getOnlineSeckills,
  getSeckillToken,
  doSeckill,
  readCouponCache,
  saveClaimedCoupon
}
