<template>
  <div class="question-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>待审核互动队列</span>
          <el-button type="primary" :icon="Refresh" @click="fetchList">刷新</el-button>
        </div>
      </template>

      <el-alert
        title="该页面用于集中查看待审核互动内容（当前接入弹幕待审数据），审核写操作将在后端开放后接入。"
        type="warning"
        :closable="false"
        class="tip"
      />

      <el-form :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="房间ID">
          <el-input v-model="queryParams.roomId" placeholder="请输入课程房间ID" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="fetchList">搜索</el-button>
          <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
          <el-button type="info" @click="goCommentPage">进入弹幕管理</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="questionList" v-loading="loading" stripe>
        <el-table-column label="ID" prop="id" width="90" />
        <el-table-column label="房间ID" prop="roomId" width="110" />
        <el-table-column label="用户ID" prop="userId" width="110" />
        <el-table-column label="待审内容" prop="content" min-width="280" show-overflow-tooltip />
        <el-table-column label="发送时间" prop="createTime" width="180" />
        <el-table-column label="状态" width="110">
          <template #default>
            <el-tag type="warning">待审核</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">查看详情</el-button>
            <el-tooltip content="后端审核接口未开放">
              <el-button type="success" link disabled>处理</el-button>
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

    <el-dialog v-model="dialogVisible" title="待审互动详情" width="560px">
      <div class="detail">
        <p><strong>ID：</strong>{{ currentItem.id }}</p>
        <p><strong>房间ID：</strong>{{ currentItem.roomId }}</p>
        <p><strong>用户ID：</strong>{{ currentItem.userId }}</p>
        <p><strong>发送时间：</strong>{{ currentItem.createTime }}</p>
        <el-divider />
        <p><strong>内容：</strong></p>
        <p class="content">{{ currentItem.content }}</p>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { listDanmakuPage } from '@/api/interaction/danmaku'

const router = useRouter()
const loading = ref(false)
const dialogVisible = ref(false)
const currentItem = ref({})
const total = ref(0)
const questionList = ref([])

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  roomId: ''
})

function normalizeRow(row) {
  return {
    ...row,
    content: row.content || row.text || ''
  }
}

async function fetchList() {
  loading.value = true
  try {
    const params = {
      pageNum: queryParams.pageNum,
      pageSize: queryParams.pageSize,
      auditStatus: 0
    }
    if (queryParams.roomId) {
      params.roomId = Number(queryParams.roomId)
    }
    const res = await listDanmakuPage(params)
    questionList.value = (res.list || []).map(normalizeRow)
    total.value = res.total || questionList.value.length
  } catch (error) {
    console.error(error)
    ElMessage.error('加载待审数据失败')
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  queryParams.roomId = ''
  fetchList()
}

function goCommentPage() {
  router.push('/interaction/comment')
}

function openDetail(row) {
  currentItem.value = { ...row }
  dialogVisible.value = true
}

fetchList()
</script>

<style lang="scss" scoped>
.question-container {
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

.detail {
  p {
    margin: 8px 0;
  }

  .content {
    white-space: pre-wrap;
    color: #333;
    line-height: 1.6;
  }
}
</style>
