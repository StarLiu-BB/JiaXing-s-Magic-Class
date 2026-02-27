<template>
  <div :class="classObj" class="app-wrapper">
    <!-- 移动端遮罩层 -->
    <div v-if="device === 'mobile' && sidebar.opened" class="drawer-bg" @click="handleClickOutside" />
    
    <!-- 侧边栏 -->
    <Sidebar class="sidebar-container" />
    
    <!-- 主容器 -->
    <div class="main-container">
      <!-- 导航栏 -->
      <div :class="{ 'fixed-header': fixedHeader }">
        <Navbar />
      </div>
      
      <!-- 标签视图 -->
      <TagsView v-if="showTagsView" />
      
      <!-- 主内容区 -->
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

// 计算属性
const classObj = computed(() => ({
  hideSidebar: !appStore.sidebar.opened,
  openSidebar: appStore.sidebar.opened,
  withoutAnimation: appStore.sidebar.withoutAnimation,
  mobile: appStore.device === 'mobile'
}))

const device = computed(() => appStore.device)
const sidebar = computed(() => appStore.sidebar)
const fixedHeader = computed(() => true) // 固定头部
const showTagsView = computed(() => true) // 显示标签视图

// 移动端点击遮罩层关闭侧边栏
const handleClickOutside = () => {
  appStore.closeSidebar(false)
}
</script>

<style lang="scss" scoped>
@import '@/styles/variables.scss';

.app-wrapper {
  position: relative;
  height: 100%;
  width: 100%;
  
  &.mobile.openSidebar {
    position: fixed;
    top: 0;
  }
}

.drawer-bg {
  background: rgba(0, 0, 0, 0.3);
  width: 100%;
  top: 0;
  height: 100%;
  position: absolute;
  z-index: 999;
}

.fixed-header {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 9;
  width: calc(100% - #{$sideBarWidth});
  transition: width 0.28s;
}

.hideSidebar .fixed-header {
  width: calc(100% - #{$sideBarCollapseWidth});
}

.mobile .fixed-header {
  width: 100%;
}

.sidebar-container {
  position: fixed;
  top: 0;
  bottom: 0;
  left: 0;
  z-index: 1001;
  height: 100%;
  transition: width 0.28s;
  width: $sideBarWidth !important;
  background-color: $menuBg;
  overflow: hidden;
}

.hideSidebar .sidebar-container {
  width: $sideBarCollapseWidth !important;
}

.main-container {
  min-height: 100%;
  transition: margin-left 0.28s;
  margin-left: $sideBarWidth;
  position: relative;
}

.hideSidebar .main-container {
  margin-left: $sideBarCollapseWidth;
}

.mobile .main-container {
  margin-left: 0px;
}
</style>

