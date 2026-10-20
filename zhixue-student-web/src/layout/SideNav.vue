<template>
  <nav class="side-nav" aria-label="主导航">
    <div class="brand">
      <BrandMark :size="28" />
      <span>智学云</span>
    </div>

    <button type="button" class="new-chat" @click="newChat">
      <el-icon :size="16"><Plus /></el-icon>
      <span>新对话</span>
    </button>

    <!-- 二期上线课程库、我的课程、商城、订单、收藏后再显示入口，避免点进去是空页面 -->
    <div class="nav-scroll">
      <RecentList @navigate="emit('navigate')" />
    </div>

    <div class="user-footer">
      <el-dropdown trigger="click" placement="top-start" @command="onCommand">
        <button type="button" class="user-btn" aria-label="账号菜单">
          <span class="avatar" aria-hidden="true">{{ initial }}</span>
          <span class="user-name">{{ user.displayName }}</span>
          <el-icon class="user-more"><MoreFilled /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { MoreFilled, Plus, SwitchButton } from '@element-plus/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import RecentList from './RecentList.vue'
import { useConversationsStore } from '@/stores/conversations'
import { useUserStore } from '@/stores/user'
import { CHAT_DRAFT_KEY } from '@/utils/auth'

const emit = defineEmits(['navigate'])
const router = useRouter()
const user = useUserStore()
const conversations = useConversationsStore()
const initial = computed(() => user.displayName.slice(0, 1))

function newChat() {
  emit('navigate')
  router.push({ name: 'chat' })
}

async function onCommand(command) {
  if (command !== 'logout') {
    return
  }
  await user.logout()
  conversations.reset()
  sessionStorage.removeItem(CHAT_DRAFT_KEY)
  router.replace({ name: 'login' })
}
</script>

<style lang="scss" scoped>
.side-nav {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 14px 10px 10px;
  background: var(--el-fill-color-lighter);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 8px 14px;
  font-size: 16px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.new-chat {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--zx-surface);
  color: var(--el-text-color-primary);
  font: inherit;
  font-weight: 550;
  cursor: pointer;
  transition: border-color 0.16s var(--zx-ease);

  &:hover {
    border-color: var(--el-color-primary-light-5);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.nav-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin: 16px -4px 8px;
  padding: 0 4px;
}

.user-footer {
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);

  :deep(.el-dropdown) {
    width: 100%;
  }
}

.user-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  cursor: pointer;

  &:hover {
    background: var(--el-fill-color);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.user-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-more {
  color: var(--el-text-color-secondary);
}
</style>
