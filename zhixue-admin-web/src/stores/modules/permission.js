import { defineStore } from 'pinia'
import { ref } from 'vue'
import { asyncRoutes, constantRoutes } from '@/router'

/**
 * 使用 meta.role 判断当前用户是否有权限访问该路由
 * @param {Array} roles 用户角色
 * @param {Object} route 路由对象
 */
function hasPermission(roles, route) {
  if (route.meta && route.meta.roles) {
    return roles.some(role => route.meta.roles.includes(role))
  } else {
    return true
  }
}

/**
 * 递归过滤异步路由表，返回符合用户角色权限的路由表
 * @param {Array} routes 路由表
 * @param {Array} roles 用户角色
 */
function filterAsyncRoutes(routes, roles) {
  const res = []
  
  routes.forEach(route => {
    const tmp = { ...route }
    if (hasPermission(roles, tmp)) {
      if (tmp.children) {
        tmp.children = filterAsyncRoutes(tmp.children, roles)
      }
      res.push(tmp)
    }
  })
  
  return res
}

export const usePermissionStore = defineStore('permission', () => {
  // State
  const routes = ref([])
  const addRoutes = ref([])

  // Actions
  /**
   * 根据角色生成路由
   */
  function generateRoutes(roles) {
    return new Promise(resolve => {
      let accessedRoutes
      
      if (roles.includes('admin')) {
        // 管理员拥有所有权限
        accessedRoutes = asyncRoutes || []
      } else {
        // 根据角色过滤路由
        accessedRoutes = filterAsyncRoutes(asyncRoutes, roles)
      }
      
      addRoutes.value = accessedRoutes
      routes.value = constantRoutes.concat(accessedRoutes)
      
      resolve(accessedRoutes)
    })
  }

  /**
   * 重置路由
   */
  function resetRoutes() {
    routes.value = []
    addRoutes.value = []
  }

  return {
    // State
    routes,
    addRoutes,
    // Actions
    generateRoutes,
    resetRoutes
  }
})

