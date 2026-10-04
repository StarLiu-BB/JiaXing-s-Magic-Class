/**
 * 学生互动路由模块
 *
 * 原"待审队列"页已删除：弹幕发送时由 AI 直接判为通过(1)或拒绝(2)，
 * 不存在待审核(0)状态，那个页面永远为空；人工复核统一在"弹幕审核"页完成。
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
