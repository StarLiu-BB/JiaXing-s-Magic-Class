/**
 * 优惠券管理 API
 *
 * 字段与后端 CouponCreateDTO / CouponUpdateDTO 一致：name / discount（减免金额）/
 * thresholdAmount（使用门槛）/ totalCount / userLimit（每人限领）/
 * startTime / endTime（ISO 格式 YYYY-MM-DDTHH:mm:ss）/ status（1启用 0停用）。
 * 后端只支持满减券，没有"折扣券/代金券"类型与折扣率字段。
 * 权限（后端强制校验）：列表与统计 marketing:coupon:list，其余写操作 marketing:coupon:edit。
 *
 * 修复前：表单用 type/discountAmount/minAmount/perUserLimit 等字段提交，后端校验失败；
 * 列表失败时改调 C 端可领取列表、新增失败时改调别名接口、发放失败时改为给当前登录人领券——
 * 这些"兜底"会把失败伪装成成功，已全部移除。
 */
import request from '@/utils/request'

/**
 * 优惠券列表（分页），返回 { list, total }
 * @param {Object} query 查询参数
 * @param {number} query.pageNum 页码
 * @param {number} query.pageSize 每页数量
 * @param {string} [query.name] 名称（模糊匹配）
 * @param {number} [query.status] 状态（1启用 0停用）
 */
export function listCoupon(query) {
  return request({
    url: '/marketing/coupon/list',
    method: 'get',
    params: query
  }).then((res) => {
    const page = res?.data || {}
    return { list: page.records || [], total: Number(page.total || 0) }
  })
}

/**
 * 优惠券详情
 * @param {number|string} couponId 优惠券ID
 */
export function getCoupon(couponId) {
  return request({
    url: `/marketing/coupon/${couponId}`,
    method: 'get'
  })
}

/**
 * 新增优惠券
 * @param {Object} data 优惠券数据（字段见文件头注释）
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
 * @param {Object} data 优惠券数据（含 id）
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
 * @param {string|number|Array} couponIds 优惠券ID，支持逗号分隔或数组
 */
export function deleteCoupon(couponIds) {
  return request({
    url: `/marketing/coupon/${Array.isArray(couponIds) ? couponIds.join(',') : couponIds}`,
    method: 'delete'
  })
}

/**
 * 启用 / 停用
 * @param {number|string} couponId 优惠券ID
 * @param {number} status 1启用 0停用
 */
export function changeCouponStatus(couponId, status) {
  return request({
    url: `/marketing/coupon/${couponId}/status`,
    method: 'put',
    data: { status }
  })
}

/**
 * 向指定用户发放优惠券，返回成功发放的人数
 * @param {number|string} couponId 优惠券ID
 * @param {Array<number>} userIds 用户ID列表
 */
export function issueCoupon(couponId, userIds) {
  return request({
    url: `/marketing/coupon/${couponId}/issue`,
    method: 'post',
    data: { userIds }
  })
}

/**
 * 优惠券统计
 * @param {number|string} couponId 优惠券ID
 */
export function getCouponStatistics(couponId) {
  return request({
    url: `/marketing/coupon/${couponId}/statistics`,
    method: 'get'
  })
}
