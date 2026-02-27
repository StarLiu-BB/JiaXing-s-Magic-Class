import { createPinia } from 'pinia'

// 创建 Pinia 实例
const pinia = createPinia()

export default pinia

// 导出所有 stores
export { useUserStore } from './modules/user'
export { usePermissionStore } from './modules/permission'
export { useAppStore } from './modules/app'
export { useTagsViewStore } from './modules/tagsView'
