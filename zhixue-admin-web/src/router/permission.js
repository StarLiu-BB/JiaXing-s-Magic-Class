/**
 * 路由权限工具
 */

/**
 * 检查是否有权限
 * @param {Array} permissions 用户权限列表
 * @param {string|Array} requiredPermission 需要的权限
 * @returns {boolean} 是否有权限
 */
export function hasPermission(permissions, requiredPermission) {
  if (!requiredPermission) {
    return true
  }
  
  if (Array.isArray(requiredPermission)) {
    return requiredPermission.some(perm => permissions.includes(perm))
  }
  
  return permissions.includes(requiredPermission)
}

/**
 * 过滤路由（根据权限）
 * @param {Array} routes 路由列表
 * @param {Array} permissions 用户权限列表
 * @returns {Array} 过滤后的路由
 */
export function filterRoutes(routes, permissions) {
  return routes.filter(route => {
    if (route.meta && route.meta.permission) {
      return hasPermission(permissions, route.meta.permission)
    }
    return true
  }).map(route => {
    if (route.children) {
      route.children = filterRoutes(route.children, permissions)
    }
    return route
  })
}

