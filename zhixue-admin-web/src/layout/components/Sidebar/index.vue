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
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
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

// 获取用户角色
const userRoles = computed(() => userStore.roles || ['ADMIN'])

// 检查是否有角色权限
const hasRole = (roles) => {
  if (!roles || roles.length === 0) return true
  return roles.some(role => userRoles.value.includes(role))
}

// 完整菜单配置（带角色权限）
const allMenus = [
  // 首页 - 所有角色可见
  {
    path: '/dashboard',
    meta: { title: '首页', icon: 'HomeFilled' }
  },
  // AI 助手 - 所有角色可见
  {
    path: '/ai',
    meta: { title: 'AI 助手', icon: 'MagicStick' },
    children: [
      { path: 'chat', meta: { title: '智能对话', icon: 'ChatDotRound' } }
    ]
  },
  // 课程管理 - 管理员和教师可见
  {
    path: '/course',
    meta: { title: '课程管理', icon: 'VideoPlay', roles: ['ADMIN', 'TEACHER'] },
    children: [
      { path: 'list', meta: { title: '课程列表', icon: 'List' } },
      { path: 'category', meta: { title: '分类管理', icon: 'Grid', roles: ['ADMIN'] } },
      { path: 'chapter', meta: { title: '章节管理', icon: 'Folder' } }
    ]
  },
  // 学生互动 - 教师可见
  {
    path: '/interaction',
    meta: { title: '学生互动', icon: 'ChatLineSquare', roles: ['ADMIN', 'TEACHER'] },
    children: [
      { path: 'question', meta: { title: '问答管理', icon: 'QuestionFilled' } },
      { path: 'comment', meta: { title: '评论管理', icon: 'Comment' } }
    ]
  },
  // 营销管理 - 仅管理员可见
  {
    path: '/marketing',
    meta: { title: '营销中心', icon: 'TrendCharts', roles: ['ADMIN'] },
    children: [
      { path: 'seckill', meta: { title: '秒杀活动', icon: 'Timer' } },
      { path: 'coupon', meta: { title: '优惠券', icon: 'Ticket' } }
    ]
  },
  // 系统管理 - 仅管理员可见
  {
    path: '/system',
    meta: { title: '系统设置', icon: 'Setting', roles: ['ADMIN'] },
    children: [
      { path: 'user', meta: { title: '用户管理', icon: 'User' } },
      { path: 'role', meta: { title: '角色管理', icon: 'Avatar' } },
      { path: 'menu', meta: { title: '菜单管理', icon: 'Menu' } },
      { path: 'dict', meta: { title: '字典管理', icon: 'Collection' } }
    ]
  }
]

// 根据角色过滤菜单
const filterMenusByRole = (menus) => {
  return menus.filter(menu => {
    // 检查菜单本身的角色权限
    if (!hasRole(menu.meta?.roles)) return false
    // 过滤子菜单
    if (menu.children) {
      menu.children = menu.children.filter(child => hasRole(child.meta?.roles))
    }
    return true
  })
}

// 根据角色过滤后的菜单
const menuRoutes = computed(() => {
  return filterMenusByRole(JSON.parse(JSON.stringify(allMenus)))
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

