/**
 * AI 智能助手路由模块
 */
export default {
  path: '/ai',
  component: () => import('@/layout/index.vue'),
  redirect: '/ai/chat',
  name: 'AI',
  meta: {
    title: 'AI 助手',
    icon: 'ChatDotRound'
  },
  children: [
    {
      path: 'chat',
      name: 'AiChat',
      component: () => import('@/views/ai/chat/index.vue'),
      meta: {
        title: 'AI 对话',
        icon: 'ChatLineRound',
        keepAlive: true
      }
    }
  ]
}
