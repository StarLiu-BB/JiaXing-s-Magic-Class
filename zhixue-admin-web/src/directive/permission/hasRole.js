import { useUserStore } from '@/stores/modules/user'

/**
 * 角色指令 v-hasRole
 * 使用方式：
 * <el-button v-hasRole="['admin']">管理员按钮</el-button>
 * <el-button v-hasRole="'editor'">编辑按钮</el-button>
 */
export default {
  mounted(el, binding) {
    updateRoleDisplay(el, binding.value)
  },
  updated(el, binding) {
    updateRoleDisplay(el, binding.value)
  }
}

function updateRoleDisplay(el, value) {
  const userStore = useUserStore()
  const roles = userStore.roles || []

  if (!value) {
    console.warn('v-hasRole: 需要传入角色值')
    el.style.display = 'none'
    return
  }

  const roleValues = Array.isArray(value) ? value : [value]
  const allowed = roleValues.some(role => roles.includes(role))
  el.style.display = allowed ? '' : 'none'
}
