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
  try {
    return await get('/marketing/coupon/available')
  } catch (error) {
    const list = readCouponCache()
    return { code: 200, data: list }
  }
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
