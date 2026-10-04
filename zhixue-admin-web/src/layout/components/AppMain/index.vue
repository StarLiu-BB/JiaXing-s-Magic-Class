<template>
  <main class="app-main">
    <router-view v-slot="{ Component }">
      <transition name="page" mode="out-in">
        <keep-alive :include="cachedViews">
          <component :is="Component" :key="key" />
        </keep-alive>
      </transition>
    </router-view>
  </main>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useTagsViewStore } from '@/stores/modules/tagsView'

const route = useRoute()
const tagsViewStore = useTagsViewStore()

const cachedViews = computed(() => tagsViewStore.cachedViews)
const key = computed(() => route.path)
</script>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.app-main {
  position: relative;
  width: 100%;
  min-height: calc(100vh - #{$headerHeight});
  padding: 20px 24px 32px;
}

// 页面切换：短促的淡入 + 轻微上移，原先是 0.5s 的左右横移
.page-enter-active {
  transition: opacity 0.18s var(--zx-ease), transform 0.18s var(--zx-ease);
}

.page-leave-active {
  transition: opacity 0.1s var(--zx-ease);
}

.page-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.page-leave-to {
  opacity: 0;
}
</style>
