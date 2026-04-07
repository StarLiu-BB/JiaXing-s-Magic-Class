<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>秒杀管理</span>
          <el-button type="primary" @click="openCreateDialog">新增秒杀活动</el-button>
        </div>
      </template>

      <el-form :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="活动名称">
          <el-input v-model="queryParams.name" placeholder="请输入活动名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态">
            <el-option label="未开始" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已结束" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="name" label="活动名称" min-width="200" />
        <el-table-column prop="courseId" label="课程ID" width="110" />
        <el-table-column prop="seckillPrice" label="秒杀价" width="120">
          <template #default="{ row }">¥{{ Number(row.seckillPrice || 0) }}</template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="100" />
        <el-table-column label="活动时间" min-width="280">
          <template #default="{ row }">
            {{ formatPeriod(row.startTime, row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button type="warning" link @click="handlePreload(row)">预热库存</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList"
          @size-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="活动名称" prop="name">
          <el-input v-model="form.name" maxlength="60" show-word-limit />
        </el-form-item>
        <el-form-item label="课程ID" prop="courseId">
          <el-input-number v-model="form.courseId" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="秒杀价" prop="seckillPrice">
          <el-input-number v-model="form.seckillPrice" :min="0.01" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="库存" prop="stock">
          <el-input-number v-model="form.stock" :min="1" :step="10" />
        </el-form-item>
        <el-form-item label="活动时间" prop="timeRange">
          <el-date-picker
            v-model="timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="未开始" :value="0" />
            <el-option label="进行中" :value="1" />
            <el-option label="已结束" :value="2" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addSeckill,
  deleteSeckill,
  getSeckill,
  listSeckill,
  preloadSeckill,
  updateSeckill
} from '@/api/marketing/seckill'

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const total = ref(0)
const tableData = ref([])
const formRef = ref(null)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  name: '',
  status: undefined
})

const form = reactive({
  id: undefined,
  name: '',
  courseId: undefined,
  seckillPrice: 1,
  stock: 100,
  startTime: '',
  endTime: '',
  status: 0
})
const timeRange = ref([])

const rules = {
  name: [{ required: true, message: '请输入活动名称', trigger: 'blur' }],
  courseId: [{ required: true, message: '请输入课程ID', trigger: 'blur' }],
  seckillPrice: [{ required: true, message: '请输入秒杀价', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }]
}

const dialogTitle = computed(() => (form.id ? '编辑秒杀活动' : '新增秒杀活动'))

function resetForm() {
  Object.assign(form, {
    id: undefined,
    name: '',
    courseId: undefined,
    seckillPrice: 1,
    stock: 100,
    startTime: '',
    endTime: '',
    status: 0
  })
  timeRange.value = []
}

function normalizeRow(row) {
  return {
    ...row,
    id: row.id ?? row.activityId,
    status: Number(row.status ?? inferStatus(row.startTime, row.endTime))
  }
}

function inferStatus(startTime, endTime) {
  const now = Date.now()
  const start = startTime ? new Date(startTime).getTime() : 0
  const end = endTime ? new Date(endTime).getTime() : 0
  if (start && now < start) {
    return 0
  }
  if (end && now > end) {
    return 2
  }
  return 1
}

function getStatusType(status) {
  return status === 1 ? 'success' : status === 0 ? 'info' : 'danger'
}

function getStatusText(status) {
  return status === 1 ? '进行中' : status === 0 ? '未开始' : '已结束'
}

function formatPeriod(startTime, endTime) {
  if (!startTime || !endTime) {
    return '-'
  }
  return `${startTime} ~ ${endTime}`
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listSeckill(queryParams)
    let rows = (res.list || []).map(normalizeRow)
    if (queryParams.name) {
      const name = queryParams.name.toLowerCase()
      rows = rows.filter((item) => `${item.name || ''}`.toLowerCase().includes(name))
    }
    if (queryParams.status !== undefined && queryParams.status !== null) {
      rows = rows.filter((item) => Number(item.status) === Number(queryParams.status))
    }
    tableData.value = rows
    total.value = res.total || rows.length
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  queryParams.name = ''
  queryParams.status = undefined
  fetchList()
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

async function openEditDialog(row) {
  resetForm()
  try {
    const res = await getSeckill(row.id)
    const detail = normalizeRow(res?.data || row)
    Object.assign(form, detail)
    timeRange.value = [detail.startTime, detail.endTime].filter(Boolean)
    dialogVisible.value = true
  } catch (error) {
    console.error(error)
    ElMessage.error('读取活动详情失败')
  }
}

async function submitForm() {
  try {
    await formRef.value?.validate()
    if (timeRange.value.length !== 2) {
      ElMessage.warning('请选择活动时间')
      return
    }
    if (timeRange.value.length === 2) {
      form.startTime = timeRange.value[0]
      form.endTime = timeRange.value[1]
    }
    submitLoading.value = true
    if (form.id) {
      await updateSeckill({ ...form })
      ElMessage.success('活动更新成功')
    } else {
      await addSeckill({ ...form })
      ElMessage.success('活动创建成功')
    }
    dialogVisible.value = false
    fetchList()
  } catch (error) {
    if (error) {
      console.error(error)
    }
  } finally {
    submitLoading.value = false
  }
}

async function handlePreload(row) {
  try {
    await preloadSeckill(row.id)
    ElMessage.success('库存预热已触发')
  } catch (error) {
    console.error(error)
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除活动「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await deleteSeckill(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

fetchList()
</script>

<style scoped>
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.search-form {
  margin-bottom: 16px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
