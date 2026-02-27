<template>
  <div class="sidebar-logo-container" :class="{ collapse: isCollapse }">
    <router-link to="/" class="sidebar-logo-link">
      <div class="logo-icon">
        <el-icon :size="isCollapse ? 24 : 28"><Reading /></el-icon>
      </div>
      <transition name="fade">
        <span v-if="!isCollapse" class="sidebar-title">{{ title }}</span>
      </transition>
    </router-link>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/modules/app'
import { Reading } from '@element-plus/icons-vue'

const appStore = useAppStore()

const isCollapse = computed(() => appStore.isCollapse)
const title = computed(() => '智学云')
</script>

<style lang="scss" scoped>
.sidebar-logo-container {
  position: relative;
  width: 100%;
  height: 60px;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  overflow: hidden;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);

  .sidebar-logo-link {
    height: 100%;
    width: 100%;
    display: flex;
    align-items: center;
    padding: 0 16px;
    text-decoration: none;
    
    .logo-icon {
      width: 40px;
      height: 40px;
      background: linear-gradient(135deg, #3699ff 0%, #0073e6 100%);
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #fff;
      flex-shrink: 0;
      box-shadow: 0 4px 12px rgba(54, 153, 255, 0.3);
    }

    .sidebar-title {
      margin-left: 12px;
      color: #fff;
      font-weight: 600;
      font-size: 18px;
      letter-spacing: 1px;
      white-space: nowrap;
    }
  }

  &.collapse {
    .sidebar-logo-link {
      justify-content: center;
      padding: 0;
    }
    
    .logo-icon {
      width: 36px;
      height: 36px;
    }
  }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

