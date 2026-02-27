/**
 * 营销管理路由模块
 */
export default {
  path: '/marketing',
  component: () => import('@/layout/index.vue'),
  redirect: '/marketing/seckill',
  name: 'Marketing',
  meta: {
    title: '营销管理',
    icon: 'Promotion'
  },
  children: [
    {
      path: 'seckill',
      name: 'Seckill',
      component: () => import('@/views/marketing/seckill/index.vue'),
      meta: {
        title: '秒杀管理',
        icon: 'Timer',
        permission: ['marketing:seckill:list'],
        keepAlive: true
      }
    },
    {
      path: 'coupon',
      name: 'Coupon',
      component: () => import('@/views/marketing/coupon/index.vue'),
      meta: {
        title: '优惠券管理',
        icon: 'Ticket',
        permission: ['marketing:coupon:list'],
        keepAlive: true
      }
    }
  ]
}

