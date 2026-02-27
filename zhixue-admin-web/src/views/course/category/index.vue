<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>分类管理</span>
          <el-button type="primary" @click="handleAdd">新增分类</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" row-key="id" border default-expand-all>
        <el-table-column prop="name" label="分类名称" />
        <el-table-column prop="orderNum" label="排序" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const tableData = ref([
  { id: 1, name: '编程开发', orderNum: 1, status: 1, children: [
    { id: 2, name: 'Java', orderNum: 1, status: 1 },
    { id: 3, name: 'Python', orderNum: 2, status: 1 },
    { id: 4, name: '前端开发', orderNum: 3, status: 1 }
  ]},
  { id: 5, name: '人工智能', orderNum: 2, status: 1 }
])

const handleAdd = () => ElMessage.info('新增分类功能开发中')
const handleEdit = (row) => ElMessage.info('编辑功能开发中')
const handleDelete = (row) => {
  ElMessageBox.confirm('确定删除该分类吗？', '提示', { type: 'warning' })
    .then(() => ElMessage.success('删除成功'))
    .catch(() => {})
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
