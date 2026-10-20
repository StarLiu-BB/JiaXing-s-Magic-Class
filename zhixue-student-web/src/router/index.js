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
      { path: 'chat', name: 'chat', component: ChatView }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'chat' } }
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(createAuthGuard(() => useUserStore(), (type, message) => ElMessage({ type, message })))

export default router
