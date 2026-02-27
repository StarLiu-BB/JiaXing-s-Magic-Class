<template>
  <div v-if="!item.meta || !item.meta.hidden" class="sidebar-item">
    <!-- 单个菜单项（无子菜单或只有一个子菜单） -->
    <template v-if="!item.children || item.children.length === 0">
      <app-link :to="resolvePath(item.path)">
        <el-menu-item :index="resolvePath(item.path)">
          <el-icon v-if="item.meta?.icon">
            <component :is="item.meta.icon" />
          </el-icon>
          <template #title>
            <span>{{ item.meta?.title }}</span>
          </template>
        </el-menu-item>
      </app-link>
    </template>

    <!-- 有子菜单 -->
    <el-sub-menu v-else :index="resolvePath(item.path)" popper-append-to-body>
      <template #title>
        <el-icon v-if="item.meta?.icon">
          <component :is="item.meta.icon" />
        </el-icon>
        <span>{{ item.meta?.title }}</span>
      </template>
      <app-link 
        v-for="child in item.children" 
        :key="child.path"
        :to="resolvePath(item.path + '/' + child.path)"
      >
        <el-menu-item :index="resolvePath(item.path + '/' + child.path)">
          <el-icon v-if="child.meta?.icon">
            <component :is="child.meta.icon" />
          </el-icon>
          <template #title>
            <span>{{ child.meta?.title }}</span>
          </template>
        </el-menu-item>
      </app-link>
    </el-sub-menu>
  </div>
</template>

<script setup>
import AppLink from './Link.vue'

const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  basePath: {
    type: String,
    default: ''
  }
})

// 解析路径
function resolvePath(routePath) {
  if (!routePath) return '/'
  if (routePath.startsWith('http')) return routePath
  return routePath.startsWith('/') ? routePath : `/${routePath}`
}
</script>

<style lang="scss" scoped>
.sidebar-item {
  :deep(.el-menu-item),
  :deep(.el-sub-menu__title) {
    i, .el-icon {
      margin-right: 8px;
      font-size: 18px;
    }
  }
}
</style>

