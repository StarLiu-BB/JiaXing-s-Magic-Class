<template>
  <div class="navbar">
    <div class="navbar-left">
      <button
        type="button"
        class="icon-button"
        :aria-label="isCollapse ? '展开侧边栏' : '收起侧边栏'"
        :title="isCollapse ? '展开侧边栏' : '收起侧边栏'"
        @click="toggleSideBar"
      >
        <el-icon :size="18"><Expand v-if="isCollapse" /><Fold v-else /></el-icon>
      </button>
      <span class="divider" aria-hidden="true" />
      <Breadcrumb />
    </div>

    <div class="navbar-right">
      <Screenfull class="icon-button" />

      <el-dropdown trigger="click" placement="bottom-end" @command="handleCommand">
        <button type="button" class="user-trigger" aria-label="账号菜单">
          <img v-if="avatarUrl" :src="avatarUrl" class="avatar" alt="" />
          <span v-else class="avatar avatar-initial" aria-hidden="true">{{ initial }}</span>
          <span class="user-name">{{ displayName }}</span>
          <el-icon class="caret" :size="12"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu class="user-menu">
            <div class="user-menu-head">
              <div class="user-menu-name">{{ displayName }}</div>
              <div v-if="username && username !== displayName" class="user-menu-sub">{{ username }}</div>
            </div>
            <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Fold, Expand, ArrowDown, SwitchButton } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/modules/app'
import { useUserStore } from '@/stores/modules/user'
import Breadcrumb from './Breadcrumb.vue'
import Screenfull from './Screenfull.vue'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const isCollapse = computed(() => appStore.isCollapse)
const username = computed(() => userStore.name)
const displayName = computed(() => userStore.userInfo?.nickname || userStore.name || '未命名用户')
const initial = computed(() => Array.from(displayName.value)[0] || '?')
// 只展示站内或 https 头像；原先的默认头像是第三方图床的外链 GIF
const avatarUrl = computed(() => {
  const url = userStore.avatar
  return url && (url.startsWith('/') || url.startsWith('https://')) ? url : ''
})

const toggleSideBar = () => {
  appStore.toggleSidebar()
}

const handleCommand = (command) => {
  if (command === 'logout') logout()
}

const logout = () => {
  ElMessageBox.confirm('退出后需要重新输入账号和验证码。', '退出登录', {
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    await userStore.logout()
    router.push('/login')
    ElMessage.success('已退出登录')
  }).catch(() => {})
}
</script>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.navbar {
  height: $navBarHeight;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 12px;
  background: var(--zx-surface);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.navbar-left,
.navbar-right {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.divider {
  width: 1px;
  height: 18px;
  margin: 0 8px 0 4px;
  background: var(--el-border-color);
}

.icon-button {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition: background-color 0.16s var(--zx-ease), color 0.16s var(--zx-ease);

  &:hover {
    background: var(--el-fill-color-light);
    color: var(--el-text-color-primary);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 38px;
  padding: 0 8px 0 4px;
  margin-left: 4px;
  border: none;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  font: inherit;
  color: var(--el-text-color-primary);
  transition: background-color 0.16s var(--zx-ease);

  &:hover {
    background: var(--el-fill-color-light);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  object-fit: cover;
  flex-shrink: 0;
}

.avatar-initial {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 13px;
  font-weight: 600;
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  font-weight: 500;
}

.caret {
  color: var(--el-text-color-secondary);
}

.user-menu-head {
  padding: 8px 16px 10px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  min-width: 168px;
}

.user-menu-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.user-menu-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 640px) {
  .user-name {
    display: none;
  }
}
</style>
