/**
 * 秒杀管理 API
 *
 * 字段与后端 SeckillActivityDTO 一致：title / productId / seckillPrice / totalStock /
 * startTime / endTime（ISO 格式 YYYY-MM-DDTHH:mm:ss）/ status（0未开始 1进行中 2已结束）。
 * 权限（后端强制校验）：列表与统计 marketing:seckill:list，其余写操作 marketing:seckill:edit。
 *
 * 修复前：表单用 name/courseId/stock 提交，后端校验失败；新增失败时还会改去调用预热接口，
 * 详情失败时退化为拉整页列表查找——这些"兜底"只会掩盖真实错误，已移除。
 */
import request from '@/utils/request'

/**
 * 秒杀活动列表（分页），返回 { list, total }
 * @param {Object} query 查询参数
 * @param {number} query.pageNum 页码
 * @param {number} query.pageSize 每页数量
 * @param {string} [query.title] 活动标题（模糊匹配）
 * @param {number} [query.status] 状态
 */
export function listSeckill(query) {
  return request({
    url: '/marketing/seckill/list',
    method: 'get',
    params: query
  }).then((res) => {
    const page = res?.data || {}
    return { list: page.records || [], total: Number(page.total || 0) }
  })
}

/**
 * 秒杀活动详情
 * @param {number|string} id 活动ID
 */
export function getSeckill(id) {
  return request({
    url: `/marketing/seckill/${id}`,
    method: 'get'
  })
}

/**
 * 新增秒杀活动
 * @param {Object} data 活动数据（字段见文件头注释）
 */
export function addSeckill(data) {
  return request({
    url: '/marketing/seckill',
    method: 'post',
    data
  })
}

/**
 * 修改秒杀活动
 * @param {Object} data 活动数据（含 id）
 */
export function updateSeckill(data) {
  return request({
    url: '/marketing/seckill',
    method: 'put',
    data
  })
}

/**
 * 删除秒杀活动
 * @param {string|number|Array} ids 活动ID，支持逗号分隔或数组
 */
export function deleteSeckill(ids) {
  return request({
    url: `/marketing/seckill/${Array.isArray(ids) ? ids.join(',') : ids}`,
    method: 'delete'
  })
}

/**
 * 修改活动状态（C 端只展示 status=1 的活动）
 * @param {number|string} id 活动ID
 * @param {number} status 0未开始 1进行中 2已结束
 */
export function changeSeckillStatus(id, status) {
  return request({
    url: `/marketing/seckill/${id}/status`,
    method: 'put',
    data: { status }
  })
}

/**
 * 装载 Redis 库存（按"总库存 - 已成交"，已装载则不覆盖）
 * @param {number|string} activityId 活动ID
 */
export function preloadSeckill(activityId) {
  return request({
    url: '/marketing/seckill/preload',
    method: 'post',
    params: { activityId }
  })
}

/**
 * 秒杀活动统计
 * @param {number|string} id 活动ID
 */
export function getSeckillStatistics(id) {
  return request({
    url: `/marketing/seckill/${id}/statistics`,
    method: 'get'
  })
}
