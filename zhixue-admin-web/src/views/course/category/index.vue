<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>分类管理</span>
          <el-tooltip content="第一阶段暂不开放分类编辑，支持查询" placement="left">
            <el-button type="primary" disabled>新增分类</el-button>
          </el-tooltip>
        </div>
      </template>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
        class="state-alert"
      />

      <el-table
        :data="tableData"
        v-loading="loading"
        row-key="id"
        border
        default-expand-all
      >
        <el-table-column prop="name" label="分类名称" min-width="220" />
        <el-table-column prop="sort" label="排序" width="90" />
        <el-table-column label="操作" width="150">
          <template #default>
            <el-tooltip content="第一阶段暂不开放分类编辑">
              <el-button type="primary" link disabled>编辑</el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && tableData.length === 0 && !errorMessage"
        description="暂无分类数据"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getCategoryList } from '@/api/course/course'

const loading = ref(false)
const tableData = ref([])
const errorMessage = ref('')

const buildTree = (items) => {
  const map = new Map()
  const roots = []
  items.forEach(item => {
    map.set(item.id, { ...item, children: [] })
  })
  map.forEach(item => {
    const parentId = item.parentId || 0
    if (parentId === 0 || !map.has(parentId)) {
      roots.push(item)
    } else {
      map.get(parentId).children.push(item)
    }
  })
  return roots
}

const getList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await getCategoryList()
    const list = Array.isArray(response?.data)
      ? response.data
      : Array.isArray(response)
        ? response
        : []
    tableData.value = buildTree(list)
  } catch (error) {
    console.error('获取分类列表失败:', error)
    tableData.value = []
    errorMessage.value = '分类列表加载失败，请检查后端服务或稍后重试。'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.state-alert {
  margin-bottom: 12px;
}
</style>
