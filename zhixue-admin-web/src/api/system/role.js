/**
 * 角色管理 API
 */
import request from '@/utils/request'

/**
 * 角色列表（分页）
 * @param {Object} query 查询参数
 * @param {number} query.pageNum 页码
 * @param {number} query.pageSize 每页数量
 * @param {string} query.roleName 角色名称
 * @param {string} query.roleKey 角色标识
 * @param {number} query.status 状态
 */
export function listRole(query) {
  return request({
    url: '/system/role/list',
    method: 'get',
    params: query
  })
}

/**
 * 角色详情
 * @param {number} roleId 角色ID
 */
export function getRole(roleId) {
  return request({
    url: `/system/role/${roleId}`,
    method: 'get'
  })
}

/**
 * 新增角色
 * @param {Object} data 角色数据
 * @param {string} data.roleName 角色名称
 * @param {string} data.roleKey 角色标识
 * @param {number} data.roleSort 显示顺序
 * @param {number} data.status 状态
 * @param {string} data.remark 备注
 * @param {Array} data.menuIds 菜单ID数组
 */
export function addRole(data) {
  return request({
    url: '/system/role',
    method: 'post',
    data
  })
}

/**
 * 修改角色
 * @param {Object} data 角色数据
 * @param {number} data.id 角色ID
 * @param {string} data.roleName 角色名称
 * @param {string} data.roleKey 角色标识
 * @param {number} data.roleSort 显示顺序
 * @param {number} data.status 状态
 * @param {string} data.remark 备注
 * @param {Array} data.menuIds 菜单ID数组
 */
export function updateRole(data) {
  return request({
    url: '/system/role',
    method: 'put',
    data
  })
}

/**
 * 删除角色
 * @param {string|Array} roleIds 角色ID，支持单个ID或ID数组
 */
export function deleteRole(roleIds) {
  return request({
    url: `/system/role/${roleIds}`,
    method: 'delete'
  })
}

/**
 * 修改角色状态
 * @param {number} roleId 角色ID
 * @param {number} status 状态（0-正常，1-停用）
 */
export function changeRoleStatus(roleId, status) {
  const data = {
    roleId,
    status
  }
  return request({
    url: '/system/role/changeStatus',
    method: 'put',
    data
  })
}

