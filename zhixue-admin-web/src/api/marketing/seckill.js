/**
 * 秒杀管理 API
 */
import request from '@/utils/request'

/**
 * 秒杀活动列表（分页）
 * @param {Object} query 查询参数
 * @param {number} query.pageNum 页码
 * @param {number} query.pageSize 每页数量
 * @param {string} query.name 活动名称
 * @param {number} query.status 状态（0-未开始，1-进行中，2-已结束）
 */
export function listSeckill(query) {
  return request({
    url: '/marketing/seckill/list',
    method: 'get',
    params: query
  })
}

/**
 * 秒杀活动详情
 * @param {number} seckillId 秒杀活动ID
 */
export function getSeckill(seckillId) {
  return request({
    url: `/marketing/seckill/${seckillId}`,
    method: 'get'
  })
}

/**
 * 新增秒杀活动
 * @param {Object} data 秒杀活动数据
 * @param {string} data.name 活动名称
 * @param {number} data.courseId 课程ID
 * @param {number} data.seckillPrice 秒杀价格
 * @param {number} data.stock 库存数量
 * @param {string} data.startTime 开始时间
 * @param {string} data.endTime 结束时间
 * @param {number} data.status 状态
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
 * @param {Object} data 秒杀活动数据
 * @param {number} data.id 秒杀活动ID
 * @param {string} data.name 活动名称
 * @param {number} data.courseId 课程ID
 * @param {number} data.seckillPrice 秒杀价格
 * @param {number} data.stock 库存数量
 * @param {string} data.startTime 开始时间
 * @param {string} data.endTime 结束时间
 * @param {number} data.status 状态
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
 * @param {string|Array} seckillIds 秒杀活动ID，支持单个ID或ID数组
 */
export function deleteSeckill(seckillIds) {
  return request({
    url: `/marketing/seckill/${seckillIds}`,
    method: 'delete'
  })
}

/**
 * 启用/停用秒杀活动
 * @param {number} seckillId 秒杀活动ID
 * @param {number} status 状态（0-停用，1-启用）
 */
export function changeSeckillStatus(seckillId, status) {
  return request({
    url: `/marketing/seckill/${seckillId}/status`,
    method: 'put',
    data: { status }
  })
}

/**
 * 秒杀活动统计
 * @param {number} seckillId 秒杀活动ID
 */
export function getSeckillStatistics(seckillId) {
  return request({
    url: `/marketing/seckill/${seckillId}/statistics`,
    method: 'get'
  })
}

