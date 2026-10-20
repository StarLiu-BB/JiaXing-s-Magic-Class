import { createApp } from 'vue'
import { ElMessage } from 'element-plus'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'

// 主题与全局样式必须在 Element Plus 样式之后引入
import '@fontsource-variable/geist'
import '@/styles/index.scss'

import App from './App.vue'
import router from './router'
import { useConversationsStore } from './stores/conversations'
import { useUserStore } from './stores/user'
import { setSessionExpiredHandler } from './utils/session'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 任何请求发现登录失效：清掉本地登录态，带上当前地址去登录页，登录后回到原处
setSessionExpiredHandler(() => {
  useUserStore(pinia).reset()
  useConversationsStore(pinia).reset()
  const current = router.currentRoute.value
  if (current.name && current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
