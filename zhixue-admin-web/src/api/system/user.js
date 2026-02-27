/**
 * 用户管理 API
 */
import request from '@/utils/request'

/**
 * 用户列表（分页）
 * @param {Object} query 查询参数
 * @param {number} query.pageNum 页码
 * @param {number} query.pageSize 每页数量
 * @param {string} query.username 用户名
 * @param {string} query.phone 手机号
 * @param {number} query.status 状态
 */
export function listUser(query) {
  return request({
    url: '/system/user/list',
    method: 'get',
    params: query
  })
}

/**
 * 用户详情
 * @param {number} userId 用户ID
 */
export function getUser(userId) {
  return request({
    url: `/system/user/${userId}`,
    method: 'get'
  })
}

/**
 * 新增用户
 * @param {Object} data 用户数据
 * @param {string} data.username 用户名
 * @param {string} data.password 密码
 * @param {string} data.nickname 昵称
 * @param {string} data.phone 手机号
 * @param {string} data.email 邮箱
 * @param {number} data.status 状态
 * @param {Array} data.roleIds 角色ID数组
 */
export function addUser(data) {
  return request({
    url: '/system/user',
    method: 'post',
    data
  })
}

/**
 * 修改用户
 * @param {Object} data 用户数据
 * @param {number} data.id 用户ID
 * @param {string} data.username 用户名
 * @param {string} data.nickname 昵称
 * @param {string} data.phone 手机号
 * @param {string} data.email 邮箱
 * @param {number} data.status 状态
 * @param {Array} data.roleIds 角色ID数组
 */
export function updateUser(data) {
  return request({
    url: '/system/user',
    method: 'put',
    data
  })
}

/**
 * 删除用户
 * @param {string|Array} userIds 用户ID，支持单个ID或ID数组
 */
export function deleteUser(userIds) {
  return request({
    url: `/system/user/${userIds}`,
    method: 'delete'
  })
}

/**
 * 重置密码
 * @param {number} userId 用户ID
 * @param {string} password 新密码
 */
export function resetUserPwd(userId, password) {
  const data = {
    userId,
    password
  }
  return request({
    url: '/system/user/resetPwd',
    method: 'put',
    data
  })
}

/**
 * 修改用户状态
 * @param {number} userId 用户ID
 * @param {number} status 状态（0-正常，1-停用）
 */
export function changeUserStatus(userId, status) {
  const data = {
    userId,
    status
  }
  return request({
    url: '/system/user/changeStatus',
    method: 'put',
    data
  })
}

