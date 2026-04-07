<template>
  <div class="sidebar-wrapper" :class="{ 'has-logo': showLogo }">
    <Logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :background-color="variables.menuBg"
        :text-color="variables.menuText"
        :unique-opened="true"
        :active-text-color="variables.menuActiveText"
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

// 菜单样式变量 - 现代简洁风格
const variables = {
  menuBg: '#1e1e2d',
  menuText: '#a2a3b7',
  menuActiveText: '#ffffff'
}

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
.sidebar-wrapper {
  height: 100%;
  
  :deep(.el-menu) {
    border-right: none;
    
    .el-menu-item,
    .el-sub-menu__title {
      height: 48px;
      line-height: 48px;
      font-size: 14px;
      
      &:hover {
        background-color: rgba(255, 255, 255, 0.05) !important;
      }
    }
    
    .el-menu-item.is-active {
      background: linear-gradient(90deg, #3699ff 0%, #1e1e2d 100%) !important;
      border-left: 3px solid #3699ff;
    }
    
    .el-sub-menu.is-active > .el-sub-menu__title {
      color: #ffffff !important;
    }
    
    .el-sub-menu .el-menu-item {
      padding-left: 50px !important;
      min-width: auto;
    }
  }
  
  :deep(.scrollbar-wrapper) {
    overflow-x: hidden !important;
  }
}
</style>
