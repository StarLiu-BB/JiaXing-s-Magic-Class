/**
 * 学生互动路由模块
 *
 * 弹幕广播前审核：通过(1)、拒绝(2)、故障待人工复核(0)。
 * 人工复核与当前降级策略统一在"弹幕审核"页展示。
 */
export default {
  path: '/interaction',
  component: () => import('@/layout/index.vue'),
  redirect: '/interaction/comment',
  name: 'Interaction',
  meta: {
    title: '学生互动',
    icon: 'ChatLineSquare'
  },
  children: [
    {
      path: 'comment',
      name: 'Comment',
      component: () => import('@/views/interaction/comment/index.vue'),
      meta: {
        title: '弹幕审核',
        icon: 'Comment',
        permission: ['interaction:danmaku:list'],
        keepAlive: true
      }
    }
  ]
}
