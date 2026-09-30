<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>秒杀管理</span>
          <el-button v-hasPermi="['marketing:seckill:edit']" type="primary" @click="openCreateDialog">新增秒杀活动</el-button>
        </div>
      </template>

      <el-form :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="活动标题">
          <el-input v-model="queryParams.title" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option v-for="(item, key) in STATUS" :key="key" :label="item.text" :value="Number(key)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="title" label="活动标题" min-width="180" />
        <el-table-column prop="productId" label="课程ID" width="100" />
        <el-table-column label="秒杀价" width="110">
          <template #default="{ row }">¥{{ Number(row.seckillPrice || 0).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="totalStock" label="总库存" width="90" />
        <el-table-column label="活动时间" min-width="300">
          <template #default="{ row }">{{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusOf(row).tag">{{ statusOf(row).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openStatistics(row)">统计</el-button>
            <span v-hasPermi="['marketing:seckill:edit']">
              <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
              <el-button v-if="Number(row.status) !== 1" type="success" link @click="handleStatus(row, 1)">上线</el-button>
              <el-button v-else type="warning" link @click="handleStatus(row, 0)">下线</el-button>
              <el-button type="warning" link @click="handlePreload(row)">装载库存</el-button>
              <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
            </span>
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
          @size-change="handleQuery"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑秒杀活动' : '新增秒杀活动'" width="620px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="活动标题" prop="title">
          <el-input v-model="form.title" maxlength="60" show-word-limit />
        </el-form-item>
        <el-form-item label="课程ID" prop="productId">
          <el-input-number v-model="form.productId" :min="1" :step="1" :controls="false" style="width: 200px" />
          <span class="form-tip">须为已发布且已上架的课程，否则用户买到后无法观看</span>
        </el-form-item>
        <el-form-item label="秒杀价" prop="seckillPrice">
          <el-input-number v-model="form.seckillPrice" :min="0.01" :precision="2" :step="1" />
        </el-form-item>
        <el-form-item label="总库存" prop="totalStock">
          <el-input-number v-model="form.totalStock" :min="1" :step="10" />
        </el-form-item>
        <el-form-item label="活动时间" prop="timeRange">
          <el-date-picker
            v-model="form.timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            format="YYYY-MM-DD HH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="form.status" style="width: 100%">
            <el-option v-for="(item, key) in STATUS" :key="key" :label="item.text" :value="Number(key)" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statVisible" title="活动统计" width="460px">
      <el-descriptions v-if="stat" :column="1" border>
        <el-descriptions-item label="活动">{{ stat.title }}</el-descriptions-item>
        <el-descriptions-item label="总库存">{{ stat.totalStock }}</el-descriptions-item>
        <el-descriptions-item label="成交记录">{{ stat.totalOrderCount }}</el-descriptions-item>
        <el-descriptions-item label="已下单">{{ stat.createdCount }}</el-descriptions-item>
        <el-descriptions-item label="已取消">{{ stat.canceledCount }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addSeckill,
  changeSeckillStatus,
  deleteSeckill,
  getSeckill,
  getSeckillStatistics,
  listSeckill,
  preloadSeckill,
  updateSeckill
} from '@/api/marketing/seckill'

// C 端只展示"进行中"的活动
const STATUS = {
  0: { text: '未开始', tag: 'info' },
  1: { text: '进行中', tag: 'success' },
  2: { text: '已结束', tag: 'danger' }
}

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const statVisible = ref(false)
const stat = ref(null)
const total = ref(0)
const tableData = ref([])
const formRef = ref(null)

const queryParams = reactive({ pageNum: 1, pageSize: 10, title: '', status: undefined })

const emptyForm = () => ({
  id: undefined,
  title: '',
  productId: undefined,
  seckillPrice: 1,
  totalStock: 100,
  timeRange: [],
  status: 0
})
const form = reactive(emptyForm())

const rules = {
  title: [{ required: true, message: '请输入活动标题', trigger: 'blur' }],
  productId: [{ required: true, message: '请输入课程ID', trigger: 'blur' }],
  seckillPrice: [{ required: true, message: '请输入秒杀价', trigger: 'blur' }],
  totalStock: [{ required: true, message: '请输入总库存', trigger: 'blur' }],
  timeRange: [{ type: 'array', len: 2, required: true, message: '请选择活动时间', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

function statusOf(row) {
  return STATUS[Number(row?.status)] || { text: '未知', tag: 'info' }
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '-'
}

/** 后端 LocalDateTime 可能带毫秒，日期选择器按秒绑定 */
function toPickerValue(value) {
  return value ? String(value).split('.')[0] : ''
}

async function fetchList() {
  loading.value = true
  try {
    const query = { pageNum: queryParams.pageNum, pageSize: queryParams.pageSize }
    if (queryParams.title) query.title = queryParams.title.trim()
    if (queryParams.status !== undefined && queryParams.status !== null && queryParams.status !== '') {
      query.status = queryParams.status
    }
    const res = await listSeckill(query)
    tableData.value = res.list
    total.value = res.total
  } catch (error) {
    tableData.value = []
    total.value = 0
    console.error(error)
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  fetchList()
}

function resetQuery() {
  Object.assign(queryParams, { pageNum: 1, pageSize: 10, title: '', status: undefined })
  fetchList()
}

function openCreateDialog() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function openEditDialog(row) {
  try {
    const res = await getSeckill(row.id)
    const detail = res?.data || row
    Object.assign(form, emptyForm(), {
      id: detail.id,
      title: detail.title,
      productId: Number(detail.productId),
      seckillPrice: Number(detail.seckillPrice),
      totalStock: Number(detail.totalStock),
      timeRange: [toPickerValue(detail.startTime), toPickerValue(detail.endTime)],
      status: Number(detail.status)
    })
    dialogVisible.value = true
  } catch (error) {
    console.error(error)
  }
}

async function submitForm() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  const payload = {
    id: form.id,
    title: form.title,
    productId: form.productId,
    seckillPrice: form.seckillPrice,
    totalStock: form.totalStock,
    startTime: form.timeRange[0],
    endTime: form.timeRange[1],
    status: form.status
  }
  submitLoading.value = true
  try {
    if (payload.id) {
      await updateSeckill(payload)
      ElMessage.success('活动已更新')
    } else {
      await addSeckill(payload)
      ElMessage.success('活动已创建')
    }
    dialogVisible.value = false
    fetchList()
  } catch (error) {
    console.error(error)
  } finally {
    submitLoading.value = false
  }
}

async function handleStatus(row, status) {
  const action = status === 1 ? '上线' : '下线'
  try {
    await ElMessageBox.confirm(
      status === 1 ? `上线后用户即可在小程序看到并参与「${row.title}」。` : `下线后用户将无法再参与「${row.title}」。`,
      `确认${action}`,
      { type: 'warning' }
    )
    await changeSeckillStatus(row.id, status)
    ElMessage.success(`已${action}`)
    fetchList()
  } catch (error) {
    if (error !== 'cancel') console.error(error)
  }
}

async function handlePreload(row) {
  try {
    await preloadSeckill(row.id)
    ElMessage.success('库存已装载（按总库存减已成交；已装载时不覆盖）')
  } catch (error) {
    console.error(error)
  }
}

async function openStatistics(row) {
  try {
    const res = await getSeckillStatistics(row.id)
    stat.value = res?.data || null
    statVisible.value = true
  } catch (error) {
    console.error(error)
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除活动「${row.title}」吗？`, '删除确认', { type: 'warning' })
    await deleteSeckill(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') console.error(error)
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

.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
