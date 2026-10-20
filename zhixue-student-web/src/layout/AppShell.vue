<template>
  <div class="app-shell" :class="{ narrow: isNarrow }">
    <aside v-if="!isNarrow" class="sidebar">
      <SideNav />
    </aside>
    <el-drawer v-else v-model="drawerOpen" direction="ltr" size="280px" :with-header="false" class="nav-drawer">
      <SideNav @navigate="drawerOpen = false" />
    </el-drawer>

    <main class="main">
      <header v-if="isNarrow" class="topbar">
        <button type="button" class="icon-btn" aria-label="打开导航" @click="drawerOpen = true">
          <el-icon :size="20"><Expand /></el-icon>
        </button>
        <span class="topbar-title">智学云</span>
      </header>
      <div class="page">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Expand } from '@element-plus/icons-vue'
import SideNav from './SideNav.vue'
import { useMediaQuery } from '@/utils/media'

// 与设计文档一致：宽度 < 960px 时侧边栏收起为抽屉
const isNarrow = useMediaQuery('(max-width: 959px)')
const drawerOpen = ref(false)
const route = useRoute()

watch(() => route.fullPath, () => {
  drawerOpen.value = false
})
</script>

<style lang="scss" scoped>
.app-shell {
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  height: 100dvh;
  background: var(--zx-surface);

  &.narrow {
    grid-template-columns: minmax(0, 1fr);
  }
}

.sidebar {
  min-height: 0;
  border-right: 1px solid var(--el-border-color-lighter);
}

.main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 52px;
  flex-shrink: 0;
  padding: 0 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.topbar-title {
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.icon-btn {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-primary);
  cursor: pointer;

  &:hover {
    background: var(--el-fill-color);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>

<style lang="scss">
// 抽屉渲染在组件外，用全局样式去掉默认内边距
.nav-drawer .el-drawer__body {
  padding: 0;
}
</style>
