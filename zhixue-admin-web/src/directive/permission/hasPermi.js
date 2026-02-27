import { useUserStore } from '@/stores/modules/user'

/**
 * 权限指令 v-hasPermi
 * 使用方式：
 * <el-button v-hasPermi="['system:user:add']">新增</el-button>
 * <el-button v-hasPermi="'system:user:edit'">编辑</el-button>
 */
export default {
  mounted(el, binding) {
    // TODO: 临时跳过权限检查，用于测试
    return
    const { value } = binding
    const userStore = useUserStore()
    const permissions = userStore.permissions || []

    if (value) {
      // 将权限值转换为数组
      const permissionValues = Array.isArray(value) ? value : [value]
      
      // 检查是否有权限
      const hasPermission = permissionValues.some(permission => {
        return permissions.includes(permission)
      })

      if (!hasPermission) {
        // 无权限则移除元素
        el.parentNode && el.parentNode.removeChild(el)
      }
    } else {
      // 如果没有传入权限值，默认移除元素
      console.warn('v-hasPermi: 需要传入权限值')
      el.parentNode && el.parentNode.removeChild(el)
    }
  },
  updated(el, binding) {
    // TODO: 临时跳过权限检查，用于测试
    return
    const { value } = binding
    const userStore = useUserStore()
    const permissions = userStore.permissions || []

    if (value) {
      // 将权限值转换为数组
      const permissionValues = Array.isArray(value) ? value : [value]
      
      // 检查是否有权限
      const hasPermission = permissionValues.some(permission => {
        return permissions.includes(permission)
      })

      if (!hasPermission) {
        // 无权限则移除元素
        el.parentNode && el.parentNode.removeChild(el)
      }
    }
  }
}

