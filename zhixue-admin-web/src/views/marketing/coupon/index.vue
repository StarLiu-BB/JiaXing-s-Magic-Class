<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>优惠券管理</span>
          <el-button v-hasPermi="['marketing:coupon:edit']" type="primary" @click="openCreateDialog">新增优惠券</el-button>
        </div>
      </template>

      <el-form :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="名称">
          <el-input v-model="queryParams.name" placeholder="模糊匹配" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="id" label="ID" width="180" />
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="优惠" width="150">
          <template #default="{ row }">{{ discountText(row) }}</template>
        </el-table-column>
        <el-table-column label="剩余 / 总量" width="120">
          <template #default="{ row }">{{ row.remainCount ?? '-' }} / {{ row.totalCount ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="userLimit" label="每人限领" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-switch
              :model-value="Number(row.status) === 1"
              :disabled="!canEdit"
              @change="(val) => onStatusChange(row, val)"
            />
          </template>
        </el-table-column>
        <el-table-column label="有效期" min-width="300">
          <template #default="{ row }">{{ formatTime(row.startTime) }} ~ {{ formatTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openStatistics(row)">统计</el-button>
            <span v-hasPermi="['marketing:coupon:edit']">
              <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
              <el-button type="warning" link @click="handleIssue(row)">发放</el-button>
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑优惠券' : '新增优惠券'" width="600px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="减免金额" prop="discount">
          <el-input-number v-model="form.discount" :min="0.01" :step="10" :precision="2" />
        </el-form-item>
        <el-form-item label="使用门槛" prop="thresholdAmount">
          <el-input-number v-model="form.thresholdAmount" :min="0" :step="10" :precision="2" />
          <span class="form-tip">订单满该金额可用，0 表示无门槛</span>
        </el-form-item>
        <el-form-item label="发放总量" prop="totalCount">
          <el-input-number v-model="form.totalCount" :min="1" :step="100" />
        </el-form-item>
        <el-form-item label="每人限领" prop="userLimit">
          <el-input-number v-model="form.userLimit" :min="1" :step="1" />
        </el-form-item>
        <el-form-item label="有效期" prop="timeRange">
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
        <el-form-item v-if="form.id" label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="statVisible" title="优惠券统计" width="460px">
      <el-descriptions v-if="stat" :column="1" border>
        <el-descriptions-item label="总量">{{ stat.totalCount }}</el-descriptions-item>
        <el-descriptions-item label="剩余">{{ stat.remainCount }}</el-descriptions-item>
        <el-descriptions-item label="已发放">{{ stat.issuedCount }}</el-descriptions-item>
        <el-descriptions-item label="已使用">{{ stat.usedCount }}</el-descriptions-item>
        <el-descriptions-item label="未使用">{{ stat.unusedCount }}</el-descriptions-item>
        <el-descriptions-item label="已过期">{{ stat.expiredCount }}</el-descriptions-item>
        <el-descriptions-item label="使用率">{{ (Number(stat.usageRate || 0) * 100).toFixed(1) }}%</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/modules/user'
import {
  addCoupon,
  changeCouponStatus,
  deleteCoupon,
  getCoupon,
  getCouponStatistics,
  issueCoupon,
  listCoupon,
  updateCoupon
} from '@/api/marketing/coupon'

const userStore = useUserStore()
const canEdit = computed(() => (userStore.permissions || []).includes('marketing:coupon:edit'))

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const statVisible = ref(false)
const stat = ref(null)
const total = ref(0)
const tableData = ref([])
const formRef = ref(null)

const queryParams = reactive({ pageNum: 1, pageSize: 10, name: '', status: undefined })

const emptyForm = () => ({
  id: undefined,
  name: '',
  discount: 10,
  thresholdAmount: 0,
  totalCount: 100,
  userLimit: 1,
  timeRange: [],
  enabled: true
})
const form = reactive(emptyForm())

const rules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  discount: [{ required: true, message: '请输入减免金额', trigger: 'blur' }],
  thresholdAmount: [{ required: true, message: '请输入使用门槛', trigger: 'blur' }],
  totalCount: [{ required: true, message: '请输入发放总量', trigger: 'blur' }],
  userLimit: [{ required: true, message: '请输入每人限领', trigger: 'blur' }],
  timeRange: [{ type: 'array', len: 2, required: true, message: '请选择有效期', trigger: 'change' }]
}

function discountText(row) {
  const discount = Number(row.discount || 0).toFixed(2)
  const threshold = Number(row.thresholdAmount || 0)
  return threshold > 0 ? `满${threshold}减${discount}` : `无门槛减${discount}`
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '-'
}

function toPickerValue(value) {
  return value ? String(value).split('.')[0] : ''
}

async function fetchList() {
  loading.value = true
  try {
    const query = { pageNum: queryParams.pageNum, pageSize: queryParams.pageSize }
    if (queryParams.name) query.name = queryParams.name.trim()
    if (queryParams.status !== undefined && queryParams.status !== null && queryParams.status !== '') {
      query.status = queryParams.status
    }
    const res = await listCoupon(query)
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
  Object.assign(queryParams, { pageNum: 1, pageSize: 10, name: '', status: undefined })
  fetchList()
}

function openCreateDialog() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

async function openEditDialog(row) {
  try {
    const res = await getCoupon(row.id)
    const detail = res?.data || row
    Object.assign(form, emptyForm(), {
      id: detail.id,
      name: detail.name,
      discount: Number(detail.discount),
      thresholdAmount: Number(detail.thresholdAmount),
      totalCount: Number(detail.totalCount),
      userLimit: Number(detail.userLimit),
      timeRange: [toPickerValue(detail.startTime), toPickerValue(detail.endTime)],
      enabled: Number(detail.status) === 1
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
    name: form.name,
    discount: form.discount,
    thresholdAmount: form.thresholdAmount,
    totalCount: form.totalCount,
    userLimit: form.userLimit,
    startTime: form.timeRange[0],
    endTime: form.timeRange[1]
  }
  if (form.id) {
    payload.status = form.enabled ? 1 : 0
  }
  submitLoading.value = true
  try {
    if (payload.id) {
      await updateCoupon(payload)
      ElMessage.success('优惠券已更新')
    } else {
      await addCoupon(payload)
      ElMessage.success('优惠券已创建')
    }
    dialogVisible.value = false
    fetchList()
  } catch (error) {
    console.error(error)
  } finally {
    submitLoading.value = false
  }
}

async function onStatusChange(row, enabled) {
  try {
    await changeCouponStatus(row.id, enabled ? 1 : 0)
    ElMessage.success(enabled ? '已启用' : '已停用')
  } catch (error) {
    console.error(error)
  } finally {
    // 以服务端状态为准刷新，失败时开关自动回到原位
    fetchList()
  }
}

async function handleIssue(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入接收用户ID，多个以英文逗号分隔', `发放「${row.name}」`, {
      inputPlaceholder: '例如：1003,1004',
      inputValidator: (v) => (/^\s*\d+(\s*,\s*\d+)*\s*$/.test(v || '') ? true : '请输入以逗号分隔的用户ID')
    })
    const userIds = value.split(',').map((id) => Number(id.trim()))
    const res = await issueCoupon(row.id, userIds)
    const success = Number(res?.data || 0)
    // 后端逐个发放，超出限领或库存的用户会失败；如实展示成功人数，不笼统提示"发放成功"
    if (success === userIds.length) {
      ElMessage.success(`已发放给 ${success} 位用户`)
    } else {
      ElMessage.warning(`成功发放 ${success} / ${userIds.length} 位用户，其余可能已达限领数或库存不足`)
    }
    fetchList()
  } catch (error) {
    if (error !== 'cancel') console.error(error)
  }
}

async function openStatistics(row) {
  try {
    const res = await getCouponStatistics(row.id)
    stat.value = res?.data || null
    statVisible.value = true
  } catch (error) {
    console.error(error)
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除优惠券「${row.name}」吗？`, '删除确认', { type: 'warning' })
    await deleteCoupon(row.id)
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
