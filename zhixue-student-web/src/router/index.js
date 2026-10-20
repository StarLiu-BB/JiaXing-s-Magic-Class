import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppShell from '@/layout/AppShell.vue'
import ChatView from '@/views/chat/ChatView.vue'
import { useUserStore } from '@/stores/user'
import { createAuthGuard } from './guard'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/login/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: AppShell,
    children: [
      { path: '', redirect: { name: 'chat' } },
      { path: 'chat', name: 'chat', component: ChatView },
      // 两条路由用同一个组件实例：新会话收到 meta 后把地址换成 /chat/:id，不能因此重建页面打断流式输出
      { path: 'chat/:id', name: 'conversation', component: ChatView },
      { path: 'course/:id', name: 'course', component: () => import('@/views/course/CourseDetail.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'chat' } }
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(createAuthGuard(() => useUserStore(), (type, message) => ElMessage({ type, message })))

export default router
