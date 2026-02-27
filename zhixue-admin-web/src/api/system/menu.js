/**
 * 菜单管理 API
 */
import request from '@/utils/request'

/**
 * 菜单列表
 * @param {Object} query 查询参数
 * @param {string} query.menuName 菜单名称
 * @param {number} query.status 状态
 */
export function listMenu(query) {
  return request({
    url: '/system/menu/list',
    method: 'get',
    params: query
  })
}

/**
 * 菜单详情
 * @param {number} menuId 菜单ID
 */
export function getMenu(menuId) {
  return request({
    url: `/system/menu/${menuId}`,
    method: 'get'
  })
}

/**
 * 新增菜单
 * @param {Object} data 菜单数据
 * @param {number} data.parentId 父菜单ID
 * @param {string} data.menuType 菜单类型（M-目录，C-菜单，F-按钮）
 * @param {string} data.menuName 菜单名称
 * @param {string} data.path 路由地址
 * @param {string} data.component 组件路径
 * @param {string} data.permission 权限标识
 * @param {string} data.icon 菜单图标
 * @param {number} data.menuSort 显示顺序
 * @param {number} data.status 状态
 * @param {string} data.remark 备注
 */
export function addMenu(data) {
  return request({
    url: '/system/menu',
    method: 'post',
    data
  })
}

/**
 * 修改菜单
 * @param {Object} data 菜单数据
 * @param {number} data.id 菜单ID
 * @param {number} data.parentId 父菜单ID
 * @param {string} data.menuType 菜单类型（M-目录，C-菜单，F-按钮）
 * @param {string} data.menuName 菜单名称
 * @param {string} data.path 路由地址
 * @param {string} data.component 组件路径
 * @param {string} data.permission 权限标识
 * @param {string} data.icon 菜单图标
 * @param {number} data.menuSort 显示顺序
 * @param {number} data.status 状态
 * @param {string} data.remark 备注
 */
export function updateMenu(data) {
  return request({
    url: '/system/menu',
    method: 'put',
    data
  })
}

/**
 * 删除菜单
 * @param {number} menuId 菜单ID
 */
export function deleteMenu(menuId) {
  return request({
    url: `/system/menu/${menuId}`,
    method: 'delete'
  })
}

