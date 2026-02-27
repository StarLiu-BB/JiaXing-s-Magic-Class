import { useUserStore } from '@/stores/modules/user'

/**
 * 角色指令 v-hasRole
 * 使用方式：
 * <el-button v-hasRole="['admin']">管理员按钮</el-button>
 * <el-button v-hasRole="'editor'">编辑按钮</el-button>
 */
export default {
  mounted(el, binding) {
    const { value } = binding
    const userStore = useUserStore()
    const roles = userStore.roles || []

    if (value) {
      // 将角色值转换为数组
      const roleValues = Array.isArray(value) ? value : [value]
      
      // 检查是否有角色
      const hasRole = roleValues.some(role => {
        return roles.includes(role)
      })

      if (!hasRole) {
        // 无角色则移除元素
        el.parentNode && el.parentNode.removeChild(el)
      }
    } else {
      // 如果没有传入角色值，默认移除元素
      console.warn('v-hasRole: 需要传入角色值')
      el.parentNode && el.parentNode.removeChild(el)
    }
  },
  updated(el, binding) {
    const { value } = binding
    const userStore = useUserStore()
    const roles = userStore.roles || []

    if (value) {
      // 将角色值转换为数组
      const roleValues = Array.isArray(value) ? value : [value]
      
      // 检查是否有角色
      const hasRole = roleValues.some(role => {
        return roles.includes(role)
      })

      if (!hasRole) {
        // 无角色则移除元素
        el.parentNode && el.parentNode.removeChild(el)
      }
    }
  }
}

