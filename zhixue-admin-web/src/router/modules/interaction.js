/**
 * 学生互动路由模块
 */
export default {
  path: '/interaction',
  component: () => import('@/layout/index.vue'),
  redirect: '/interaction/question',
  name: 'Interaction',
  meta: {
    title: '学生互动',
    icon: 'ChatLineSquare'
  },
  children: [
    {
      path: 'question',
      name: 'Question',
      component: () => import('@/views/interaction/question/index.vue'),
      meta: {
        title: '待审队列',
        icon: 'QuestionFilled',
        keepAlive: true
      }
    },
    {
      path: 'comment',
      name: 'Comment',
      component: () => import('@/views/interaction/comment/index.vue'),
      meta: {
        title: '弹幕管理',
        icon: 'Comment',
        keepAlive: true
      }
    }
  ]
}
