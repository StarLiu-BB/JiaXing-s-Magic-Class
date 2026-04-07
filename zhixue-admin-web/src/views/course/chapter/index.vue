<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>章节管理</span>
          <el-tooltip content="第一阶段暂不开放章节编辑，支持查询" placement="left">
            <el-button type="primary" disabled>新增章节</el-button>
          </el-tooltip>
        </div>
      </template>

      <el-form :inline="true" class="query-form">
        <el-form-item label="课程ID">
          <el-input
            v-model="courseIdInput"
            placeholder="请输入课程ID后查询"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询章节</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
        class="state-alert"
      />

      <el-table
        :data="tableData"
        v-loading="loading"
        row-key="id"
        border
        default-expand-all
      >
        <el-table-column prop="title" label="章节/课时名称" min-width="220" />
        <el-table-column prop="orderNum" label="排序" width="90" />
        <el-table-column prop="duration" label="时长(秒)" width="110" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.lessonId ? (row.status === 1 ? 'success' : 'info') : 'warning'">
              {{ row.lessonId ? (row.status === 1 ? '已发布' : '草稿') : '章节' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default>
            <el-tooltip content="第一阶段暂不开放章节编辑">
              <el-button type="primary" link disabled>编辑</el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && !errorMessage && tableData.length === 0"
        :description="courseIdInput ? '该课程暂无章节数据' : '请输入课程ID后查询章节'"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listChapter, listLesson } from '@/api/course/chapter'

const loading = ref(false)
const tableData = ref([])
const courseIdInput = ref('')
const errorMessage = ref('')

const normalizeRows = (response) => {
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response)) return response
  return []
}

const handleSearch = async () => {
  const courseId = Number(courseIdInput.value)
  if (!courseId) {
    ElMessage.warning('请输入有效的课程ID')
    return
  }

  loading.value = true
  errorMessage.value = ''
  tableData.value = []

  try {
    const chapterResponse = await listChapter(courseId)
    const chapters = normalizeRows(chapterResponse)
    const sectionsByChapter = await Promise.all(
      chapters.map(async (chapter) => {
        try {
          const sectionResponse = await listLesson(chapter.id)
          const sections = normalizeRows(sectionResponse)
          return {
            ...chapter,
            children: sections.map(section => ({
              ...section,
              lessonId: section.id
            }))
          }
        } catch (sectionError) {
          console.error(`加载章节 ${chapter.id} 的课时失败`, sectionError)
          return {
            ...chapter,
            children: []
          }
        }
      })
    )
    tableData.value = sectionsByChapter
  } catch (error) {
    console.error('获取章节列表失败:', error)
    errorMessage.value = '章节列表加载失败，请检查后端服务或稍后重试。'
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  courseIdInput.value = ''
  errorMessage.value = ''
  tableData.value = []
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.query-form {
  margin-bottom: 12px;
}

.state-alert {
  margin-bottom: 12px;
}
</style>
