<template>
  <div class="comment-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>评论管理</span>
          <el-button type="primary" :icon="Refresh" @click="handleRefresh">刷新</el-button>
        </div>
      </template>
      
      <!-- 搜索区域 -->
      <el-form :model="queryParams" :inline="true" class="search-form">
        <el-form-item label="课程">
          <el-input v-model="queryParams.courseName" placeholder="请输入课程名称" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="请选择状态" clearable>
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已拒绝" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      
      <!-- 评论列表 -->
      <el-table :data="commentList" v-loading="loading" stripe>
        <el-table-column label="学生" prop="studentName" width="100" />
        <el-table-column label="课程" prop="courseName" width="150" show-overflow-tooltip />
        <el-table-column label="评论内容" prop="content" min-width="250" show-overflow-tooltip />
        <el-table-column label="评分" width="150" align="center">
          <template #default="{ row }">
            <el-rate v-model="row.rating" disabled />
          </template>
        </el-table-column>
        <el-table-column label="评论时间" prop="createTime" width="160" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button type="success" link @click="handleApprove(row)">通过</el-button>
              <el-button type="danger" link @click="handleReject(row)">拒绝</el-button>
            </template>
            <el-button type="primary" link @click="handleView(row)">查看</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSearch"
          @current-change="handleSearch"
        />
      </div>
    </el-card>
    
    <!-- 查看对话框 -->
    <el-dialog v-model="dialogVisible" title="评论详情" width="500px">
      <div class="comment-detail">
        <div class="comment-info">
          <p><strong>学生：</strong>{{ currentComment.studentName }}</p>
          <p><strong>课程：</strong>{{ currentComment.courseName }}</p>
          <p><strong>评论时间：</strong>{{ currentComment.createTime }}</p>
          <p><strong>评分：</strong><el-rate v-model="currentComment.rating" disabled /></p>
        </div>
        <el-divider />
        <div class="comment-content">
          <p><strong>评论内容：</strong></p>
          <p>{{ currentComment.content }}</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const dialogVisible = ref(false)
const currentComment = ref({})
const total = ref(0)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  courseName: '',
  status: undefined
})

// 模拟评论数据
const commentList = ref([
  { id: 1, studentName: '张三', courseName: 'Java 基础入门', content: '老师讲得很清楚，非常适合初学者！', rating: 5, createTime: '2024-12-29 10:30:00', status: 1 },
  { id: 2, studentName: '李四', courseName: 'Spring Boot 实战', content: '内容很实用，学到了很多东西。', rating: 4, createTime: '2024-12-29 09:15:00', status: 0 },
  { id: 3, studentName: '王五', courseName: 'MySQL 数据库', content: '希望能多一些实战案例。', rating: 3, createTime: '2024-12-28 16:45:00', status: 0 },
  { id: 4, studentName: '赵六', courseName: 'Vue 3 开发', content: '课程质量很高，推荐！', rating: 5, createTime: '2024-12-28 14:20:00', status: 1 },
  { id: 5, studentName: '钱七', courseName: 'Java 基础入门', content: '垃圾课程', rating: 1, createTime: '2024-12-27 11:00:00', status: 2 }
])

const getStatusType = (status) => {
  const types = { 0: 'warning', 1: 'success', 2: 'danger' }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = { 0: '待审核', 1: '已通过', 2: '已拒绝' }
  return texts[status] || '未知'
}

const handleSearch = () => {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    total.value = commentList.value.length
  }, 500)
}

const handleReset = () => {
  queryParams.courseName = ''
  queryParams.status = undefined
  handleSearch()
}

const handleRefresh = () => {
  handleSearch()
  ElMessage.success('刷新成功')
}

const handleView = (row) => {
  currentComment.value = { ...row }
  dialogVisible.value = true
}

const handleApprove = (row) => {
  ElMessageBox.confirm('确定要通过这条评论吗？', '提示', {
    type: 'info'
  }).then(() => {
    const index = commentList.value.findIndex(c => c.id === row.id)
    if (index !== -1) {
      commentList.value[index].status = 1
    }
    ElMessage.success('审核通过')
  }).catch(() => {})
}

const handleReject = (row) => {
  ElMessageBox.confirm('确定要拒绝这条评论吗？', '提示', {
    type: 'warning'
  }).then(() => {
    const index = commentList.value.findIndex(c => c.id === row.id)
    if (index !== -1) {
      commentList.value[index].status = 2
    }
    ElMessage.success('已拒绝')
  }).catch(() => {})
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除这条评论吗？', '提示', {
    type: 'warning'
  }).then(() => {
    const index = commentList.value.findIndex(c => c.id === row.id)
    if (index !== -1) {
      commentList.value.splice(index, 1)
    }
    ElMessage.success('删除成功')
  }).catch(() => {})
}

// 初始化
handleSearch()
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

.search-form {
  margin-bottom: 20px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.comment-detail {
  .comment-info {
    background: #f5f7fa;
    padding: 15px;
    border-radius: 4px;
    
    p {
      margin: 8px 0;
      color: #606266;
      display: flex;
      align-items: center;
      
      strong {
        min-width: 80px;
      }
    }
  }
  
  .comment-content {
    p {
      margin: 5px 0;
      line-height: 1.6;
    }
  }
}
</style>
