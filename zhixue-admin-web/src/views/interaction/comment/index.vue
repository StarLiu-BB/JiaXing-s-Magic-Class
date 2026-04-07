<template>
  <div class="comment-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>弹幕管理</span>
          <el-button type="primary" :icon="Refresh" @click="fetchList">刷新</el-button>
        </div>
      </template>

      <el-alert
        title="当前后端尚未开放弹幕审核写接口，页面已改为真实数据只读管理。"
        type="info"
        :closable="false"
        class="tip"
      />

      <el-form :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="房间ID">
          <el-input v-model="queryParams.roomId" placeholder="请输入课程房间ID" clearable />
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select v-model="queryParams.auditStatus" clearable placeholder="全部状态">
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已拒绝" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchList">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="commentList" v-loading="loading" stripe>
        <el-table-column label="ID" prop="id" width="90" />
        <el-table-column label="房间ID" prop="roomId" width="110" />
        <el-table-column label="用户ID" prop="userId" width="110" />
        <el-table-column label="弹幕内容" prop="content" min-width="260" show-overflow-tooltip />
        <el-table-column label="发送时间" prop="createTime" width="180" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.auditStatus)">
              {{ getStatusText(row.auditStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleView(row)">查看</el-button>
            <el-tooltip content="后端未开放审核写接口">
              <el-button type="success" link disabled>通过</el-button>
            </el-tooltip>
            <el-tooltip content="后端未开放审核写接口">
              <el-button type="danger" link disabled>拒绝</el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="fetchList"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="弹幕详情" width="560px">
      <div class="comment-detail">
        <p><strong>ID：</strong>{{ currentComment.id }}</p>
        <p><strong>房间ID：</strong>{{ currentComment.roomId }}</p>
        <p><strong>用户ID：</strong>{{ currentComment.userId }}</p>
        <p><strong>发送时间：</strong>{{ currentComment.createTime }}</p>
        <p><strong>审核状态：</strong>{{ getStatusText(currentComment.auditStatus) }}</p>
        <el-divider />
        <p><strong>内容：</strong></p>
        <p class="content">{{ currentComment.content }}</p>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listDanmakuPage } from '@/api/interaction/danmaku'

const loading = ref(false)
const dialogVisible = ref(false)
const currentComment = ref({})
const total = ref(0)
const commentList = ref([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  roomId: '',
  auditStatus: undefined
})

function getStatusType(status) {
  const map = { 0: 'warning', 1: 'success', 2: 'danger' }
  return map[status] || 'info'
}

function getStatusText(status) {
  const map = { 0: '待审核', 1: '已通过', 2: '已拒绝' }
  return map[status] || '未知'
}

function normalizeRow(row) {
  return {
    ...row,
    auditStatus: Number(row.auditStatus ?? 0),
    content: row.content || row.text || ''
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params = {
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize
    }
    if (queryParams.roomId) {
      params.roomId = Number(queryParams.roomId)
    }
    if (queryParams.auditStatus !== undefined) {
      params.auditStatus = queryParams.auditStatus
    }
    const res = await listDanmakuPage(params)
    commentList.value = (res.list || []).map(normalizeRow)
    total.value = res.total || commentList.value.length
  } catch (error) {
    console.error(error)
    ElMessage.error('加载弹幕数据失败')
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  queryParams.roomId = ''
  queryParams.auditStatus = undefined
  fetchList()
}

function handleView(row) {
  currentComment.value = { ...row }
  dialogVisible.value = true
}

fetchList()
</script>

<style lang="scss" scoped>
.comment-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.tip {
  margin-bottom: 16px;
}

.search-form {
  margin-bottom: 20px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.comment-detail {
  p {
    margin: 8px 0;
  }

  .content {
    line-height: 1.6;
    white-space: pre-wrap;
    color: #333;
  }
}
</style>
