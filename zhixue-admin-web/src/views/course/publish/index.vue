<template>
  <div class="app-container">
    <el-steps :active="activeStep" finish-status="success" align-center class="steps">
      <el-step title="基本信息" />
      <el-step title="课程内容" />
      <el-step title="章节管理" />
      <el-step title="课程设置" />
      <el-step title="完成发布" />
    </el-steps>

    <div class="step-content">
      <!-- 步骤1：基本信息 -->
      <el-form
        v-show="activeStep === 0"
        ref="step1FormRef"
        :model="form"
        :rules="step1Rules"
        label-width="120px"
      >
        <el-form-item label="课程标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入课程标题" maxlength="100" show-word-limit />
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
          <el-upload
            class="cover-uploader"
            :action="uploadUrl"
            :show-file-list="false"
            :on-success="handleCoverSuccess"
            :before-upload="beforeCoverUpload"
          >
            <img v-if="form.cover" :src="form.cover" class="cover-image" />
            <el-icon v-else class="cover-uploader-icon"><Plus /></el-icon>
          </el-upload>
          <div class="upload-tip">建议尺寸：750x422px，支持 JPG、PNG 格式</div>
        </el-form-item>
        <el-form-item label="课程价格" prop="price">
          <el-input-number
            v-model="form.price"
            :min="0"
            :precision="2"
            placeholder="请输入价格"
            style="width: 200px"
          />
          <span style="margin-left: 10px">元</span>
        </el-form-item>
        <el-form-item label="原价" prop="originalPrice">
          <el-input-number
            v-model="form.originalPrice"
            :min="0"
            :precision="2"
            placeholder="请输入原价（可选）"
            style="width: 200px"
          />
          <span style="margin-left: 10px">元</span>
        </el-form-item>
        <el-form-item label="课程简介" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="5"
            placeholder="请输入课程简介"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="学习目标">
          <el-input
            v-model="objectivesInput"
            type="textarea"
            :rows="3"
            placeholder="每行一个目标，例如：&#10;掌握Vue3基础语法&#10;学会使用组合式API"
            @blur="handleObjectivesBlur"
          />
        </el-form-item>
        <el-form-item label="适合人群">
          <el-input
            v-model="suitableInput"
            type="textarea"
            :rows="3"
            placeholder="每行一个人群，例如：&#10;前端开发者&#10;Vue初学者"
            @blur="handleSuitableBlur"
          />
        </el-form-item>
      </el-form>

      <!-- 步骤2：课程内容 -->
      <div v-show="activeStep === 1" class="rich-text-editor">
        <el-form-item label="课程详情" prop="content">
          <div id="editor-container" style="height: 500px"></div>
        </el-form-item>
      </div>

      <!-- 步骤3：章节管理 -->
      <div v-show="activeStep === 2" class="chapter-management">
        <div class="chapter-toolbar">
          <el-button type="primary" icon="Plus" @click="handleAddChapter">添加章节</el-button>
        </div>
        <el-tree
          :data="chapterTree"
          :props="{ children: 'lessons', label: 'title' }"
          default-expand-all
          :expand-on-click-node="false"
          node-key="id"
          draggable
          @node-drop="handleNodeDrop"
        >
          <template #default="{ node, data }">
            <div class="tree-node">
              <span v-if="!data.lessonId" class="node-label">
                <el-icon><Folder /></el-icon>
                {{ data.title }}
              </span>
              <span v-else class="node-label">
                <el-icon><VideoPlay /></el-icon>
                {{ data.title }}
                <el-tag v-if="data.isFree" type="success" size="small" style="margin-left: 10px">免费</el-tag>
              </span>
              <span class="node-actions">
                <el-button
                  v-if="!data.lessonId"
                  link
                  type="primary"
                  icon="Plus"
                  @click="handleAddLesson(data)"
                >
                  添加课时
                </el-button>
                <el-button link type="primary" icon="Edit" @click="handleEditNode(data)">编辑</el-button>
                <el-button link type="danger" icon="Delete" @click="handleDeleteNode(data)">删除</el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>

      <!-- 步骤4：课程设置 -->
      <el-form
        v-show="activeStep === 3"
        ref="step4FormRef"
        :model="form"
        :rules="step4Rules"
        label-width="120px"
      >
        <el-form-item label="有效期" prop="validityDays">
          <el-radio-group v-model="form.validityType">
            <el-radio :label="0">永久有效</el-radio>
            <el-radio :label="1">购买后有效</el-radio>
          </el-radio-group>
          <el-input-number
            v-if="form.validityType === 1"
            v-model="form.validityDays"
            :min="1"
            placeholder="天数"
            style="width: 150px; margin-left: 20px"
          />
          <span v-if="form.validityType === 1" style="margin-left: 10px">天</span>
        </el-form-item>
        <el-form-item label="试看设置">
          <el-switch v-model="form.allowPreview" />
          <span style="margin-left: 10px">允许试看</span>
        </el-form-item>
        <el-form-item v-if="form.allowPreview" label="试看课时数">
          <el-input-number
            v-model="form.previewLessons"
            :min="0"
            placeholder="试看课时数"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="学习模式">
          <el-radio-group v-model="form.studyMode">
            <el-radio :label="0">自由学习</el-radio>
            <el-radio :label="1">顺序学习</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <!-- 步骤5：完成发布 -->
      <div v-show="activeStep === 4" class="publish-summary">
        <el-result icon="success" title="课程信息已保存" sub-title="请确认信息无误后发布课程">
          <template #extra>
            <el-button type="primary" @click="handlePublish">立即发布</el-button>
            <el-button @click="handleSaveDraft">保存草稿</el-button>
          </template>
        </el-result>
        <el-descriptions title="课程信息" :column="2" border style="margin-top: 30px">
          <el-descriptions-item label="课程标题">{{ form.title }}</el-descriptions-item>
          <el-descriptions-item label="课程分类">
            {{ getCategoryName(form.categoryId) }}
          </el-descriptions-item>
          <el-descriptions-item label="课程价格">¥{{ form.price }}</el-descriptions-item>
          <el-descriptions-item label="原价">¥{{ form.originalPrice || '无' }}</el-descriptions-item>
          <el-descriptions-item label="章节数">{{ chapterTree.length }}</el-descriptions-item>
          <el-descriptions-item label="课时数">{{ totalLessons }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </div>

    <div class="step-actions">
      <el-button v-if="activeStep > 0" @click="prevStep">上一步</el-button>
      <el-button v-if="activeStep < 4" type="primary" @click="nextStep">下一步</el-button>
      <el-button v-if="activeStep === 4" type="success" @click="handlePublish">发布课程</el-button>
      <el-button @click="handleCancel">取消</el-button>
    </div>

    <!-- 章节/课时编辑弹窗 -->
    <el-dialog
      :title="nodeDialogTitle"
      v-model="nodeDialogVisible"
      width="600px"
      append-to-body
    >
      <el-form
        ref="nodeFormRef"
        :model="nodeForm"
        :rules="nodeRules"
        label-width="100px"
      >
        <el-form-item v-if="!nodeForm.lessonId" label="章节标题" prop="title">
          <el-input v-model="nodeForm.title" placeholder="请输入章节标题" />
        </el-form-item>
        <el-form-item v-else label="课时标题" prop="title">
          <el-input v-model="nodeForm.title" placeholder="请输入课时标题" />
        </el-form-item>
        <el-form-item v-if="nodeForm.lessonId" label="视频地址" prop="videoUrl">
          <el-input v-model="nodeForm.videoUrl" placeholder="请输入视频地址" />
        </el-form-item>
        <el-form-item v-if="nodeForm.lessonId" label="时长（分钟）" prop="duration">
          <el-input-number v-model="nodeForm.duration" :min="1" placeholder="时长" />
        </el-form-item>
        <el-form-item v-if="nodeForm.lessonId" label="是否免费">
          <el-switch v-model="nodeForm.isFree" />
        </el-form-item>
        <el-form-item v-if="nodeForm.lessonId" label="课时描述">
          <el-input
            v-model="nodeForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入课时描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nodeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitNodeForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Folder, VideoPlay } from '@element-plus/icons-vue'
import { getCourse, addCourse, updateCourse } from '@/api/course/course'
import { getCategoryList } from '@/api/course/course'
import { listChapter, addChapter, updateChapter, deleteChapter, addLesson, updateLesson, deleteLesson } from '@/api/course/chapter'
import { uploadImage } from '@/api/media/upload'

const route = useRoute()
const router = useRouter()

// 步骤控制
const activeStep = ref(0)
const courseId = ref(null)

// 表单引用
const step1FormRef = ref(null)
const step4FormRef = ref(null)
const nodeFormRef = ref(null)

// 数据
const categoryOptions = ref([])
const chapterTree = ref([])
const objectivesInput = ref('')
const suitableInput = ref('')
const uploadUrl = ref(import.meta.env.VITE_APP_BASE_API + '/media/upload/image')

// 富文本编辑器实例
let editorInstance = null

// 表单数据
const form = reactive({
  id: undefined,
  title: '',
  categoryId: undefined,
  cover: '',
  price: 0,
  originalPrice: 0,
  description: '',
  objectives: [],
  suitable: [],
  content: '',
  validityType: 0,
  validityDays: 365,
  allowPreview: true,
  previewLessons: 3,
  studyMode: 0,
  status: 0
})

// 节点表单
const nodeDialogVisible = ref(false)
const nodeDialogTitle = ref('')
const nodeForm = reactive({
  id: undefined,
  chapterId: undefined,
  lessonId: undefined,
  title: '',
  videoUrl: '',
  duration: 0,
  isFree: false,
  description: '',
  sortOrder: 0
})

// 表单验证规则
const step1Rules = reactive({
  title: [{ required: true, message: '请输入课程标题', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择课程分类', trigger: 'change' }],
  cover: [{ required: true, message: '请上传课程封面', trigger: 'change' }],
  price: [{ required: true, message: '请输入课程价格', trigger: 'blur' }]
})

const step4Rules = reactive({
  validityDays: [
    {
      validator: (rule, value, callback) => {
        if (form.validityType === 1 && !value) {
          callback(new Error('请输入有效期天数'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
})

const nodeRules = reactive({
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  videoUrl: [
    {
      validator: (rule, value, callback) => {
        if (nodeForm.lessonId && !value) {
          callback(new Error('请输入视频地址'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  duration: [
    {
      validator: (rule, value, callback) => {
        if (nodeForm.lessonId && !value) {
          callback(new Error('请输入时长'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
})

// 计算属性
const totalLessons = computed(() => {
  let count = 0
  chapterTree.value.forEach(chapter => {
    count += (chapter.lessons || []).length
  })
  return count
})

// 获取分类名称
const getCategoryName = (categoryId) => {
  const category = categoryOptions.value.find(c => c.id === categoryId)
  return category ? category.name : ''
}

// 获取分类列表
const getCategoryOptions = async () => {
  try {
    const response = await getCategoryList()
    categoryOptions.value = response || []
  } catch (error) {
    console.error('获取分类列表失败:', error)
  }
}

// 初始化富文本编辑器
const initEditor = async () => {
  // 这里使用简单的 textarea，实际项目中可以使用 Quill、TinyMCE 等富文本编辑器
  // 示例：使用 textarea 代替
  await nextTick()
  const container = document.getElementById('editor-container')
  if (container && !editorInstance) {
    // 创建简单的文本编辑器
    const textarea = document.createElement('textarea')
    textarea.className = 'el-textarea__inner'
    textarea.style.width = '100%'
    textarea.style.height = '100%'
    textarea.style.border = '1px solid #dcdfe6'
    textarea.style.borderRadius = '4px'
    textarea.style.padding = '5px 15px'
    textarea.placeholder = '请输入课程详情内容...'
    textarea.value = form.content
    textarea.addEventListener('input', (e) => {
      form.content = e.target.value
    })
    container.appendChild(textarea)
    editorInstance = textarea
  }
}

// 学习目标处理
const handleObjectivesBlur = () => {
  form.objectives = objectivesInput.value
    .split('\n')
    .map(item => item.trim())
    .filter(item => item)
}

// 适合人群处理
const handleSuitableBlur = () => {
  form.suitable = suitableInput.value
    .split('\n')
    .map(item => item.trim())
    .filter(item => item)
}

// 封面上传
const beforeCoverUpload = (file) => {
  const isImage = file.type.startsWith('image/')
  const isLt2M = file.size / 1024 / 1024 < 2

  if (!isImage) {
    ElMessage.error('只能上传图片文件!')
    return false
  }
  if (!isLt2M) {
    ElMessage.error('图片大小不能超过 2MB!')
    return false
  }
  return true
}

const handleCoverSuccess = (response) => {
  if (response.code === 200 || response.data) {
    form.cover = response.data || response.url || response.img
    ElMessage.success('封面上传成功')
  } else {
    ElMessage.error('封面上传失败')
  }
}

// 步骤控制
const nextStep = async () => {
  if (activeStep.value === 0) {
    // 验证第一步
    step1FormRef.value.validate(async (valid) => {
      if (valid) {
        handleObjectivesBlur()
        handleSuitableBlur()
        activeStep.value++
        await nextTick()
        if (activeStep.value === 1) {
          initEditor()
        }
      }
    })
  } else if (activeStep.value === 1) {
    // 验证第二步
    if (!form.content.trim()) {
      ElMessage.warning('请输入课程详情内容')
      return
    }
    activeStep.value++
  } else if (activeStep.value === 2) {
    // 验证第三步
    if (chapterTree.value.length === 0) {
      ElMessage.warning('请至少添加一个章节')
      return
    }
    // 检查是否有课时
    const hasLessons = chapterTree.value.some(chapter => chapter.lessons && chapter.lessons.length > 0)
    if (!hasLessons) {
      ElMessage.warning('请至少添加一个课时')
      return
    }
    activeStep.value++
  } else if (activeStep.value === 3) {
    // 验证第四步
    step4FormRef.value.validate((valid) => {
      if (valid) {
        activeStep.value++
      }
    })
  }
}

const prevStep = () => {
  if (activeStep.value > 0) {
    activeStep.value--
  }
}

// 章节管理
const handleAddChapter = () => {
  resetNodeForm()
  nodeForm.chapterId = undefined
  nodeForm.lessonId = undefined
  nodeDialogTitle.value = '添加章节'
  nodeDialogVisible.value = true
}

const handleAddLesson = (chapter) => {
  resetNodeForm()
  nodeForm.chapterId = chapter.id
  nodeForm.lessonId = 'new'
  nodeDialogTitle.value = '添加课时'
  nodeDialogVisible.value = true
}

const handleEditNode = (data) => {
  resetNodeForm()
  if (data.lessonId) {
    // 编辑课时
    nodeForm.id = data.id
    nodeForm.chapterId = data.chapterId
    nodeForm.lessonId = data.lessonId
    nodeForm.title = data.title
    nodeForm.videoUrl = data.videoUrl || ''
    nodeForm.duration = data.duration || 0
    nodeForm.isFree = data.isFree || false
    nodeForm.description = data.description || ''
    nodeDialogTitle.value = '编辑课时'
  } else {
    // 编辑章节
    nodeForm.id = data.id
    nodeForm.title = data.title
    nodeDialogTitle.value = '编辑章节'
  }
  nodeDialogVisible.value = true
}

const handleDeleteNode = async (data) => {
  try {
    await ElMessageBox.confirm('确定要删除吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    if (data.lessonId) {
      // 删除课时
      await deleteLesson(data.id)
      // 从树中移除
      const chapter = chapterTree.value.find(c => c.id === data.chapterId)
      if (chapter && chapter.lessons) {
        const index = chapter.lessons.findIndex(l => l.id === data.id)
        if (index > -1) {
          chapter.lessons.splice(index, 1)
        }
      }
    } else {
      // 删除章节
      await deleteChapter(data.id)
      const index = chapterTree.value.findIndex(c => c.id === data.id)
      if (index > -1) {
        chapterTree.value.splice(index, 1)
      }
    }
    ElMessage.success('删除成功')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
    }
  }
}

const submitNodeForm = async () => {
  nodeFormRef.value.validate(async (valid) => {
    if (valid) {
      try {
        if (nodeForm.lessonId) {
          // 保存课时
          const lessonData = {
            chapterId: nodeForm.chapterId,
            title: nodeForm.title,
            videoUrl: nodeForm.videoUrl,
            duration: nodeForm.duration,
            isFree: nodeForm.isFree,
            description: nodeForm.description,
            sortOrder: nodeForm.sortOrder || 0
          }

          if (nodeForm.id) {
            // 更新
            lessonData.id = nodeForm.id
            await updateLesson(lessonData)
            // 更新树中数据
            const chapter = chapterTree.value.find(c => c.id === nodeForm.chapterId)
            if (chapter && chapter.lessons) {
              const lesson = chapter.lessons.find(l => l.id === nodeForm.id)
              if (lesson) {
                Object.assign(lesson, lessonData)
              }
            }
          } else {
            // 新增
            const response = await addLesson(lessonData)
            const newLesson = {
              id: response.id || response.data?.id,
              chapterId: nodeForm.chapterId,
              lessonId: 'lesson',
              ...lessonData
            }
            const chapter = chapterTree.value.find(c => c.id === nodeForm.chapterId)
            if (chapter) {
              if (!chapter.lessons) {
                chapter.lessons = []
              }
              chapter.lessons.push(newLesson)
            }
          }
        } else {
          // 保存章节
          const chapterData = {
            courseId: courseId.value,
            title: nodeForm.title,
            sortOrder: nodeForm.sortOrder || 0
          }

          if (nodeForm.id) {
            // 更新
            chapterData.id = nodeForm.id
            await updateChapter(chapterData)
            // 更新树中数据
            const chapter = chapterTree.value.find(c => c.id === nodeForm.id)
            if (chapter) {
              chapter.title = nodeForm.title
            }
          } else {
            // 新增
            if (!courseId.value) {
              ElMessage.warning('请先保存课程基本信息')
              return
            }
            chapterData.courseId = courseId.value
            const response = await addChapter(chapterData)
            const newChapter = {
              id: response.id || response.data?.id,
              title: nodeForm.title,
              lessons: []
            }
            chapterTree.value.push(newChapter)
          }
        }

        nodeDialogVisible.value = false
        ElMessage.success('保存成功')
      } catch (error) {
        console.error('保存失败:', error)
      }
    }
  })
}

const handleNodeDrop = () => {
  // 拖拽后更新排序
  // 这里可以调用API更新排序
}

const resetNodeForm = () => {
  nodeForm.id = undefined
  nodeForm.chapterId = undefined
  nodeForm.lessonId = undefined
  nodeForm.title = ''
  nodeForm.videoUrl = ''
  nodeForm.duration = 0
  nodeForm.isFree = false
  nodeForm.description = ''
  nodeForm.sortOrder = 0
  nodeFormRef.value?.resetFields()
}

// 发布/保存
const handlePublish = async () => {
  try {
    await ElMessageBox.confirm('确定要发布该课程吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    form.status = 1 // 已发布
    await saveCourse()
    if (courseId.value) {
      await saveChaptersAndLessons()
    }
    ElMessage.success('发布成功')
    router.push('/course/list')
  } catch (error) {
    if (error !== 'cancel') {
      console.error('发布失败:', error)
      ElMessage.error('发布失败，请重试')
    }
  }
}

const handleSaveDraft = async () => {
  try {
    form.status = 0 // 草稿
    await saveCourse()
    if (courseId.value) {
      await saveChaptersAndLessons()
    }
    ElMessage.success('保存草稿成功')
    router.push('/course/list')
  } catch (error) {
    console.error('保存草稿失败:', error)
    ElMessage.error('保存草稿失败，请重试')
  }
}

const saveCourse = async () => {
  // 保存课程基本信息
  const courseData = {
    title: form.title,
    categoryId: form.categoryId,
    cover: form.cover,
    price: form.price,
    originalPrice: form.originalPrice || 0,
    description: form.description,
    objectives: form.objectives,
    suitable: form.suitable,
    content: form.content,
    validityType: form.validityType,
    validityDays: form.validityType === 1 ? form.validityDays : undefined,
    allowPreview: form.allowPreview,
    previewLessons: form.allowPreview ? form.previewLessons : undefined,
    studyMode: form.studyMode,
    status: form.status
  }

  if (courseId.value) {
    courseData.id = courseId.value
    await updateCourse(courseData)
  } else {
    const response = await addCourse(courseData)
    courseId.value = response.id || response.data?.id
    // 保存章节和课时
    await saveChaptersAndLessons()
  }
}

// 保存章节和课时
const saveChaptersAndLessons = async () => {
  if (!courseId.value || chapterTree.value.length === 0) {
    return
  }

  for (let i = 0; i < chapterTree.value.length; i++) {
    const chapter = chapterTree.value[i]
    let chapterId = chapter.id

    // 保存章节
    if (!chapterId) {
      const chapterData = {
        courseId: courseId.value,
        title: chapter.title,
        sortOrder: i + 1
      }
      const chapterResponse = await addChapter(chapterData)
      chapterId = chapterResponse.id || chapterResponse.data?.id
      chapter.id = chapterId
    } else {
      await updateChapter({
        id: chapterId,
        title: chapter.title,
        sortOrder: i + 1
      })
    }

    // 保存课时
    if (chapter.lessons && chapter.lessons.length > 0) {
      for (let j = 0; j < chapter.lessons.length; j++) {
        const lesson = chapter.lessons[j]
        const lessonData = {
          chapterId: chapterId,
          title: lesson.title,
          videoUrl: lesson.videoUrl,
          duration: lesson.duration,
          isFree: lesson.isFree,
          description: lesson.description,
          sortOrder: j + 1
        }

        if (lesson.id && lesson.id !== 'lesson') {
          lessonData.id = lesson.id
          await updateLesson(lessonData)
        } else {
          const lessonResponse = await addLesson(lessonData)
          lesson.id = lessonResponse.id || lessonResponse.data?.id
        }
      }
    }
  }
}

const handleCancel = () => {
  router.back()
}

// 加载课程数据（编辑模式）
const loadCourseData = async () => {
  const id = route.query.id
  if (id) {
    courseId.value = parseInt(id)
    try {
      const response = await getCourse(courseId.value)
      Object.assign(form, response)
      objectivesInput.value = (response.objectives || []).join('\n')
      suitableInput.value = (response.suitable || []).join('\n')
      
      // 加载章节数据
      try {
        const chaptersResponse = await listChapter(courseId.value)
        if (chaptersResponse && chaptersResponse.length > 0) {
          chapterTree.value = chaptersResponse.map(chapter => ({
            id: chapter.id,
            title: chapter.title,
            sortOrder: chapter.sortOrder || 0,
            lessons: (chapter.lessons || []).map(lesson => ({
              id: lesson.id,
              chapterId: chapter.id,
              lessonId: 'lesson',
              title: lesson.title,
              videoUrl: lesson.videoUrl || '',
              duration: lesson.duration || 0,
              isFree: lesson.isFree || false,
              description: lesson.description || '',
              sortOrder: lesson.sortOrder || 0
            }))
          }))
        }
      } catch (chapterError) {
        console.error('加载章节数据失败:', chapterError)
        // 如果章节API失败，尝试从课程响应中获取
        if (response.chapters) {
          chapterTree.value = response.chapters.map(chapter => ({
            id: chapter.id,
            title: chapter.title,
            sortOrder: chapter.sortOrder || 0,
            lessons: (chapter.lessons || []).map(lesson => ({
              id: lesson.id,
              chapterId: chapter.id,
              lessonId: 'lesson',
              title: lesson.title,
              videoUrl: lesson.videoUrl || '',
              duration: lesson.duration || 0,
              isFree: lesson.isFree || false,
              description: lesson.description || '',
              sortOrder: lesson.sortOrder || 0
            }))
          }))
        }
      }
    } catch (error) {
      console.error('加载课程数据失败:', error)
    }
  }
}

// 组件挂载
onMounted(async () => {
  await getCategoryOptions()
  await loadCourseData()
  if (activeStep.value === 1) {
    await initEditor()
  }
})

// 组件卸载
onUnmounted(() => {
  if (editorInstance) {
    editorInstance = null
  }
})
</script>

<style lang="scss" scoped>
.app-container {
  padding: 20px;
  background: #fff;
  border-radius: 4px;
}

.steps {
  margin-bottom: 40px;
}

.step-content {
  min-height: 500px;
  padding: 20px 0;
}

.cover-uploader {
  :deep(.el-upload) {
    border: 1px dashed #d9d9d9;
    border-radius: 6px;
    cursor: pointer;
    position: relative;
    overflow: hidden;
    transition: all 0.3s;

    &:hover {
      border-color: #409eff;
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
    color: #8c939d;
    width: 200px;
    height: 112px;
    line-height: 112px;
    text-align: center;
  }
}

.upload-tip {
  font-size: 12px;
  color: #999;
  margin-top: 5px;
}

.rich-text-editor {
  :deep(#editor-container) {
    border: 1px solid #dcdfe6;
    border-radius: 4px;
  }
}

.chapter-management {
  .chapter-toolbar {
    margin-bottom: 20px;
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

    .node-actions {
      margin-left: auto;
    }
  }
}

.publish-summary {
  text-align: center;
}

.step-actions {
  text-align: center;
  margin-top: 40px;
  padding-top: 20px;
  border-top: 1px solid #e8e8e8;
}
</style>

