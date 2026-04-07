<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>字典管理</span>
          <el-button type="primary" disabled>新增字典</el-button>
        </div>
      </template>
      <el-alert
        title="当前版本未启用字典管理写操作，本页以只读方式展示基础字典。"
        type="warning"
        :closable="false"
        style="margin-bottom: 16px"
      />
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="dictName" label="字典名称" />
        <el-table-column prop="dictType" label="字典类型" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" />
        <el-table-column label="操作" width="200">
          <template #default="{ row }">
            <el-button type="primary" link disabled @click="handleReadonlyNotice">编辑</el-button>
            <el-button type="warning" link disabled @click="handleReadonlyNotice">字典数据</el-button>
            <el-button type="danger" link disabled @click="handleReadonlyNotice">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const tableData = ref([
  { id: 1, dictName: '用户状态', dictType: 'sys_user_status', status: 1, remark: '用户状态列表' },
  { id: 2, dictName: '通用状态', dictType: 'sys_normal_status', status: 1, remark: '通用状态列表' }
])

const handleReadonlyNotice = () => ElMessage.warning('当前版本未开放字典写操作')
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
