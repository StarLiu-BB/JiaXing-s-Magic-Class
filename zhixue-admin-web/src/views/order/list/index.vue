<template>
  <div class="app-container">
    <section class="stat-grid" aria-label="订单统计">
      <div v-for="item in statCards" :key="item.key" class="stat-card">
        <div class="stat-label">
          <span v-if="item.cls" class="stat-dot" :class="item.cls" aria-hidden="true" />{{ item.label }}
        </div>
        <div class="stat-value">{{ item.value }}</div>
      </div>
    </section>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>订单列表</span>
          <el-button @click="refreshAll">刷新</el-button>
        </div>
      </template>

      <el-form :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="订单号">
          <el-input v-model="queryParams.orderNo" placeholder="精确匹配" clearable @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input v-model="queryParams.userId" placeholder="下单用户ID" clearable @keyup.enter="handleQuery" />
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
        <el-table-column prop="orderNo" label="订单号" min-width="190" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="productId" label="课程ID" width="100" />
        <el-table-column label="金额" width="110">
          <template #default="{ row }">¥{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusOf(row).tag">{{ statusOf(row).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付渠道" width="110">
          <template #default="{ row }">{{ channelText(row.payChannel) }}</template>
        </el-table-column>
        <el-table-column prop="payTime" label="支付时间" width="170">
          <template #default="{ row }">{{ formatTime(row.payTime) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="Number(row.status) === 0"
              v-hasPermi="['order:cancel']"
              type="warning"
              link
              @click="handleCancel(row)"
            >取消</el-button>
            <el-button
              v-if="Number(row.status) === 1"
              v-hasPermi="['order:refund']"
              type="danger"
              link
              @click="handleRefund(row)"
            >退款</el-button>
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

    <el-drawer v-model="detailVisible" title="订单详情" size="480px">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="订单ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ detail.userId }}</el-descriptions-item>
        <el-descriptions-item label="课程ID">{{ detail.productId }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ formatAmount(detail.amount) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusOf(detail).tag">{{ statusOf(detail).text }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="支付渠道">{{ channelText(detail.payChannel) }}</el-descriptions-item>
        <el-descriptions-item label="支付流水号">{{ detail.payNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="支付时间">{{ formatTime(detail.payTime) }}</el-descriptions-item>
        <el-descriptions-item label="过期时间">{{ formatTime(detail.expireTime) }}</el-descriptions-item>
        <el-descriptions-item label="下单时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ detail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelOrder, getOrder, getOrderStatistics, listOrder, refundOrder } from '@/api/order/order'

// 与 order_schema.sql 保持一致
const STATUS = {
  0: { text: '待支付', tag: 'warning' },
  1: { text: '已支付', tag: 'success' },
  2: { text: '已取消', tag: 'info' },
  3: { text: '已退款', tag: 'danger' }
}
const CHANNEL = { mock: '模拟收银台', alipay: '支付宝', wechat: '微信支付' }

const loading = ref(false)
const total = ref(0)
const tableData = ref([])
const stats = ref({})
const detailVisible = ref(false)
const detail = ref(null)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  orderNo: '',
  userId: '',
  status: undefined
})

const statCards = computed(() => [
  { key: 'total', label: '订单总数', value: stats.value.totalCount ?? '-' },
  { key: 'pending', label: '待支付', value: stats.value.pendingCount ?? '-', cls: 'warning' },
  { key: 'paid', label: '已支付', value: stats.value.paidCount ?? '-', cls: 'success' },
  { key: 'canceled', label: '已取消', value: stats.value.canceledCount ?? '-' },
  { key: 'refunded', label: '已退款', value: stats.value.refundedCount ?? '-', cls: 'danger' },
  { key: 'amount', label: '实收金额', value: stats.value.paidAmount == null ? '-' : `¥${formatAmount(stats.value.paidAmount)}`, cls: 'success' }
])

function statusOf(row) {
  return STATUS[Number(row?.status)] || { text: '未知', tag: 'info' }
}

function channelText(channel) {
  return channel ? (CHANNEL[channel] || channel) : '-'
}

function formatAmount(value) {
  return Number(value || 0).toFixed(2)
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '-'
}

/** 只提交有值的查询条件，避免空字符串被后端当作筛选值 */
function buildQuery() {
  const query = { pageNum: queryParams.pageNum, pageSize: queryParams.pageSize }
  if (queryParams.orderNo) query.orderNo = queryParams.orderNo.trim()
  if (queryParams.userId) query.userId = queryParams.userId.trim()
  if (queryParams.status !== undefined && queryParams.status !== null && queryParams.status !== '') {
    query.status = queryParams.status
  }
  return query
}

async function fetchList() {
  loading.value = true
  try {
    const res = await listOrder(buildQuery())
    const page = res?.data || {}
    tableData.value = page.records || []
    total.value = Number(page.total || 0)
  } catch (error) {
    // 失败即提示并清空，不保留上一页数据冒充当前结果
    tableData.value = []
    total.value = 0
    console.error(error)
  } finally {
    loading.value = false
  }
}

async function fetchStats() {
  try {
    const res = await getOrderStatistics()
    stats.value = res?.data || {}
  } catch (error) {
    stats.value = {}
    console.error(error)
  }
}

function refreshAll() {
  fetchList()
  fetchStats()
}

function handleQuery() {
  queryParams.pageNum = 1
  fetchList()
}

function resetQuery() {
  Object.assign(queryParams, { pageNum: 1, pageSize: 10, orderNo: '', userId: '', status: undefined })
  fetchList()
}

async function openDetail(row) {
  try {
    const res = await getOrder(row.id)
    detail.value = res?.data || row
    detailVisible.value = true
  } catch (error) {
    console.error(error)
  }
}

async function handleCancel(row) {
  try {
    await ElMessageBox.confirm(`确认取消订单「${row.orderNo}」吗？`, '取消订单', { type: 'warning' })
    await cancelOrder(row.id, '运营取消订单')
    ElMessage.success('订单已取消')
    refreshAll()
  } catch (error) {
    if (error !== 'cancel') console.error(error)
  }
}

async function handleRefund(row) {
  try {
    const { value } = await ElMessageBox.prompt(
      `订单「${row.orderNo}」金额 ¥${formatAmount(row.amount)}。退款后该用户将立即失去课程观看权限。`,
      '确认退款',
      {
        type: 'warning',
        inputPlaceholder: '请填写退款原因',
        inputValidator: (v) => (v && v.trim() ? true : '退款原因不能为空')
      }
    )
    await refundOrder(row.id, value.trim())
    ElMessage.success('退款成功')
    refreshAll()
  } catch (error) {
    if (error !== 'cancel') console.error(error)
  }
}

refreshAll()
</script>

<style scoped>
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  padding: 14px 16px;
  background: var(--zx-surface);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--zx-radius-card);
}

.stat-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 2px;
  background: var(--el-color-info);
}

.stat-dot.success { background: var(--el-color-success); }
.stat-dot.warning { background: var(--el-color-warning); }
.stat-dot.danger { background: var(--el-color-danger); }
.stat-dot.info { background: var(--el-border-color-darker); }

.stat-value {
  margin-top: 6px;
  font-size: 22px;
  line-height: 1.2;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--el-text-color-primary);
  font-variant-numeric: tabular-nums;
}

.search-form {
  margin-bottom: 4px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
