import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/modules/user'
import { useTagsViewStore } from '@/stores/modules/tagsView'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

// 配置 NProgress
NProgress.configure({ showSpinner: false })

// 静态路由（不需要权限验证）
export const constantRoutes = [
  {
    path: '/redirect',
    component: () => import('@/layout/index.vue'),
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect/index.vue')
      }
    ]
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: {
      title: '登录',
      hidden: true
    }
  },
  {
    path: '/404',
    name: '404',
    component: () => import('@/views/error/404.vue'),
    meta: {
      title: '404',
      hidden: true
    }
  },
  {
    path: '/401',
    name: '401',
    component: () => import('@/views/error/401.vue'),
    meta: {
      title: '401',
      hidden: true
    }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Home.vue'),
        meta: {
          title: '首页',
          icon: 'HomeFilled',
          affix: true,
          keepAlive: true
        }
      }
    ]
  }
]

// 动态路由（需要权限验证）
export const asyncRoutes = []

// 创建路由实例
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes,
  scrollBehavior: () => ({ left: 0, top: 0 })
})

// 白名单路由（不需要登录验证）
const whiteList = ['/login', '/404', '/401']

// 路由守卫 - 前置守卫
router.beforeEach(async (to, from, next) => {
  // 开始进度条
  NProgress.start()

  const userStore = useUserStore()
  const tagsViewStore = useTagsViewStore()

  // 判断是否有 token
  if (userStore.token) {
    // 已登录
    if (to.path === '/login') {
      // 如果已登录，跳转到首页
      next({ path: '/dashboard' })
      NProgress.done()
    } else {
      // 检查是否已获取用户信息（通过 roles 判断）
      if (userStore.roles && userStore.roles.length > 0) {
        // 已有用户信息，直接放行
        if (hasPermission(to, userStore.permissions)) {
          next()
        } else {
          next({ path: '/401', replace: true })
          NProgress.done()
        }
      } else {
        // 没有用户信息，设置默认权限后放行
        try {
          // 临时设置权限（实际应该从后端获取）
          if (userStore.permissions.length === 0) {
            userStore.permissions = [
              'system:user:list',
              'system:role:list',
              'system:menu:list',
              'system:dict:list',
              'course:list',
              'course:category:list',
              'course:chapter:list',
              'marketing:seckill:list',
              'marketing:coupon:list'
            ]
          }
          // 设置默认角色
          if (!userStore.roles || userStore.roles.length === 0) {
            userStore.roles = ['ADMIN']
          }
          
          // 根据用户权限动态加载路由
          await loadAsyncRoutes(userStore.permissions)
          // 确保 addRoutes 完成，设置 replace: true
          next({ ...to, replace: true })
        } catch (error) {
          // 获取用户信息失败，清除 token，跳转到登录页
          userStore.resetToken()
          next(`/login?redirect=${to.path}`)
          NProgress.done()
        }
      }
    }
  } else {
    // 未登录
    if (whiteList.indexOf(to.path) !== -1) {
      // 在白名单中，直接访问
      next()
    } else {
      // 不在白名单中，跳转到登录页
      next(`/login?redirect=${to.path}`)
      NProgress.done()
    }
  }
})

// 路由守卫 - 后置守卫
router.afterEach((to) => {
  // 结束进度条
  NProgress.done()

  const tagsViewStore = useTagsViewStore()
  
  // 添加访问的视图
  if (to.name) {
    tagsViewStore.addVisitedView(to)
    tagsViewStore.addCachedView(to)
  }
})

// 动态加载路由
function loadAsyncRoutes(permissions) {
  return new Promise((resolve) => {
    // 导入所有模块路由
    const modules = import.meta.glob('./modules/*.js', { eager: true })
    
    // 过滤有权限的路由
    const accessedRoutes = []
    
    Object.keys(modules).forEach((key) => {
      const route = modules[key].default
      if (hasPermission(route, permissions)) {
        // 递归处理子路由
        if (route.children) {
          route.children = filterAsyncRoutes(route.children, permissions)
        }
        accessedRoutes.push(route)
      }
    })
    
    // 添加动态路由（添加到根路由）
    accessedRoutes.forEach(route => {
      router.addRoute(route)
    })
    
    // 添加 404 路由（必须放在最后）
    router.addRoute({
      path: '/:pathMatch(.*)*',
      redirect: '/404',
      hidden: true
    })
    
    resolve(accessedRoutes)
  })
}

// 过滤异步路由
function filterAsyncRoutes(routes, permissions) {
  const res = []
  
  routes.forEach(route => {
    const tmp = { ...route }
    if (hasPermission(tmp, permissions)) {
      if (tmp.children) {
        tmp.children = filterAsyncRoutes(tmp.children, permissions)
      }
      res.push(tmp)
    }
  })
  
  return res
}

// 检查是否有权限
function hasPermission(route, permissions) {
  if (route.meta && route.meta.permission) {
    // 如果路由需要权限，检查用户是否有该权限
    if (permissions && permissions.length > 0) {
      return permissions.some(permission => {
        if (Array.isArray(route.meta.permission)) {
          return route.meta.permission.includes(permission)
        } else {
          return route.meta.permission === permission
        }
      })
    } else {
      return false
    }
  } else {
    // 如果路由不需要权限，直接允许访问
    return true
  }
}

export default router
