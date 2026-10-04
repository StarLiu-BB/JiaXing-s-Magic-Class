<template>
  <div class="sidebar-wrapper" :class="{ 'has-logo': showLogo }">
    <Logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar wrap-class="scrollbar-wrapper" class="sidebar-scroll">
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :unique-opened="true"
        :collapse-transition="false"
        mode="vertical"
        class="sidebar-menu"
      >
        <SidebarItem
          v-for="route in menuRoutes"
          :key="route.path"
          :item="route"
          :base-path="route.path"
        />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import { useUserStore } from '@/stores/modules/user'
import Logo from './Logo.vue'
import SidebarItem from './SidebarItem.vue'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

const showLogo = computed(() => true)
const isCollapse = computed(() => appStore.isCollapse)
const userPermissions = computed(() => userStore.permissions || [])
const userRoles = computed(() => userStore.roles || [])

const hasRole = (route) => {
  if (!route.meta?.roles || route.meta.roles.length === 0) return true
  return route.meta.roles.some(role => userRoles.value.includes(role))
}

const hasPermission = (route) => {
  if (!route.meta?.permission) return true
  const required = Array.isArray(route.meta.permission)
    ? route.meta.permission
    : [route.meta.permission]
  return required.some(item => userPermissions.value.includes(item))
}

const canAccess = (route) => hasRole(route) && hasPermission(route)

const buildMenus = (routes) => routes
  .filter(route => !route.meta?.hidden && canAccess(route))
  .map(route => {
    const menu = {
      path: route.path,
      meta: route.meta ? { ...route.meta } : {},
      children: route.children ? [...route.children] : undefined
    }
    if (menu.children?.length) {
      menu.children = buildMenus(menu.children)
    }
    return menu
  })
  .filter(route => !route.children || route.children.length > 0)

const moduleRoutes = (() => {
  const modules = import.meta.glob('/src/router/modules/*.js', { eager: true })
  return Object.values(modules)
    .map(m => m.default)
    .filter(Boolean)
})()

const baseMenus = [{
  path: '/dashboard',
  meta: { title: '首页', icon: 'HomeFilled' }
}, ...moduleRoutes]

const menuRoutes = computed(() => {
  return buildMenus(baseMenus)
})

// 当前激活的菜单
const activeMenu = computed(() => {
  const { meta, path } = route
  if (meta.activeMenu) {
    return meta.activeMenu
  }
  return path
})
</script>

<style lang="scss" scoped>
// 菜单颜色、高度、缩进均由 styles/element.scss 中的 --el-menu-* 变量控制
.sidebar-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.sidebar-scroll {
  flex: 1;
  min-height: 0;
}

.sidebar-menu {
  padding: 10px 0 16px;

  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    margin: 2px 10px;
    border-radius: 8px;
    transition: background-color 0.16s var(--zx-ease), color 0.16s var(--zx-ease);

    .el-icon {
      color: var(--el-text-color-secondary);
      transition: color 0.16s var(--zx-ease);
    }

    &:hover .el-icon {
      color: var(--el-text-color-primary);
    }
  }

  :deep(.el-sub-menu .el-menu) {
    background: transparent;
  }

  :deep(.el-menu-item.is-active) {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
    font-weight: 600;

    .el-icon {
      color: var(--el-color-primary);
    }
  }

  // 子菜单处于激活路径时，父级标题加深而不上色，避免两处同时高亮
  :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
    color: var(--el-text-color-primary);
    font-weight: 600;

    .el-icon {
      color: var(--el-color-primary);
    }
  }

  &.el-menu--collapse {
    width: 100%;

    :deep(.el-menu-item),
    :deep(.el-sub-menu__title) {
      margin: 2px 8px;
      padding: 0 !important;
      justify-content: center;
    }

    :deep(.el-sub-menu__icon-arrow) {
      display: none;
    }

    // Element 的折叠样式用 `.el-menu--collapse > .el-sub-menu` 子选择器隐藏标题，
    // 但 SidebarItem 外面包了一层 div，选择器匹配不到，折叠后文字会挤在 64px 里
    :deep(.el-sub-menu__title > span) {
      display: none;
    }
  }
}
</style>
