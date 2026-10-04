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
    url: `/system/menu/info/${menuId}`,
    method: 'get'
  })
}

/**
 * 新增菜单（字段与后端 SysMenu 实体一致）
 * @param {Object} data 菜单数据
 * @param {number|string} data.parentId 父菜单ID，顶级为 0
 * @param {number} data.menuType 菜单类型（0目录 1菜单 2按钮）
 * @param {string} data.menuName 菜单名称
 * @param {string} data.path 路由地址
 * @param {string} data.component 组件路径（目录为 Layout）
 * @param {string} data.perms 权限标识
 * @param {string} data.icon 菜单图标
 * @param {number} data.orderNum 显示顺序
 * @param {number} data.visible 是否显示（1显示 0隐藏）
 * @param {number} data.status 状态（0正常 1停用）
 */
export function addMenu(data) {
  return request({
    url: '/system/menu',
    method: 'post',
    data
  })
}

/**
 * 修改菜单（字段同新增，另需 id）
 * @param {Object} data 菜单数据
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
