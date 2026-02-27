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

// 打开右键菜单
const openMenu = (tag, e) => {
  const menuMinWidth = 105
  const offsetLeft = scrollPaneRef.value.$el.getBoundingClientRect().left // container margin left
  const offsetWidth = scrollPaneRef.value.$el.offsetWidth // container width
  const maxLeft = offsetWidth - menuMinWidth // left boundary
  const left = e.clientX - offsetLeft + 15 // 15: margin right

  if (left > maxLeft) {
    left = maxLeft
  }

  top.value = e.clientY
  left.value = left
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
.tags-view-container {
  height: 34px;
  width: 100%;
  background: #fff;
  border-bottom: 1px solid #d8dce5;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.12), 0 0 3px 0 rgba(0, 0, 0, 0.04);
  
  .tags-view-wrapper {
    .tags-view-item {
      display: inline-block;
      position: relative;
      cursor: pointer;
      height: 26px;
      line-height: 26px;
      border: 1px solid #d8dce5;
      color: #495060;
      background: #fff;
      padding: 0 8px;
      font-size: 12px;
      margin-left: 5px;
      margin-top: 4px;
      
      &:first-of-type {
        margin-left: 15px;
      }
      
      &:last-of-type {
        margin-right: 15px;
      }
      
      &.active {
        background-color: #42b983;
        color: #fff;
        border-color: #42b983;
        
        &::before {
          content: '';
          background: #fff;
          display: inline-block;
          width: 8px;
          height: 8px;
          border-radius: 50%;
          position: relative;
          margin-right: 2px;
        }
      }
      
      .el-icon-close {
        width: 16px;
        height: 16px;
        vertical-align: 2px;
        border-radius: 50%;
        text-align: center;
        transition: all 0.3s cubic-bezier(0.645, 0.045, 0.355, 1);
        transform-origin: 100% 50%;
        
        &:before {
          transform: scale(0.6);
          display: inline-block;
          vertical-align: -3px;
        }
        
        &:hover {
          background-color: #b4bccc;
          color: #fff;
        }
      }
    }
  }
  
  .contextmenu {
    margin: 0;
    background: #fff;
    z-index: 3000;
    position: absolute;
    list-style-type: none;
    padding: 5px 0;
    border-radius: 4px;
    font-size: 12px;
    font-weight: 400;
    color: #333;
    box-shadow: 2px 2px 3px 0 rgba(0, 0, 0, 0.3);
    
    li {
      margin: 0;
      padding: 7px 16px;
      cursor: pointer;
      
      &:hover {
        background: #eee;
      }
    }
  }
}
</style>

