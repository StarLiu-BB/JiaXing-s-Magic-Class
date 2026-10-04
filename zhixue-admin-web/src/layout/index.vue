<template>
  <div :class="classObj" class="app-wrapper">
    <!-- 移动端遮罩层 -->
    <div v-if="device === 'mobile' && sidebar.opened" class="drawer-bg" @click="handleClickOutside" />

    <Sidebar class="sidebar-container" />

    <div class="main-container">
      <!-- 顶栏与标签栏一起吸顶：原先只固定了顶栏，标签栏整条被盖在顶栏下面 -->
      <header class="app-header">
        <Navbar />
        <TagsView v-if="showTagsView" />
      </header>

      <AppMain />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/modules/app'
import Sidebar from './components/Sidebar/index.vue'
import Navbar from './components/Navbar/index.vue'
import TagsView from './components/TagsView/index.vue'
import AppMain from './components/AppMain/index.vue'

const appStore = useAppStore()

const classObj = computed(() => ({
  hideSidebar: !appStore.sidebar.opened,
  openSidebar: appStore.sidebar.opened,
  withoutAnimation: appStore.sidebar.withoutAnimation,
  mobile: appStore.device === 'mobile'
}))

const device = computed(() => appStore.device)
const sidebar = computed(() => appStore.sidebar)
const showTagsView = computed(() => true)

// 移动端点击遮罩层关闭侧边栏
const handleClickOutside = () => {
  appStore.closeSidebar(false)
}
</script>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.app-wrapper {
  position: relative;
  min-height: 100%;
  width: 100%;

  &.mobile.openSidebar {
    position: fixed;
    top: 0;
  }
}

.drawer-bg {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: var(--el-overlay-color-lighter);
}

.sidebar-container {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 1001;
  width: $sideBarWidth;
  overflow: hidden;
  background: var(--zx-surface);
  border-right: 1px solid var(--el-border-color-light);
  transition: width 0.24s var(--zx-ease), transform 0.24s var(--zx-ease);
}

.main-container {
  position: relative;
  min-height: 100vh;
  margin-left: $sideBarWidth;
  background: var(--zx-canvas);
  transition: margin-left 0.24s var(--zx-ease);
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 20;
}

.hideSidebar {
  .sidebar-container {
    width: $sideBarCollapseWidth;
  }

  .main-container {
    margin-left: $sideBarCollapseWidth;
  }
}

.mobile {
  .main-container {
    margin-left: 0;
  }

  .sidebar-container {
    width: $sideBarWidth;
  }

  &.hideSidebar .sidebar-container {
    transform: translateX(-$sideBarWidth);
  }
}

.withoutAnimation {
  .sidebar-container,
  .main-container {
    transition: none;
  }
}
</style>
