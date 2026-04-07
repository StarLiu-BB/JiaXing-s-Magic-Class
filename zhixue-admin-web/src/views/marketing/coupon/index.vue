<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>优惠券管理</span>
          <el-button type="primary" @click="openCreateDialog">新增优惠券</el-button>
        </div>
      </template>

      <el-form :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" placeholder="优惠券名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="fetchList">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="name" label="优惠券名称" min-width="180" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">
            {{ getTypeText(row.type) }}
          </template>
        </el-table-column>
        <el-table-column label="面额/折扣" width="140">
          <template #default="{ row }">
            {{ getDiscountText(row) }}
          </template>
        </el-table-column>
        <el-table-column label="库存" width="120">
          <template #default="{ row }">
            {{ row.totalCount ?? row.total ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-switch
              :model-value="Number(row.status) === 1"
              @change="(val) => onStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="220">
          <template #default="{ row }">
            {{ formatPeriod(row.startTime, row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button type="warning" link @click="handleIssue(row)">发放</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="满减券" :value="0" />
            <el-option label="折扣券" :value="1" />
            <el-option label="代金券" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.type === 1" label="折扣率" prop="discountRate">
          <el-input-number v-model="form.discountRate" :min="0.1" :max="1" :step="0.05" :precision="2" />
        </el-form-item>
        <el-form-item v-else label="优惠金额" prop="discountAmount">
          <el-input-number v-model="form.discountAmount" :min="0" :step="10" :precision="2" />
        </el-form-item>
        <el-form-item label="门槛金额" prop="minAmount">
          <el-input-number v-model="form.minAmount" :min="0" :step="10" :precision="2" />
        </el-form-item>
        <el-form-item label="发放数量" prop="totalCount">
          <el-input-number v-model="form.totalCount" :min="-1" :step="100" />
        </el-form-item>
        <el-form-item label="每人限领" prop="perUserLimit">
          <el-input-number v-model="form.perUserLimit" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="有效期" prop="timeRange">
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
          <el-switch v-model="statusSwitch" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="200" show-word-limit />
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
  addCoupon,
  changeCouponStatus,
  claimCoupon,
  deleteCoupon,
  getCoupon,
  issueCoupon,
  listCoupon,
  updateCoupon
} from '@/api/marketing/coupon'

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
  type: 0,
  discountAmount: 0,
  discountRate: 0.9,
  minAmount: 0,
  totalCount: 100,
  perUserLimit: 1,
  startTime: '',
  endTime: '',
  status: 1,
  remark: ''
})
const timeRange = ref([])
const statusSwitch = ref(true)

const rules = {
  name: [{ required: true, message: '请输入优惠券名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择优惠券类型', trigger: 'change' }]
}

const dialogTitle = computed(() => (form.id ? '编辑优惠券' : '新增优惠券'))

function resetForm() {
  Object.assign(form, {
    id: undefined,
    name: '',
    type: 0,
    discountAmount: 0,
    discountRate: 0.9,
    minAmount: 0,
    totalCount: 100,
    perUserLimit: 1,
    startTime: '',
    endTime: '',
    status: 1,
    remark: ''
  })
  timeRange.value = []
  statusSwitch.value = true
}

function normalizeRow(row) {
  return {
    ...row,
    id: row.id ?? row.couponId,
    type: Number(row.type ?? 0),
    status: Number(row.status ?? 1),
    totalCount: row.totalCount ?? row.total ?? 0,
    discountAmount: row.discountAmount ?? row.discount ?? 0
  }
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listCoupon(queryParams)
    tableData.value = (res.list || []).map(normalizeRow)
    total.value = res.total || tableData.value.length
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

function getTypeText(type) {
  return ['满减券', '折扣券', '代金券'][Number(type)] || '未知'
}

function getDiscountText(row) {
  if (Number(row.type) === 1) {
    const rate = Number(row.discountRate || 1)
    return `${Math.round(rate * 10 * 10) / 10} 折`
  }
  return `¥${Number(row.discountAmount || 0)}`
}

function formatPeriod(startTime, endTime) {
  if (!startTime || !endTime) {
    return '-'
  }
  return `${startTime} ~ ${endTime}`
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

async function openEditDialog(row) {
  resetForm()
  const couponId = row.id
  if (!couponId) {
    return
  }
  try {
    const res = await getCoupon(couponId)
    const detail = normalizeRow(res?.data || row)
    Object.assign(form, detail)
    timeRange.value = [detail.startTime, detail.endTime].filter(Boolean)
    statusSwitch.value = Number(detail.status) === 1
    dialogVisible.value = true
  } catch (error) {
    console.error(error)
    ElMessage.error('读取优惠券详情失败')
  }
}

async function submitForm() {
  try {
    await formRef.value?.validate()
    if (timeRange.value.length !== 2) {
      ElMessage.warning('请选择优惠券有效期')
      return
    }
    if (timeRange.value.length === 2) {
      form.startTime = timeRange.value[0]
      form.endTime = timeRange.value[1]
    }
    form.status = statusSwitch.value ? 1 : 0
    const payload = {
      ...form
    }
    submitLoading.value = true
    if (payload.id) {
      await updateCoupon(payload)
      ElMessage.success('优惠券更新成功')
    } else {
      await addCoupon(payload)
      ElMessage.success('优惠券创建成功')
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

async function onStatusChange(row, enabled) {
  const oldStatus = Number(row.status)
  const targetStatus = enabled ? 1 : 0
  try {
    await changeCouponStatus(row.id, targetStatus)
    row.status = targetStatus
    ElMessage.success('状态更新成功')
  } catch (error) {
    row.status = oldStatus
  }
}

async function handleIssue(row) {
  try {
    await ElMessageBox.confirm('是否立即发放该优惠券给当前用户？', '发放确认', {
      type: 'warning'
    })
    try {
      await issueCoupon(row.id)
    } catch {
      await claimCoupon(row.id)
    }
    ElMessage.success('发放成功')
  } catch (error) {
    if (error !== 'cancel') {
      console.error(error)
    }
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除优惠券「${row.name}」吗？`, '删除确认', {
      type: 'warning'
    })
    await deleteCoupon(row.id)
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
