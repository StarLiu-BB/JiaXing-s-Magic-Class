<template>
  <div class="sidebar-logo" :class="{ collapse: isCollapse }">
    <router-link to="/" class="sidebar-logo-link" aria-label="智学云 首页">
      <BrandMark :size="30" />
      <transition name="logo-fade">
        <div v-if="!isCollapse" class="sidebar-logo-text">
          <span class="name">智学云</span>
          <span class="desc">运营后台</span>
        </div>
      </transition>
    </router-link>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useAppStore } from '@/stores/modules/app'
import BrandMark from '@/components/BrandMark/index.vue'

const appStore = useAppStore()
const isCollapse = computed(() => appStore.isCollapse)
</script>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.sidebar-logo {
  height: $navBarHeight;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  padding: 0 17px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  &.collapse {
    padding: 0;
    justify-content: center;
  }
}

.sidebar-logo-link {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  border-radius: 8px;
}

.sidebar-logo-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
  white-space: nowrap;

  .name {
    font-size: 16px;
    font-weight: 650;
    color: var(--el-text-color-primary);
    letter-spacing: 0.02em;
  }

  .desc {
    margin-top: 3px;
    font-size: 11px;
    color: var(--el-text-color-secondary);
    letter-spacing: 0.04em;
  }
}

.logo-fade-enter-active {
  transition: opacity 0.2s var(--zx-ease) 0.08s;
}

.logo-fade-enter-from,
.logo-fade-leave-to {
  opacity: 0;
}
</style>
