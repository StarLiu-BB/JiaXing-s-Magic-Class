import { useUserStore } from '@/stores/modules/user'

/**
 * 权限指令 v-hasPermi
 * 使用方式：
 * <el-button v-hasPermi="['system:user:add']">新增</el-button>
 * <el-button v-hasPermi="'system:user:edit'">编辑</el-button>
 */
export default {
  mounted(el, binding) {
    updatePermissionDisplay(el, binding.value)
  },
  updated(el, binding) {
    updatePermissionDisplay(el, binding.value)
  }
}

function updatePermissionDisplay(el, value) {
  const userStore = useUserStore()
  const permissions = userStore.permissions || []

  if (!value) {
    console.warn('v-hasPermi: 需要传入权限值')
    el.style.display = 'none'
    return
  }

  const permissionValues = Array.isArray(value) ? value : [value]
  const allowed = permissionValues.some(permission => permissions.includes(permission))
  el.style.display = allowed ? '' : 'none'
}
