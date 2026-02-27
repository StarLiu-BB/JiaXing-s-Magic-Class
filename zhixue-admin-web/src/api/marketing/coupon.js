/**
 * 优惠券管理 API
 */
import request from '@/utils/request'

/**
 * 优惠券列表（分页）
 * @param {Object} query 查询参数
 * @param {number} query.pageNum 页码
 * @param {number} query.pageSize 每页数量
 * @param {string} query.name 优惠券名称
 * @param {number} query.type 优惠券类型（0-满减券，1-折扣券，2-代金券）
 * @param {number} query.status 状态（0-未开始，1-进行中，2-已结束）
 */
export function listCoupon(query) {
  return request({
    url: '/marketing/coupon/list',
    method: 'get',
    params: query
  })
}

/**
 * 优惠券详情
 * @param {number} couponId 优惠券ID
 */
export function getCoupon(couponId) {
  return request({
    url: `/marketing/coupon/${couponId}`,
    method: 'get'
  })
}

/**
 * 新增优惠券
 * @param {Object} data 优惠券数据
 * @param {string} data.name 优惠券名称
 * @param {number} data.type 优惠券类型（0-满减券，1-折扣券，2-代金券）
 * @param {number} data.discountAmount 优惠金额（满减券/代金券）
 * @param {number} data.discountRate 折扣率（折扣券，如：0.8表示8折）
 * @param {number} data.minAmount 最低使用金额
 * @param {number} data.totalCount 发放总数（-1表示不限）
 * @param {number} data.perUserLimit 每人限领数量
 * @param {string} data.startTime 开始时间
 * @param {string} data.endTime 结束时间
 * @param {number} data.status 状态
 * @param {string} data.remark 备注
 */
export function addCoupon(data) {
  return request({
    url: '/marketing/coupon',
    method: 'post',
    data
  })
}

/**
 * 修改优惠券
 * @param {Object} data 优惠券数据
 * @param {number} data.id 优惠券ID
 * @param {string} data.name 优惠券名称
 * @param {number} data.type 优惠券类型
 * @param {number} data.discountAmount 优惠金额
 * @param {number} data.discountRate 折扣率
 * @param {number} data.minAmount 最低使用金额
 * @param {number} data.totalCount 发放总数
 * @param {number} data.perUserLimit 每人限领数量
 * @param {string} data.startTime 开始时间
 * @param {string} data.endTime 结束时间
 * @param {number} data.status 状态
 * @param {string} data.remark 备注
 */
export function updateCoupon(data) {
  return request({
    url: '/marketing/coupon',
    method: 'put',
    data
  })
}

/**
 * 删除优惠券
 * @param {string|Array} couponIds 优惠券ID，支持单个ID或ID数组
 */
export function deleteCoupon(couponIds) {
  return request({
    url: `/marketing/coupon/${couponIds}`,
    method: 'delete'
  })
}

/**
 * 启用/停用优惠券
 * @param {number} couponId 优惠券ID
 * @param {number} status 状态（0-停用，1-启用）
 */
export function changeCouponStatus(couponId, status) {
  return request({
    url: `/marketing/coupon/${couponId}/status`,
    method: 'put',
    data: { status }
  })
}

/**
 * 发放优惠券
 * @param {number} couponId 优惠券ID
 * @param {Array} userIds 用户ID数组（为空则发放给所有用户）
 */
export function issueCoupon(couponId, userIds = []) {
  return request({
    url: `/marketing/coupon/${couponId}/issue`,
    method: 'post',
    data: { userIds }
  })
}

/**
 * 优惠券统计
 * @param {number} couponId 优惠券ID
 */
export function getCouponStatistics(couponId) {
  return request({
    url: `/marketing/coupon/${couponId}/statistics`,
    method: 'get'
  })
}

