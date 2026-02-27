<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>章节管理</span>
          <el-button type="primary" @click="handleAdd">新增章节</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" row-key="id" border default-expand-all>
        <el-table-column prop="title" label="章节名称" />
        <el-table-column prop="orderNum" label="排序" width="80" />
        <el-table-column prop="duration" label="时长" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '已发布' : '草稿' }}
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
  { id: 1, title: '第一章 入门', orderNum: 1, duration: '30分钟', status: 1, children: [
    { id: 2, title: '1.1 环境搭建', orderNum: 1, duration: '10分钟', status: 1 },
    { id: 3, title: '1.2 基础语法', orderNum: 2, duration: '20分钟', status: 1 }
  ]}
])

const handleAdd = () => ElMessage.info('新增章节功能开发中')
const handleEdit = (row) => ElMessage.info('编辑功能开发中')
const handleDelete = (row) => {
  ElMessageBox.confirm('确定删除该章节吗？', '提示', { type: 'warning' })
    .then(() => ElMessage.success('删除成功'))
    .catch(() => {})
}
</script>

<style scoped>
.card-header { display: flex; justify-content: space-between; align-items: center; }
</style>
