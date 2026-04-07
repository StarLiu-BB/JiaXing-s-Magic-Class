<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>菜单管理</span>
          <el-tooltip content="第一阶段暂不开放菜单编辑，支持查询与删除" placement="left">
            <el-button type="primary" disabled>新增菜单</el-button>
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
        <el-table-column prop="menuName" label="菜单名称" min-width="180" />
        <el-table-column prop="icon" label="图标" width="100" />
        <el-table-column prop="orderNum" label="排序" width="90" />
        <el-table-column prop="path" label="路由地址" min-width="200" />
        <el-table-column prop="perms" label="权限标识" min-width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-tooltip content="第一阶段暂不开放菜单编辑">
              <el-button type="primary" link disabled>编辑</el-button>
            </el-tooltip>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && tableData.length === 0 && !errorMessage"
        description="暂无菜单数据"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listMenu, deleteMenu } from '@/api/system/menu'

const loading = ref(false)
const tableData = ref([])
const errorMessage = ref('')

const normalizeMenuList = (response) => {
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response)) return response
  return []
}

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
    const response = await listMenu({})
    const list = normalizeMenuList(response)
    tableData.value = buildTree(list)
  } catch (error) {
    console.error('获取菜单列表失败:', error)
    tableData.value = []
    errorMessage.value = '菜单列表加载失败，请检查后端服务或稍后重试。'
  } finally {
    loading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该菜单吗？', '提示', { type: 'warning' })
    await deleteMenu(row.id)
    ElMessage.success('删除成功')
    await getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除菜单失败:', error)
    }
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
