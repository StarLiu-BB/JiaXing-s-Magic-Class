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

module.exports = {
  getAvailableCouponList,
  claimCoupon,
  readCouponCache,
  saveClaimedCoupon
}
