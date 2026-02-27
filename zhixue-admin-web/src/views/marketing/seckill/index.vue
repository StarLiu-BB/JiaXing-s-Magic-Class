<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>秒杀管理</span>
          <el-button type="primary" @click="handleAdd">新增秒杀活动</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="活动名称" />
        <el-table-column prop="startTime" label="开始时间" width="180" />
        <el-table-column prop="endTime" label="结束时间" width="180" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '进行中' : '已结束' }}
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
  { id: 1, name: '双十一秒杀', startTime: '2024-11-11 00:00:00', endTime: '2024-11-11 23:59:59', status: 0 }
])

const handleAdd = () => ElMessage.info('新增秒杀活动功能开发中')
const handleEdit = (row) => ElMessage.info('编辑功能开发中')
const handleDelete = (row) => {
  ElMessageBox.confirm('确定删除该活动吗？', '提示', { type: 'warning' })
    .then(() => ElMessage.success('删除成功'))
    .catch(() => {})
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
