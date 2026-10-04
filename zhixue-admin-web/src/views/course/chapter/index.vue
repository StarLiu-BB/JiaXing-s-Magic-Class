<template>
  <div class="app-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>章节管理{{ loadedCourseId ? `（课程 ${loadedCourseId}）` : '' }}</span>
          <el-button v-if="loadedCourseId" v-hasPermi="['course:edit']" type="primary" @click="openChapter()">新增章节</el-button>
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

      <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" class="state-alert" />

      <el-table :data="tableData" v-loading="loading" row-key="rowKey" border default-expand-all>
        <el-table-column prop="title" label="章节 / 课时名称" min-width="240" />
        <el-table-column prop="orderNum" label="排序" width="80" />
        <el-table-column label="时长" width="100">
          <template #default="{ row }">{{ row.isLesson ? formatDuration(row.duration) : '' }}</template>
        </el-table-column>
        <el-table-column label="视频地址" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.isLesson ? (row.videoUrl || '未设置') : '' }}</template>
        </el-table-column>
        <el-table-column label="类型 / 状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="!row.isLesson" type="warning">章节</el-tag>
            <el-tag v-else :type="Number(row.status) === 1 ? 'success' : 'info'">
              {{ Number(row.status) === 1 ? '已发布' : '未发布' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230">
          <template #default="{ row }">
            <span v-hasPermi="['course:edit']">
              <template v-if="!row.isLesson">
                <el-button type="primary" link @click="openLesson(row)">新增课时</el-button>
                <el-button type="primary" link @click="openChapter(row)">编辑</el-button>
                <el-button type="danger" link @click="removeChapter(row)">删除</el-button>
              </template>
              <template v-else>
                <el-button type="primary" link @click="openLesson(null, row)">编辑</el-button>
                <el-button type="danger" link @click="removeLesson(row)">删除</el-button>
              </template>
            </span>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!loading && !errorMessage && tableData.length === 0"
        :description="loadedCourseId ? '该课程暂无章节，可点击右上角新增' : '请输入课程ID后查询章节'"
      />
    </el-card>

    <el-dialog v-model="chapterVisible" :title="chapterForm.id ? '编辑章节' : '新增章节'" width="480px">
      <el-form ref="chapterFormRef" :model="chapterForm" :rules="chapterRules" label-width="80px">
        <el-form-item label="章节名称" prop="title">
          <el-input v-model="chapterForm.title" maxlength="60" show-word-limit />
        </el-form-item>
        <el-form-item label="排序" prop="orderNum">
          <el-input-number v-model="chapterForm.orderNum" :min="0" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chapterVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitChapter">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="lessonVisible" :title="lessonForm.id ? '编辑课时' : '新增课时'" width="560px">
      <el-form ref="lessonFormRef" :model="lessonForm" :rules="lessonRules" label-width="90px">
        <el-form-item label="课时名称" prop="title">
          <el-input v-model="lessonForm.title" maxlength="80" show-word-limit />
        </el-form-item>
        <el-form-item label="视频地址" prop="videoUrl">
          <el-input v-model="lessonForm.videoUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="时长(秒)" prop="duration">
          <el-input-number v-model="lessonForm.duration" :min="0" :step="60" />
        </el-form-item>
        <el-form-item label="排序" prop="orderNum">
          <el-input-number v-model="lessonForm.orderNum" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="lessonForm.status">
            <el-radio :value="1">已发布</el-radio>
            <el-radio :value="0">未发布</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="lessonVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitLesson">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addChapter,
  addLesson,
  deleteChapter,
  deleteLesson,
  listChapter,
  updateChapter,
  updateLesson
} from '@/api/course/chapter'

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref([])
const courseIdInput = ref('')
const loadedCourseId = ref('')
const errorMessage = ref('')

const chapterVisible = ref(false)
const chapterFormRef = ref(null)
const chapterForm = reactive({ id: undefined, title: '', orderNum: 1 })
const chapterRules = { title: [{ required: true, message: '请输入章节名称', trigger: 'blur' }] }

const lessonVisible = ref(false)
const lessonFormRef = ref(null)
const lessonForm = reactive({ id: undefined, chapterId: '', title: '', videoUrl: '', duration: 0, orderNum: 1, status: 1 })
const lessonRules = { title: [{ required: true, message: '请输入课时名称', trigger: 'blur' }] }

const formatDuration = (seconds) => {
  const s = Number(seconds || 0)
  return `${Math.floor(s / 60)}分${s % 60}秒`
}

/** 章节列表接口已携带 lessons，无需再逐个章节请求课时 */
const toRows = (chapters) => chapters.map((chapter) => ({
  ...chapter,
  rowKey: `c-${chapter.id}`,
  isLesson: false,
  children: (chapter.lessons || []).map((lesson) => ({ ...lesson, rowKey: `l-${lesson.id}`, isLesson: true }))
}))

const loadCourse = async (courseId) => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await listChapter(courseId)
    tableData.value = toRows(Array.isArray(response?.data) ? response.data : [])
    loadedCourseId.value = String(courseId)
  } catch (error) {
    console.error('获取章节列表失败:', error)
    tableData.value = []
    loadedCourseId.value = ''
    errorMessage.value = '章节列表加载失败：课程不存在或无权访问该课程。'
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  const courseId = String(courseIdInput.value || '').trim()
  if (!/^\d+$/.test(courseId)) {
    ElMessage.warning('请输入有效的课程ID')
    return
  }
  loadCourse(courseId)
}

const handleReset = () => {
  courseIdInput.value = ''
  loadedCourseId.value = ''
  errorMessage.value = ''
  tableData.value = []
}

const openChapter = (row) => {
  Object.assign(chapterForm, row
    ? { id: row.id, title: row.title, orderNum: Number(row.orderNum ?? 1) }
    : { id: undefined, title: '', orderNum: tableData.value.length + 1 })
  chapterVisible.value = true
  chapterFormRef.value?.clearValidate()
}

const submitChapter = async () => {
  try {
    await chapterFormRef.value?.validate()
  } catch {
    return
  }
  submitLoading.value = true
  try {
    const payload = { id: chapterForm.id, courseId: loadedCourseId.value, title: chapterForm.title.trim(), orderNum: chapterForm.orderNum }
    if (chapterForm.id) {
      await updateChapter(payload)
    } else {
      await addChapter(payload)
    }
    ElMessage.success('章节已保存')
    chapterVisible.value = false
    await loadCourse(loadedCourseId.value)
  } catch (error) {
    console.error(error)
  } finally {
    submitLoading.value = false
  }
}

const removeChapter = async (row) => {
  const count = (row.children || []).length
  try {
    await ElMessageBox.confirm(
      count > 0 ? `删除章节「${row.title}」将同时删除其下 ${count} 个课时，确定吗？` : `确定删除章节「${row.title}」吗？`,
      '删除确认',
      { type: 'warning' }
    )
    await deleteChapter(row.id)
    ElMessage.success('删除成功')
    await loadCourse(loadedCourseId.value)
  } catch (error) {
    if (error !== 'cancel') console.error(error)
  }
}

const openLesson = (chapter, lesson) => {
  if (lesson) {
    Object.assign(lessonForm, {
      id: lesson.id,
      chapterId: lesson.chapterId,
      title: lesson.title,
      videoUrl: lesson.videoUrl || '',
      duration: Number(lesson.duration || 0),
      orderNum: Number(lesson.orderNum ?? 1),
      status: Number(lesson.status ?? 1)
    })
  } else {
    Object.assign(lessonForm, {
      id: undefined,
      chapterId: chapter.id,
      title: '',
      videoUrl: '',
      duration: 0,
      orderNum: (chapter.children || []).length + 1,
      status: 1
    })
  }
  lessonVisible.value = true
  lessonFormRef.value?.clearValidate()
}

const submitLesson = async () => {
  try {
    await lessonFormRef.value?.validate()
  } catch {
    return
  }
  submitLoading.value = true
  try {
    const payload = {
      id: lessonForm.id,
      courseId: loadedCourseId.value,
      chapterId: lessonForm.chapterId,
      title: lessonForm.title.trim(),
      videoUrl: lessonForm.videoUrl.trim(),
      duration: lessonForm.duration,
      orderNum: lessonForm.orderNum,
      status: lessonForm.status
    }
    if (lessonForm.id) {
      await updateLesson(payload)
    } else {
      await addLesson(payload)
    }
    ElMessage.success('课时已保存')
    lessonVisible.value = false
    await loadCourse(loadedCourseId.value)
  } catch (error) {
    console.error(error)
  } finally {
    submitLoading.value = false
  }
}

const removeLesson = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除课时「${row.title}」吗？`, '删除确认', { type: 'warning' })
    await deleteLesson(row.id)
    ElMessage.success('删除成功')
    await loadCourse(loadedCourseId.value)
  } catch (error) {
    if (error !== 'cancel') console.error(error)
  }
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
