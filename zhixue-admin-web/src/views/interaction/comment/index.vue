<template>
  <div class="app-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>弹幕审核</span>
          <el-button :icon="Refresh" @click="fetchList">刷新</el-button>
        </div>
      </template>

      <el-alert
        type="info"
        show-icon
        :closable="false"
        class="tip"
        title="弹幕发送时由 AI 自动审核：通过即展示，不通过记为已拒绝；AI 服务不可用时按通过处理。可在此人工复核——恢复被误拒的弹幕，或下架不当内容。"
      />

      <el-form :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="房间ID">
          <el-input v-model="queryParams.roomId" placeholder="即课程ID" clearable style="width: 180px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="queryParams.auditStatus" clearable placeholder="全部状态" style="width: 140px">
            <el-option v-for="(item, value) in STATUS" :key="value" :label="item.text" :value="Number(value)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleQuery">查询</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column label="ID" prop="id" width="180" />
        <el-table-column label="房间ID" prop="roomId" width="100" />
        <el-table-column label="用户ID" prop="userId" width="100" />
        <el-table-column label="弹幕内容" prop="content" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusOf(row).tag">{{ statusOf(row).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="拒绝原因" prop="auditRemark" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ Number(row.auditStatus) === 2 && row.auditRemark ? row.auditRemark : '-' }}</template>
        </el-table-column>
        <el-table-column label="发送时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="{ row }">
            <span v-hasPermi="['interaction:danmaku:audit']">
              <el-button v-if="Number(row.auditStatus) !== 1" type="success" link @click="handleApprove(row)">通过</el-button>
              <el-button v-if="Number(row.auditStatus) !== 2" type="danger" link @click="handleReject(row)">下架</el-button>
            </span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleQuery"
          @current-change="fetchList"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { auditDanmaku, listDanmakuPage } from '@/api/interaction/danmaku'

const STATUS = {
  0: { text: '待审核', tag: 'warning' },
  1: { text: '已通过', tag: 'success' },
  2: { text: '已拒绝', tag: 'danger' }
}

const loading = ref(false)
const total = ref(0)
const tableData = ref([])
const queryParams = reactive({ pageNum: 1, pageSize: 10, roomId: '', auditStatus: undefined })

const statusOf = (row) => STATUS[Number(row.auditStatus)] || { text: '未知', tag: 'info' }
const formatTime = (value) => (value ? String(value).replace('T', ' ').split('.')[0] : '-')

async function fetchList() {
  loading.value = true
  try {
    const params = { pageNum: queryParams.pageNum, pageSize: queryParams.pageSize }
    const roomId = String(queryParams.roomId || '').trim()
    if (roomId) params.roomId = roomId
    if (queryParams.auditStatus !== undefined && queryParams.auditStatus !== null && queryParams.auditStatus !== '') {
      params.auditStatus = queryParams.auditStatus
    }
    const res = await listDanmakuPage(params)
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
  Object.assign(queryParams, { pageNum: 1, pageSize: 10, roomId: '', auditStatus: undefined })
  fetchList()
}

async function handleApprove(row) {
  try {
    await ElMessageBox.confirm(`通过后「${row.content}」将重新出现在播放页的历史弹幕中。`, '确认通过', { type: 'info' })
    await auditDanmaku(row.id, 1)
    ElMessage.success('已通过')
    fetchList()
  } catch (error) {
    if (error !== 'cancel') console.error(error)
  }
}

async function handleReject(row) {
  try {
    const { value } = await ElMessageBox.prompt(`下架「${row.content}」，请填写原因`, '下架弹幕', {
      inputPlaceholder: '例如：广告引流 / 人身攻击',
      inputValidator: (v) => (v && v.trim() ? true : '请填写原因')
    })
    await auditDanmaku(row.id, 2, value.trim())
    ElMessage.success('已下架')
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
  justify-content: space-between;
  align-items: center;
}

.tip,
.search-form {
  margin-bottom: 16px;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
