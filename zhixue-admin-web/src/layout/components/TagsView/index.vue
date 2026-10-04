<template>
  <div class="tags-view-container">
    <scroll-pane ref="scrollPaneRef" class="tags-view-wrapper">
      <router-link
        v-for="tag in visitedViews"
        ref="tagRefs"
        :key="tag.path"
        :class="isActive(tag) ? 'active' : ''"
        :to="{ path: tag.path, query: tag.query, fullPath: tag.fullPath }"
        class="tags-view-item"
        @click.middle="!isAffix(tag) ? closeSelectedTag(tag) : ''"
        @contextmenu.prevent="openMenu(tag, $event)"
      >
        {{ tag.title }}
        <el-icon
          v-if="!isAffix(tag)"
          class="el-icon-close"
          @click.prevent.stop="closeSelectedTag(tag)"
        >
          <Close />
        </el-icon>
      </router-link>
    </scroll-pane>
    
    <!-- 右键菜单 -->
    <ul v-show="visible" :style="{ left: left + 'px', top: top + 'px' }" class="contextmenu">
      <li @click="refreshSelectedTag(selectedTag)">刷新</li>
      <li v-if="!isAffix(selectedTag)" @click="closeSelectedTag(selectedTag)">关闭</li>
      <li @click="closeOthersTags">关闭其他</li>
      <li @click="closeAllTags(selectedTag)">关闭所有</li>
    </ul>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTagsViewStore } from '@/stores/modules/tagsView'
import { Close } from '@element-plus/icons-vue'
import ScrollPane from './ScrollPane.vue'

const route = useRoute()
const router = useRouter()
const tagsViewStore = useTagsViewStore()

const scrollPaneRef = ref(null)
const tagRefs = ref([])
const visible = ref(false)
const top = ref(0)
const left = ref(0)
const selectedTag = ref({})

const visitedViews = computed(() => tagsViewStore.visitedViews)

// 判断是否为当前激活的标签
const isActive = (tag) => {
  return tag.path === route.path
}

// 判断是否为固定标签
const isAffix = (tag) => {
  return tag.meta && tag.meta.affix
}

// 打开右键菜单（菜单为 position: fixed，直接使用视口坐标，并防止溢出右边界）
const openMenu = (tag, e) => {
  const menuWidth = 128
  top.value = e.clientY + 4
  left.value = Math.min(e.clientX, window.innerWidth - menuWidth - 8)
  visible.value = true
  selectedTag.value = tag
}

// 关闭右键菜单
const closeMenu = () => {
  visible.value = false
}

// 刷新选中的标签
const refreshSelectedTag = (view) => {
  router.replace({ path: '/redirect' + view.fullPath })
}

// 关闭选中的标签
const closeSelectedTag = (view) => {
  tagsViewStore.delVisitedView(view).then((visitedViews) => {
    if (isActive(view)) {
      toLastView(visitedViews, view)
    }
  })
}

// 关闭其他标签
const closeOthersTags = () => {
  router.push(selectedTag.value)
  tagsViewStore.delOthersVisitedViews(selectedTag.value).then(() => {
    moveToCurrentTag()
  })
}

// 关闭所有标签
const closeAllTags = (view) => {
  tagsViewStore.delAllVisitedViews().then((visitedViews) => {
    if (visitedViews.findIndex((item) => item.path === view.path) === -1) {
      toLastView(visitedViews)
    }
  })
}

// 跳转到最后一个视图
const toLastView = (visitedViews, view) => {
  const latestView = visitedViews.slice(-1)[0]
  if (latestView) {
    router.push(latestView.fullPath)
  } else {
    // 如果标签被全部关闭，跳转到首页
    if (view.name === 'Dashboard') {
      router.replace({ path: '/redirect' + view.fullPath })
    } else {
      router.push('/')
    }
  }
}

// 移动到当前标签
const moveToCurrentTag = () => {
  nextTick(() => {
    for (const tag of tagRefs.value) {
      if (tag.to.path === route.path) {
        scrollPaneRef.value.moveToTarget(tag.$el)
        break
      }
    }
  })
}

// 监听路由变化
watch(route, () => {
  addView()
  moveToCurrentTag()
})

// 添加视图
const addView = () => {
  const { name } = route
  if (name) {
    tagsViewStore.addVisitedView(route)
    tagsViewStore.addCachedView(route)
  }
  return false
}

// 监听点击事件，关闭右键菜单
watch(visible, (value) => {
  if (value) {
    document.body.addEventListener('click', closeMenu)
  } else {
    document.body.removeEventListener('click', closeMenu)
  }
})

// 初始化
addView()
</script>

<style lang="scss" scoped>
@use '@/styles/variables' as *;

.tags-view-container {
  height: $tagsViewHeight;
  width: 100%;
  background: var(--zx-surface);
  border-bottom: 1px solid var(--el-border-color-light);

  .tags-view-wrapper {
    height: 100%;

    :deep(.scroll-wrapper) {
      display: flex;
      align-items: center;
      height: $tagsViewHeight;
      gap: 4px;
      padding: 0 12px;
    }

    .tags-view-item {
      display: inline-flex;
      align-items: center;
      gap: 4px;
      flex-shrink: 0;
      height: 28px;
      padding: 0 10px;
      border-radius: 6px;
      font-size: 12.5px;
      color: var(--el-text-color-secondary);
      cursor: pointer;
      transition: background-color 0.16s var(--zx-ease), color 0.16s var(--zx-ease);

      &:hover {
        background: var(--el-fill-color-light);
        color: var(--el-text-color-primary);
      }

      &.active {
        background: var(--el-color-primary-light-9);
        color: var(--el-color-primary);
        font-weight: 600;
      }

      .el-icon-close {
        width: 16px;
        height: 16px;
        margin-right: -4px;
        padding: 3px;
        border-radius: 4px;
        color: inherit;
        opacity: 0.6;
        transition: background-color 0.16s var(--zx-ease), opacity 0.16s var(--zx-ease);

        &:hover {
          opacity: 1;
          background: color-mix(in srgb, currentColor 14%, transparent);
        }
      }
    }
  }

  .contextmenu {
    position: fixed;
    z-index: 3000;
    min-width: 128px;
    margin: 0;
    padding: 4px;
    list-style: none;
    background: var(--zx-surface);
    border: 1px solid var(--el-border-color-light);
    border-radius: 8px;
    box-shadow: var(--el-box-shadow-light);
    font-size: 13px;
    color: var(--el-text-color-regular);

    li {
      padding: 6px 12px;
      border-radius: 6px;
      cursor: pointer;

      &:hover {
        background: var(--el-fill-color-light);
        color: var(--el-text-color-primary);
      }
    }
  }
}
</style>
