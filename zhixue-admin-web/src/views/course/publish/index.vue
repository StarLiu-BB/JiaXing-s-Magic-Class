<template>
  <div class="app-container">
    <el-card shadow="never" class="page-card">
      <el-steps :active="activeStep" finish-status="success" align-center class="steps">
        <el-step title="基本信息" />
        <el-step title="章节管理" />
        <el-step title="试看设置" />
        <el-step title="完成发布" />
      </el-steps>

      <el-alert v-if="readonly" title="只读模式：仅查看课程信息" type="info" :closable="false" />
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false">
        <el-button @click="loadCourseData">重试</el-button>
      </el-alert>
      <div class="step-content" v-loading="loadingCourse">
        <!-- 步骤1：基本信息（离开本步时保存，新课程以草稿创建） -->
        <el-form
          v-show="activeStep === 0"
          ref="step1FormRef"
          :model="form"
          :disabled="!canWrite"
          :rules="step1Rules"
          label-width="120px"
        >
          <el-form-item label="课程标题" prop="title">
            <el-input v-model="form.title" placeholder="请输入课程标题" maxlength="100" show-word-limit />
          </el-form-item>
          <el-form-item label="副标题" prop="subtitle">
            <el-input v-model="form.subtitle" placeholder="一句话说明课程卖点（可选）" maxlength="100" show-word-limit />
          </el-form-item>
          <el-form-item label="课程分类" prop="categoryId">
            <el-select v-model="form.categoryId" placeholder="请选择分类" style="width: 100%">
              <el-option
                v-for="category in categoryOptions"
                :key="category.id"
                :label="category.name"
                :value="category.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="课程封面" prop="cover">
            <div class="cover-field">
              <el-upload
                class="cover-uploader"
                :action="uploadUrl"
                :headers="uploadHeaders"
                :show-file-list="false"
                :on-success="handleCoverSuccess"
                :on-error="handleCoverError"
                :before-upload="beforeCoverUpload"
              >
                <img v-if="form.cover" :src="form.cover" class="cover-image" alt="课程封面" />
                <el-icon v-else class="cover-uploader-icon"><Plus /></el-icon>
              </el-upload>
              <el-input v-model="form.cover" placeholder="或直接填写图片地址 https://..." clearable class="cover-url" />
            </div>
            <div class="upload-tip">建议尺寸 750×422，JPG / PNG，不超过 2MB</div>
          </el-form-item>
          <el-form-item label="课程价格" prop="price">
            <el-input-number v-model="form.price" :min="0" :precision="2" style="width: 200px" />
            <span style="margin-left: 10px">元</span>
          </el-form-item>
          <el-form-item label="原价" prop="originalPrice">
            <el-input-number v-model="form.originalPrice" :min="0" :precision="2" style="width: 200px" />
            <span style="margin-left: 10px">元（划线价，可选）</span>
          </el-form-item>
          <el-form-item label="课程简介" prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="5"
              placeholder="请输入课程简介，学生端详情页展示"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-form>

        <!-- 步骤2：章节管理（课程已保存，章节与课时实时保存） -->
        <div v-show="activeStep === 1" class="chapter-management">
          <div v-if="canWrite" class="chapter-toolbar">
            <el-button type="primary" icon="Plus" @click="handleAddChapter">添加章节</el-button>
            <span class="upload-tip">章节与课时保存后立即生效</span>
          </div>
          <el-empty v-if="chapterTree.length === 0" description="还没有章节，先添加一个章节" :image-size="80" />
          <el-tree
            v-else
            :data="chapterTree"
            :props="{ children: 'lessons', label: 'title' }"
            default-expand-all
            :expand-on-click-node="false"
            node-key="nodeKey"
          >
            <template #default="{ data }">
              <div class="tree-node">
                <span v-if="!data.isLesson" class="node-label">
                  <el-icon><Folder /></el-icon>
                  {{ data.title }}
                </span>
                <span v-else class="node-label">
                  <el-icon><VideoPlay /></el-icon>
                  {{ data.title }}
                  <span class="lesson-meta">{{ formatMinutes(data.duration) }}</span>
                </span>
                <span v-if="canWrite" class="node-actions">
                  <el-button v-if="!data.isLesson" link type="primary" icon="Plus" @click="handleAddLesson(data)">
                    添加课时
                  </el-button>
                  <el-button link type="primary" icon="Edit" @click="handleEditNode(data)">编辑</el-button>
                  <el-button link type="danger" icon="Delete" @click="handleDeleteNode(data)">删除</el-button>
                </span>
              </div>
            </template>
          </el-tree>
        </div>

        <!-- 步骤3：试看设置 -->
        <el-form v-show="activeStep === 2" :model="form" :disabled="!canWrite" label-width="120px">
          <el-form-item label="允许试看">
            <el-switch v-model="form.allowPreview" />
          </el-form-item>
          <el-form-item v-if="form.allowPreview" label="试看课时数">
            <el-input-number v-model="form.previewLessons" :min="1" :max="Math.max(totalLessons, 1)" style="width: 150px" />
            <span class="upload-tip" style="margin-left: 12px">未购买的学生可观看按顺序排列的前 N 个课时</span>
          </el-form-item>
        </el-form>

        <!-- 步骤4：完成发布 -->
        <div v-show="activeStep === 3" class="publish-summary">
          <el-result :icon="readonly ? 'info' : 'success'" :title="readonly ? '课程信息' : '课程信息已保存'" :sub-title="summaryText" />
          <el-descriptions title="课程信息" :column="2" border style="margin-top: 12px">
            <el-descriptions-item label="课程标题">{{ form.title }}</el-descriptions-item>
            <el-descriptions-item label="课程分类">{{ getCategoryName(form.categoryId) }}</el-descriptions-item>
            <el-descriptions-item label="课程价格">¥{{ form.price }}</el-descriptions-item>
            <el-descriptions-item label="原价">{{ form.originalPrice ? `¥${form.originalPrice}` : '无' }}</el-descriptions-item>
            <el-descriptions-item label="章节数">{{ chapterTree.length }}</el-descriptions-item>
            <el-descriptions-item label="课时数">{{ totalLessons }}</el-descriptions-item>
            <el-descriptions-item label="试看">
              {{ form.allowPreview ? `前 ${form.previewLessons} 个课时` : '不允许试看' }}
            </el-descriptions-item>
            <el-descriptions-item label="当前状态">{{ isPublished ? '已发布' : '草稿' }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <div class="step-actions">
        <el-button v-if="activeStep > 0" @click="prevStep">上一步</el-button>
        <el-button v-if="activeStep < 3" type="primary" :loading="saving" :disabled="loadingCourse || !!loadError" @click="nextStep">下一步</el-button>
        <template v-if="activeStep === 3">
          <el-button v-if="canPublish" type="success" :loading="saving" :disabled="loadingCourse || !!loadError" @click="handlePublish">
            {{ isPublished ? '重新上架' : '发布课程' }}
          </el-button>
          <el-button v-if="canWrite" @click="handleSaveDraft">{{ isPublished ? '完成' : '保存为草稿' }}</el-button>
        </template>
        <el-button @click="handleCancel">{{ readonly ? '返回' : '取消' }}</el-button>
      </div>
    </el-card>

    <!-- 章节/课时编辑弹窗 -->
    <el-dialog :title="nodeDialogTitle" v-model="nodeDialogVisible" width="560px" append-to-body>
      <el-form ref="nodeFormRef" :model="nodeForm" :disabled="!canWrite" :rules="nodeRules" label-width="100px">
        <el-form-item :label="nodeForm.isLesson ? '课时标题' : '章节标题'" prop="title">
          <el-input v-model="nodeForm.title" maxlength="100" :placeholder="nodeForm.isLesson ? '请输入课时标题' : '请输入章节标题'" />
        </el-form-item>
        <el-form-item v-if="nodeForm.isLesson" label="视频地址" prop="videoUrl">
          <el-input v-model="nodeForm.videoUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item v-if="nodeForm.isLesson" label="时长（分钟）" prop="minutes">
          <el-input-number v-model="nodeForm.minutes" :min="1" :max="600" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="nodeForm.orderNum" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialogVisible = false">取消</el-button>
        <el-button v-if="canWrite" type="primary" :loading="saving" @click="submitNodeForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Folder, VideoPlay } from '@element-plus/icons-vue'
import { getCourse, addCourse, updateCourse, publishCourse, getCategoryList } from '@/api/course/course'
import { listChapter, addChapter, updateChapter, deleteChapter, addLesson, updateLesson, deleteLesson } from '@/api/course/chapter'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/stores/modules/user'

// 说明：此前页面还收集「课程详情富文本 / 学习目标 / 适合人群 / 有效期天数 / 学习模式 / 课时是否免费」，
// 但后端 course、section 表没有这些字段，保存时全部被静默丢弃。现只保留能真正存下来的字段，
// 试看改为课程级的「前 N 个课时」（与后端 LessonAccessPolicy 一致）。

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeStep = ref(0)
// 雪花 ID 必须保持字符串：parseInt 会丢精度，编辑时打开的是另一门（或不存在的）课程
const courseId = ref(null)
const isPublished = ref(false)
const saving = ref(false)
const canEdit = computed(() => (userStore.permissions || []).includes('course:edit'))
const readonly = computed(() => route.meta?.readonly === true || !canEdit.value)
const canPublish = computed(() => route.meta?.readonly !== true && (userStore.permissions || []).includes('course:publish'))
const loadingCourse = ref(!!route.query.id)
const loadError = ref('')
const canWrite = computed(() => !readonly.value && !loadingCourse.value && !loadError.value)
const requireWrite = () => {
  if (canWrite.value) return true
  ElMessage.warning('当前无法修改课程，请确认权限和加载状态')
  return false
}

const step1FormRef = ref(null)
const nodeFormRef = ref(null)

const categoryOptions = ref([])
const chapterTree = ref([])
// 后端只提供 /media/upload（单文件直传）；el-upload 不走 axios，必须手动带上令牌，否则网关 401
const uploadUrl = import.meta.env.VITE_APP_BASE_API + '/media/upload'
const uploadHeaders = computed(() => ({ Authorization: `Bearer ${getToken()}` }))

const form = reactive({
  title: '',
  subtitle: '',
  categoryId: undefined,
  cover: '',
  price: 0,
  originalPrice: 0,
  description: '',
  allowPreview: true,
  previewLessons: 1
})

const nodeDialogVisible = ref(false)
const nodeDialogTitle = ref('')
const nodeForm = reactive({ id: undefined, chapterId: undefined, isLesson: false, title: '', videoUrl: '', minutes: 10, orderNum: 1 })

const step1Rules = {
  title: [{ required: true, message: '请输入课程标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择课程分类', trigger: 'change' }],
  cover: [{ required: true, message: '请上传封面或填写图片地址', trigger: 'change' }],
  price: [{ required: true, message: '请输入课程价格', trigger: 'blur' }]
}

const nodeRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  videoUrl: [{ required: true, message: '请输入视频地址', trigger: 'blur' }],
  minutes: [{ required: true, message: '请输入时长', trigger: 'blur' }]
}

const totalLessons = computed(() => chapterTree.value.reduce((n, c) => n + (c.lessons || []).length, 0))
const summaryText = computed(() =>
  readonly.value ? '仅查看课程信息，未执行保存' : isPublished.value ? '课程已发布，修改已保存' : '课程目前是草稿，发布后学生才能看到'
)

const getCategoryName = (id) => categoryOptions.value.find((c) => c.id === id)?.name || ''
const formatMinutes = (seconds) => (seconds ? `${Math.round(seconds / 60)} 分钟` : '')

const getCategoryOptions = async () => {
  try {
    const response = await getCategoryList()
    categoryOptions.value = response?.data || []
  } catch (error) {
    console.error('获取分类列表失败:', error)
  }
}

// ---------- 封面 ----------
const beforeCoverUpload = (file) => {
  if (!requireWrite()) return false
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  if (file.size / 1024 / 1024 >= 2) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

const handleCoverSuccess = (response) => {
  if (!requireWrite()) return
  // /media/upload 返回 R<MediaFile>，地址在 data.fileUrl
  const url = response?.code === 200 ? response.data?.fileUrl : ''
  if (url) {
    form.cover = url
    step1FormRef.value?.clearValidate('cover')
    ElMessage.success('封面上传成功')
  } else {
    ElMessage.error(response?.msg || '封面上传失败，可直接填写图片地址')
  }
}

const handleCoverError = () => {
  ElMessage.error('封面上传失败，可直接填写图片地址')
}

// ---------- 保存 ----------
const coursePayload = () => ({
  title: form.title.trim(),
  subtitle: form.subtitle.trim() || undefined,
  categoryId: form.categoryId,
  coverUrl: form.cover,
  price: form.price,
  originalPrice: form.originalPrice || form.price,
  description: form.description,
  allowPreview: form.allowPreview ? 1 : 0,
  previewLessonCount: form.previewLessons
})

/** 新课程以草稿创建并拿回 ID；已有课程直接更新（发布/上下架状态后端不会被编辑接口改动） */
const saveCourse = async () => {
  if (!requireWrite()) return
  if (courseId.value) {
    await updateCourse({ id: courseId.value, ...coursePayload() })
    return
  }
  const response = await addCourse(coursePayload())
  const id = response?.data?.id
  if (!id) {
    throw new Error('保存课程后未返回课程 ID')
  }
  courseId.value = String(id)
  // 刷新或回退时仍能继续编辑同一门课，而不是再新建一门
  router.replace({ query: { ...route.query, id: courseId.value } })
}

const withSaving = async (fn) => {
  saving.value = true
  try {
    return await fn()
  } finally {
    saving.value = false
  }
}

const nextStep = async () => {
  if (loadingCourse.value || loadError.value) return
  if (readonly.value) {
    activeStep.value = Math.min(activeStep.value + 1, 3)
    return
  }
  if (activeStep.value === 0) {
    const valid = await step1FormRef.value.validate().catch(() => false)
    if (!valid) return
    try {
      await withSaving(saveCourse)
      activeStep.value = 1
    } catch (error) {
      console.error('保存课程失败:', error)
    }
  } else if (activeStep.value === 1) {
    if (totalLessons.value === 0) {
      ElMessage.warning('请至少添加一个章节和一个课时')
      return
    }
    activeStep.value = 2
  } else if (activeStep.value === 2) {
    try {
      await withSaving(saveCourse)
      activeStep.value = 3
    } catch (error) {
      console.error('保存试看设置失败:', error)
    }
  }
}

const prevStep = () => {
  if (activeStep.value > 0) activeStep.value--
}

const handlePublish = async () => {
  if (!canPublish.value || !courseId.value || loadingCourse.value || loadError.value) return
  try {
    await ElMessageBox.confirm('发布后学生即可在学生端看到并购买该课程，确定发布吗？', '发布课程', {
      confirmButtonText: '发布',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await withSaving(() => publishCourse(courseId.value))
    ElMessage.success('课程已发布')
    router.push('/course/list')
  } catch (error) {
    console.error('发布失败:', error)
  }
}

const handleSaveDraft = () => {
  ElMessage.success(isPublished.value ? '已保存' : '已保存为草稿，可在课程列表中继续发布')
  router.push('/course/list')
}

const handleCancel = () => {
  router.back()
}

// ---------- 章节 / 课时（实时保存） ----------
const resetNodeForm = () => {
  Object.assign(nodeForm, { id: undefined, chapterId: undefined, isLesson: false, title: '', videoUrl: '', minutes: 10, orderNum: 1 })
  nodeFormRef.value?.clearValidate()
}

const handleAddChapter = () => {
  if (!requireWrite()) return
  resetNodeForm()
  nodeForm.orderNum = chapterTree.value.length + 1
  nodeDialogTitle.value = '添加章节'
  nodeDialogVisible.value = true
}

const handleAddLesson = (chapter) => {
  if (!requireWrite()) return
  resetNodeForm()
  nodeForm.chapterId = chapter.id
  nodeForm.isLesson = true
  nodeForm.orderNum = (chapter.lessons || []).length + 1
  nodeDialogTitle.value = `添加课时 · ${chapter.title}`
  nodeDialogVisible.value = true
}

const handleEditNode = (data) => {
  if (!requireWrite()) return
  resetNodeForm()
  Object.assign(nodeForm, {
    id: data.id,
    chapterId: data.chapterId,
    isLesson: data.isLesson,
    title: data.title,
    videoUrl: data.videoUrl || '',
    minutes: data.isLesson ? Math.max(1, Math.round((data.duration || 60) / 60)) : 10,
    orderNum: data.orderNum || 1
  })
  nodeDialogTitle.value = data.isLesson ? '编辑课时' : '编辑章节'
  nodeDialogVisible.value = true
}

const handleDeleteNode = async (data) => {
  if (!requireWrite()) return
  const lessons = data.isLesson ? 0 : (data.lessons || []).length
  try {
    await ElMessageBox.confirm(
      lessons ? `删除章节「${data.title}」会同时删除其中 ${lessons} 个课时，确定吗？` : `确定删除「${data.title}」吗？`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    if (data.isLesson) {
      await deleteLesson(data.id)
    } else {
      await deleteChapter(data.id)
    }
    await loadChapters()
    ElMessage.success('已删除')
  } catch (error) {
    console.error('删除失败:', error)
  }
}

const submitNodeForm = async () => {
  if (!requireWrite()) return
  const valid = await nodeFormRef.value.validate().catch(() => false)
  if (!valid) return
  try {
    await withSaving(async () => {
      if (nodeForm.isLesson) {
        const lesson = {
          courseId: courseId.value,
          chapterId: nodeForm.chapterId,
          title: nodeForm.title.trim(),
          videoUrl: nodeForm.videoUrl.trim(),
          duration: nodeForm.minutes * 60, // 后端以秒存储
          orderNum: nodeForm.orderNum,
          status: 1
        }
        await (nodeForm.id ? updateLesson({ id: nodeForm.id, ...lesson }) : addLesson(lesson))
      } else {
        const chapter = { courseId: courseId.value, title: nodeForm.title.trim(), orderNum: nodeForm.orderNum }
        await (nodeForm.id ? updateChapter({ id: nodeForm.id, ...chapter }) : addChapter(chapter))
      }
    })
    nodeDialogVisible.value = false
    await loadChapters()
    ElMessage.success('已保存')
  } catch (error) {
    console.error('保存失败:', error)
  }
}

/** 以服务端为准重新加载章节树（章节列表接口自带课时） */
const loadChapters = async () => {
  if (!courseId.value) return
  const response = await listChapter(courseId.value)
  chapterTree.value = (response?.data || []).map((chapter) => ({
    nodeKey: `c-${chapter.id}`,
    id: chapter.id,
    title: chapter.title,
    orderNum: chapter.orderNum,
    isLesson: false,
    lessons: (chapter.lessons || []).map((lesson) => ({
      nodeKey: `l-${lesson.id}`,
      id: lesson.id,
      chapterId: chapter.id,
      isLesson: true,
      title: lesson.title,
      videoUrl: lesson.videoUrl,
      duration: lesson.duration,
      orderNum: lesson.orderNum
    }))
  }))
}

const loadCourseData = async () => {
  const id = route.query.id
  if (!id) return
  courseId.value = String(id)
  loadingCourse.value = true
  loadError.value = ''
  try {
    const course = (await getCourse(courseId.value))?.data
    if (!course?.id) throw new Error('课程信息未加载')
    Object.assign(form, {
      title: course.title || '',
      subtitle: course.subtitle || '',
      categoryId: course.categoryId,
      cover: course.coverUrl || '',
      price: Number(course.price || 0),
      originalPrice: Number(course.originalPrice || 0),
      description: course.description || '',
      allowPreview: course.allowPreview !== 0,
      previewLessons: course.previewLessonCount || 1
    })
    isPublished.value = Number(course.status) === 1 && Number(course.shelfStatus) === 1
    await loadChapters()
  } catch (error) {
    loadError.value = '课程加载失败，请重试'
    console.error('加载课程失败:', error)
  } finally {
    loadingCourse.value = false
  }
}

onMounted(async () => {
  await getCategoryOptions()
  await loadCourseData()
})
</script>

<style lang="scss" scoped>
.page-card :deep(.el-card__body) {
  padding: 28px 32px 24px;
}

.steps {
  max-width: 880px;
  margin: 0 auto 32px;
}

.step-content {
  min-height: 460px;
  max-width: 880px;
  margin: 0 auto;
  padding: 20px 0;
}

.cover-field {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  width: 100%;

  .cover-url {
    flex: 1;
    max-width: 420px;
  }
}

.cover-uploader {
  :deep(.el-upload) {
    border: 1px dashed var(--el-border-color-dark);
    border-radius: 6px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    background: var(--el-fill-color-lighter);
    transition: border-color 0.16s var(--zx-ease);

    &:hover {
      border-color: var(--el-color-primary);
    }
  }

  .cover-image {
    width: 200px;
    height: 112px;
    display: block;
    object-fit: cover;
  }

  .cover-uploader-icon {
    font-size: 28px;
    color: var(--el-text-color-placeholder);
    width: 200px;
    height: 112px;
    line-height: 112px;
    text-align: center;
  }
}

.upload-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 6px;
}

.chapter-management {
  .chapter-toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;

    .upload-tip {
      margin-top: 0;
    }
  }

  .tree-node {
    flex: 1;
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 14px;
    padding-right: 8px;

    .node-label {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .lesson-meta {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }

    .node-actions {
      margin-left: auto;
    }
  }

  :deep(.el-tree-node__content) {
    height: 38px;
  }
}

.publish-summary {
  text-align: center;
}

.step-actions {
  text-align: center;
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
