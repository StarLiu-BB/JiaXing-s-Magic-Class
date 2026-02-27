<template>
  <div class="question-container">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>问答管理</span>
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
            <el-option label="待回复" :value="0" />
            <el-option label="已回复" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      
      <!-- 问答列表 -->
      <el-table :data="questionList" v-loading="loading" stripe>
        <el-table-column label="学生" prop="studentName" width="100" />
        <el-table-column label="课程" prop="courseName" width="150" show-overflow-tooltip />
        <el-table-column label="问题内容" prop="content" min-width="250" show-overflow-tooltip />
        <el-table-column label="提问时间" prop="createTime" width="160" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'">
              {{ row.status === 1 ? '已回复' : '待回复' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleReply(row)">
              {{ row.status === 1 ? '查看' : '回复' }}
            </el-button>
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
    
    <!-- 回复对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <div class="question-detail">
        <div class="question-info">
          <p><strong>学生：</strong>{{ currentQuestion.studentName }}</p>
          <p><strong>课程：</strong>{{ currentQuestion.courseName }}</p>
          <p><strong>提问时间：</strong>{{ currentQuestion.createTime }}</p>
        </div>
        <div class="question-content">
          <p><strong>问题内容：</strong></p>
          <p>{{ currentQuestion.content }}</p>
        </div>
        <el-divider />
        <el-form v-if="currentQuestion.status !== 1" label-width="80px">
          <el-form-item label="回复内容">
            <el-input
              v-model="replyContent"
              type="textarea"
              :rows="4"
              placeholder="请输入回复内容"
            />
          </el-form-item>
        </el-form>
        <div v-else class="reply-content">
          <p><strong>回复内容：</strong></p>
          <p>{{ currentQuestion.replyContent }}</p>
          <p class="reply-time">回复时间：{{ currentQuestion.replyTime }}</p>
        </div>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="currentQuestion.status !== 1" type="primary" @click="submitReply">
          提交回复
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { Search, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const dialogVisible = ref(false)
const replyContent = ref('')
const currentQuestion = ref({})
const total = ref(0)

const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  courseName: '',
  status: undefined
})

// 模拟问答数据
const questionList = ref([
  { id: 1, studentName: '张三', courseName: 'Java 基础入门', content: '请问 Java 中的多态是什么意思？能举个例子吗？', createTime: '2024-12-29 10:30:00', status: 0 },
  { id: 2, studentName: '李四', courseName: 'Spring Boot 实战', content: '为什么我的 @Autowired 注入失败了？', createTime: '2024-12-29 09:15:00', status: 1, replyContent: '请检查是否添加了 @Component 注解，以及包扫描路径是否正确。', replyTime: '2024-12-29 11:00:00' },
  { id: 3, studentName: '王五', courseName: 'MySQL 数据库', content: '索引的原理是什么？什么时候应该建索引？', createTime: '2024-12-28 16:45:00', status: 0 },
  { id: 4, studentName: '赵六', courseName: 'Vue 3 开发', content: 'Composition API 和 Options API 有什么区别？', createTime: '2024-12-28 14:20:00', status: 1, replyContent: 'Composition API 更灵活，适合复杂逻辑复用；Options API 更直观，适合简单组件。', replyTime: '2024-12-28 15:30:00' }
])

const dialogTitle = computed(() => currentQuestion.value.status === 1 ? '查看问答' : '回复问题')

const handleSearch = () => {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    total.value = questionList.value.length
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

const handleReply = (row) => {
  currentQuestion.value = { ...row }
  replyContent.value = ''
  dialogVisible.value = true
}

const submitReply = () => {
  if (!replyContent.value.trim()) {
    ElMessage.warning('请输入回复内容')
    return
  }
  // 模拟提交
  const index = questionList.value.findIndex(q => q.id === currentQuestion.value.id)
  if (index !== -1) {
    questionList.value[index].status = 1
    questionList.value[index].replyContent = replyContent.value
    questionList.value[index].replyTime = new Date().toLocaleString()
  }
  dialogVisible.value = false
  ElMessage.success('回复成功')
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除这条问答吗？', '提示', {
    type: 'warning'
  }).then(() => {
    const index = questionList.value.findIndex(q => q.id === row.id)
    if (index !== -1) {
      questionList.value.splice(index, 1)
    }
    ElMessage.success('删除成功')
  }).catch(() => {})
}

// 初始化
handleSearch()
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

.search-form {
  margin-bottom: 20px;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.question-detail {
  .question-info {
    background: #f5f7fa;
    padding: 15px;
    border-radius: 4px;
    margin-bottom: 15px;
    
    p {
      margin: 5px 0;
      color: #606266;
    }
  }
  
  .question-content {
    p {
      margin: 5px 0;
      line-height: 1.6;
    }
  }
  
  .reply-content {
    background: #f0f9eb;
    padding: 15px;
    border-radius: 4px;
    
    p {
      margin: 5px 0;
      line-height: 1.6;
    }
    
    .reply-time {
      color: #909399;
      font-size: 12px;
      margin-top: 10px;
    }
  }
}
</style>
