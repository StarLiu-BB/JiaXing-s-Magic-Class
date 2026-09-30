/**
 * 订单管理路由模块
 */
export default {
  path: '/order',
  component: () => import('@/layout/index.vue'),
  redirect: '/order/list',
  name: 'Order',
  meta: {
    title: '订单管理',
    icon: 'Tickets'
  },
  children: [
    {
      path: 'list',
      name: 'OrderList',
      component: () => import('@/views/order/list/index.vue'),
      meta: {
        title: '订单列表',
        icon: 'List',
        permission: ['order:list'],
        keepAlive: true
      }
    }
  ]
}
