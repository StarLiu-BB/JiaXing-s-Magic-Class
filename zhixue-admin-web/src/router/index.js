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

// 业务路由静态注册，通过前置守卫做权限拦截，避免刷新动态页时出现无匹配告警。
export const asyncRoutes = Object.values(import.meta.glob('./modules/*.js', { eager: true }))
  .map(module => module.default)
  .filter(Boolean)

// 创建路由实例
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    ...constantRoutes,
    ...asyncRoutes,
    {
      path: '/:pathMatch(.*)*',
      redirect: '/404',
      hidden: true
    }
  ],
  scrollBehavior: () => ({ left: 0, top: 0 })
})

// 白名单路由（不需要登录验证）
const whiteList = ['/login', '/404', '/401']

// 路由守卫 - 前置守卫
router.beforeEach(async (to, from, next) => {
  NProgress.start()

  const userStore = useUserStore()

  if (userStore.token) {
    if (to.path === '/login') {
      next({ path: '/dashboard' })
      NProgress.done()
    } else {
      try {
        if (!userStore.infoLoaded) {
          await userStore.getInfo()
        }
        if (!userStore.routesLoaded) {
          userStore.markRoutesLoaded(true)
        }

        if (hasPermission(to, userStore.permissions)) {
          next()
        } else {
          next({ path: '/401', replace: true })
          NProgress.done()
        }
      } catch (error) {
        userStore.resetToken()
        next(`/login?redirect=${to.path}`)
        NProgress.done()
      }
    }
  } else {
    if (whiteList.indexOf(to.path) !== -1) {
      next()
    } else {
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
