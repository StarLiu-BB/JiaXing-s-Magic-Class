<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>角色管理</span>
          <el-tooltip content="第一阶段暂不开放角色编辑，支持查询与删除" placement="left">
            <el-button type="primary" disabled>新增角色</el-button>
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

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="roleName" label="角色名称" min-width="160" />
        <el-table-column prop="roleKey" label="角色标识" min-width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'success' : 'danger'">
              {{ row.status === 0 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-tooltip content="第一阶段暂不开放角色编辑">
              <el-button type="primary" link disabled>编辑</el-button>
            </el-tooltip>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && tableData.length === 0 && !errorMessage"
        description="暂无角色数据"
      />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRole, deleteRole } from '@/api/system/role'

const loading = ref(false)
const tableData = ref([])
const errorMessage = ref('')

const normalizeRoleList = (response) => {
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.records)) return response.records
  if (Array.isArray(response)) return response
  return []
}

const getList = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listRole({})
    tableData.value = normalizeRoleList(response)
  } catch (error) {
    console.error('获取角色列表失败:', error)
    tableData.value = []
    errorMessage.value = '角色列表加载失败，请检查后端服务或稍后重试。'
  } finally {
    loading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该角色吗？', '提示', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    await getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除角色失败:', error)
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
