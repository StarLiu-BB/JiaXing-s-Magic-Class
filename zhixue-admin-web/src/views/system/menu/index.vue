<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>菜单管理</span>
          <el-button type="primary" @click="handleAdd">新增菜单</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" row-key="id" border default-expand-all>
        <el-table-column prop="menuName" label="菜单名称" />
        <el-table-column prop="icon" label="图标" width="80" />
        <el-table-column prop="orderNum" label="排序" width="80" />
        <el-table-column prop="path" label="路由地址" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200">
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
  { id: 1, menuName: '系统管理', icon: 'Setting', orderNum: 1, path: '/system', status: 1, children: [
    { id: 2, menuName: '用户管理', icon: 'User', orderNum: 1, path: 'user', status: 1 },
    { id: 3, menuName: '角色管理', icon: 'UserFilled', orderNum: 2, path: 'role', status: 1 },
    { id: 4, menuName: '菜单管理', icon: 'Menu', orderNum: 3, path: 'menu', status: 1 }
  ]}
])

const handleAdd = () => ElMessage.info('新增菜单功能开发中')
const handleEdit = (row) => ElMessage.info('编辑菜单功能开发中')
const handleDelete = (row) => {
  ElMessageBox.confirm('确定删除该菜单吗？', '提示', { type: 'warning' })
    .then(() => ElMessage.success('删除成功'))
    .catch(() => {})
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
