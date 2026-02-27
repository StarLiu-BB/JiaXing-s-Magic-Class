import hasPermi from './permission/hasPermi'
import hasRole from './permission/hasRole'

/**
 * 注册所有指令
 * @param {import('vue').App} app Vue 应用实例
 */
export function setupDirectives(app) {
  // 权限指令
  app.directive('hasPermi', hasPermi)
  app.directive('hasRole', hasRole)
}

// 导出指令，方便单独使用
export { hasPermi, hasRole }

