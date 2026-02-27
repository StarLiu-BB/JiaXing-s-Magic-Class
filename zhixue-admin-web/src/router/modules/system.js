/**
 * 系统管理路由模块
 */
export default {
  path: '/system',
  component: () => import('@/layout/index.vue'),
  redirect: '/system/user',
  name: 'System',
  meta: {
    title: '系统管理',
    icon: 'Setting'
  },
  children: [
    {
      path: 'user',
      name: 'User',
      component: () => import('@/views/system/user/index.vue'),
      meta: {
        title: '用户管理',
        icon: 'User',
        permission: ['system:user:list'],
        keepAlive: true
      }
    },
    {
      path: 'role',
      name: 'Role',
      component: () => import('@/views/system/role/index.vue'),
      meta: {
        title: '角色管理',
        icon: 'UserFilled',
        permission: ['system:role:list'],
        keepAlive: true
      }
    },
    {
      path: 'menu',
      name: 'Menu',
      component: () => import('@/views/system/menu/index.vue'),
      meta: {
        title: '菜单管理',
        icon: 'Menu',
        permission: ['system:menu:list'],
        keepAlive: true
      }
    },
    {
      path: 'dict',
      name: 'Dict',
      component: () => import('@/views/system/dict/index.vue'),
      meta: {
        title: '字典管理',
        icon: 'Document',
        permission: ['system:dict:list'],
        keepAlive: true
      }
    }
  ]
}

