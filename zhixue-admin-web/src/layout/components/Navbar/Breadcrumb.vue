<template>
  <el-breadcrumb class="app-breadcrumb" separator="/">
    <transition-group name="breadcrumb">
      <el-breadcrumb-item v-for="(item, index) in levelList" :key="item.path">
        <span
          v-if="item.redirect === 'noRedirect' || index == levelList.length - 1"
          class="no-redirect"
        >{{ item.meta.title }}</span>
        <a v-else @click.prevent="handleLink(item)">{{ item.meta.title }}</a>
      </el-breadcrumb-item>
    </transition-group>
  </el-breadcrumb>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const levelList = ref([])

// 获取面包屑列表
const getBreadcrumb = () => {
  // 过滤掉没有 title 的路由
  let matched = route.matched.filter(item => item.meta && item.meta.title)
  const first = matched[0]

  if (!isDashboard(first)) {
    matched = [{ path: '/dashboard', meta: { title: '首页' } }].concat(matched)
  }

  levelList.value = matched.filter(item => item.meta && item.meta.title && item.meta.breadcrumb !== false)
}

// 判断是否为首页
const isDashboard = (route) => {
  const name = route && route.name
  if (!name) {
    return false
  }
  return name.trim().toLocaleLowerCase() === 'Dashboard'.toLocaleLowerCase()
}

// 处理链接点击
const handleLink = (item) => {
  const { redirect, path } = item
  if (redirect) {
    router.push(redirect)
    return
  }
  router.push(path)
}

// 监听路由变化
watch(route, getBreadcrumb, { immediate: true })
</script>

<style lang="scss" scoped>
.app-breadcrumb.el-breadcrumb {
  display: inline-flex;
  align-items: center;
  font-size: 13px;
  white-space: nowrap;

  :deep(.el-breadcrumb__separator) {
    color: var(--el-text-color-placeholder);
    margin: 0 8px;
  }

  a {
    color: var(--el-text-color-secondary);
    font-weight: 400;
    cursor: pointer;
    transition: color 0.16s var(--zx-ease);

    &:hover {
      color: var(--el-color-primary);
    }
  }

  // 当前页：最深、最重；父级只作路径提示
  .no-redirect {
    color: var(--el-text-color-secondary);
    cursor: text;
  }

  :deep(.el-breadcrumb__item:last-child) .no-redirect {
    color: var(--el-text-color-primary);
    font-weight: 600;
  }
}

.breadcrumb-enter-active {
  transition: opacity 0.2s var(--zx-ease);
}

.breadcrumb-enter-from,
.breadcrumb-leave-active {
  opacity: 0;
}

.breadcrumb-leave-active {
  position: absolute;
}
</style>

