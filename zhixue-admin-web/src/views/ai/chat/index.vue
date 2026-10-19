<template>
  <div class="ai-chat-container">
    <!-- 左侧：答疑范围 + 知识库上传 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <h3>答疑范围</h3>
      </div>
      <div class="upload-section">
        <el-select
          v-model="courseId"
          clearable
          filterable
          placeholder="全部课程"
          style="width: 100%"
          :loading="coursesLoading"
        >
          <el-option v-for="c in courses" :key="c.id" :label="c.title" :value="c.id" />
        </el-select>
        <p class="hint">选择课程后只检索该课程资料与通用资料；不选则检索全部知识库。</p>
      </div>

      <template v-if="canUpload">
        <div class="sidebar-header">
          <h3>上传知识</h3>
        </div>
        <div class="upload-section">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            :on-change="handleFileChange"
            :on-remove="() => (uploadForm.file = null)"
            accept=".txt,.md"
          >
            <template #trigger>
              <el-button size="small">选择 .txt / .md 文件</el-button>
            </template>
          </el-upload>
          <el-input
            v-model="uploadForm.title"
            placeholder="文档标题（默认用文件名）"
            size="small"
            maxlength="100"
            style="margin-top: 10px"
          />
          <p class="hint">
            {{ courseId ? `归属课程：${courseTitle}` : '未选择课程：作为通用知识，所有课程都可检索到' }}
          </p>
          <el-button
            type="primary"
            size="small"
            style="margin-top: 10px; width: 100%"
            :loading="uploading"
            @click="handleUpload"
          >
            上传到知识库
          </el-button>
        </div>
      </template>
    </div>

    <!-- 右侧对话区域 -->
    <div class="chat-area">
      <div class="chat-header">
        <h2>AI 课程助教</h2>
        <span class="subtitle">由本机部署的开源模型回答，答案依据课程知识库生成，仅供参考</span>
      </div>

      <div ref="messagesRef" class="chat-messages">
        <div v-if="messages.length === 0" class="welcome-message">
          <div class="welcome-icon"><el-icon :size="24"><ChatDotRound /></el-icon></div>
          <h3>有什么课程问题？</h3>
          <p>可以试试这些问题：</p>
          <div class="suggestions">
            <el-tag
              v-for="suggestion in suggestions"
              :key="suggestion"
              class="suggestion-tag"
              @click="askQuestion(suggestion)"
            >
              {{ suggestion }}
            </el-tag>
          </div>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message', msg.role, { failed: msg.failed }]"
        >
          <div class="avatar" :aria-label="msg.role === 'user' ? '我' : 'AI 助手'">
            <el-icon :size="18"><User v-if="msg.role === 'user'" /><Cpu v-else /></el-icon>
          </div>
          <div class="content">
            <div class="text" v-html="formatMessage(msg.content)"></div>
            <div class="time">{{ msg.time }}<template v-if="msg.cost"> · 用时 {{ msg.cost }} 秒</template></div>
          </div>
        </div>

        <div v-if="loading" class="message assistant">
          <div class="avatar" aria-label="AI 助手"><el-icon :size="18"><Cpu /></el-icon></div>
          <div class="content">
            <div class="typing">
              <span></span>
              <span></span>
              <span></span>
            </div>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input
          v-model="inputMessage"
          placeholder="输入问题，回车发送"
          maxlength="500"
          :disabled="loading"
          @keyup.enter="sendMessage"
        >
          <template #append>
            <el-button type="primary" :loading="loading" @click="sendMessage">发送</el-button>
          </template>
        </el-input>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, User, Cpu } from '@element-plus/icons-vue'
import { askCourse, uploadKnowledge } from '@/api/ai/chat'
import { listCourse } from '@/api/course/course'
import { useUserStore } from '@/stores/modules/user'

defineOptions({ name: 'AiChat' })

const userStore = useUserStore()
const canUpload = computed(() => (userStore.permissions || []).includes('ai:kb:edit'))

const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const messagesRef = ref(null)

// 答疑范围
const courses = ref([])
const coursesLoading = ref(false)
const courseId = ref(null)
const courseTitle = computed(() => courses.value.find((c) => c.id === courseId.value)?.title || '')

// 知识库上传
const uploadRef = ref(null)
const uploading = ref(false)
const uploadForm = ref({ title: '', file: null })

const suggestions = [
  '网关在微服务里起什么作用？',
  'RBAC 权限模型是怎么落地的？',
  'Pinia 适合管理哪些状态？',
  '购买课程后怎么开始学习？'
]

// 格式化消息（支持换行与 **加粗**）。
// 结果经 v-html 渲染，必须先转义：AI 回复可能受知识库内容影响，用户输入同样不可信，
// 未转义时一段 <img onerror> 就能在管理员页面执行脚本并读走 localStorage 中的令牌。
const escapeHtml = (text) => text
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')

const formatMessage = (text) => {
  if (!text) return ''
  return escapeHtml(String(text))
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
}

const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

const getCurrentTime = () => new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })

const loadCourses = async () => {
  coursesLoading.value = true
  try {
    const res = await listCourse({ pageNum: 1, pageSize: 100 })
    courses.value = res?.data?.records || []
  } catch (error) {
    // 无课程查看权限时不提供课程筛选，仍可按全部知识库提问
    courses.value = []
  } finally {
    coursesLoading.value = false
  }
}

const sendMessage = async () => {
  const question = inputMessage.value.trim()
  if (!question || loading.value) return
  inputMessage.value = ''
  messages.value.push({ role: 'user', content: question, time: getCurrentTime() })
  scrollToBottom()

  loading.value = true
  const start = Date.now()
  try {
    const answer = await askCourse(question, courseId.value)
    messages.value.push({
      role: 'assistant',
      content: answer || '模型没有给出回答，请换个问法再试。',
      time: getCurrentTime(),
      cost: ((Date.now() - start) / 1000).toFixed(1)
    })
  } catch (error) {
    // 失败如实显示原因，不伪装成 AI 的回答
    messages.value.push({
      role: 'assistant',
      failed: true,
      content: `没有拿到回答：${error?.message || '服务暂时不可用'}`,
      time: getCurrentTime()
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

const askQuestion = (question) => {
  inputMessage.value = question
  sendMessage()
}

const handleFileChange = (file) => {
  uploadForm.value.file = file.raw
}

const handleUpload = async () => {
  if (!uploadForm.value.file) {
    ElMessage.warning('请先选择文件')
    return
  }
  uploading.value = true
  try {
    await uploadKnowledge({
      file: uploadForm.value.file,
      title: uploadForm.value.title.trim(),
      courseId: courseId.value
    })
    ElMessage.success('已上传，可以立即提问')
    uploadForm.value = { title: '', file: null }
    uploadRef.value?.clearFiles()
  } catch (error) {
    // 失败原因已由请求拦截器提示
  } finally {
    uploading.value = false
  }
}

onMounted(loadCourses)
</script>

<style lang="scss" scoped>
.ai-chat-container {
  display: flex;
  height: calc(100vh - 152px);
  min-height: 520px;
  overflow: hidden;
  background: var(--zx-surface);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--zx-radius-card);
}

.sidebar {
  width: 280px;
  flex-shrink: 0;
  background: var(--el-fill-color-lighter);
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  overflow-y: auto;

  .sidebar-header {
    padding: 16px 20px 0;
    h3 {
      margin: 0;
      font-size: 14px;
      color: var(--el-text-color-primary);
    }
  }

  .upload-section {
    padding: 12px 20px 16px;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .hint {
    margin-top: 8px;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-secondary);
  }
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--zx-surface);
}

.chat-header {
  padding: 16px 24px;
  border-bottom: 1px solid var(--el-border-color-lighter);

  h2 {
    margin: 0 0 2px 0;
    font-size: 16px;
    color: var(--el-text-color-primary);
  }

  .subtitle {
    color: var(--el-text-color-secondary);
    font-size: 13px;
  }
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;

  .welcome-message {
    text-align: center;
    padding: 64px 20px;
    color: var(--el-text-color-secondary);

    .welcome-icon {
      width: 48px;
      height: 48px;
      margin: 0 auto 16px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 12px;
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
    }

    h3 {
      margin: 0 0 8px 0;
      font-size: 16px;
      color: var(--el-text-color-primary);
    }

    .suggestions {
      margin-top: 20px;
      display: flex;
      flex-wrap: wrap;
      justify-content: center;
      gap: 10px;
    }

    .suggestion-tag {
      cursor: pointer;
      transition: background-color 0.16s var(--zx-ease), color 0.16s var(--zx-ease);
      &:hover {
        background: var(--el-color-primary);
        color: #fff;
      }
    }
  }

  .message {
    display: flex;
    margin-bottom: 20px;
    gap: 12px;

    &.user {
      flex-direction: row-reverse;
      .content {
        background: var(--el-color-primary);
        color: #fff;
        border-radius: 12px 12px 4px 12px;
      }
      .avatar {
        color: var(--el-color-primary);
        background: var(--el-color-primary-light-9);
      }
      .time {
        text-align: right;
        color: rgba(255, 255, 255, 0.7);
      }
    }

    &.assistant {
      .content {
        background: var(--el-fill-color-light);
        color: var(--el-text-color-primary);
        border-radius: 12px 12px 12px 4px;
      }
    }

    &.failed .content {
      background: var(--el-color-danger-light-9);
      color: var(--el-color-danger);
    }

    .avatar {
      width: 34px;
      height: 34px;
      border-radius: 9px;
      color: var(--el-text-color-secondary);
      background: var(--el-fill-color);
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .content {
      max-width: 70%;
      padding: 12px 16px;

      .text {
        line-height: 1.6;
        word-break: break-word;
      }

      .time {
        font-size: 12px;
        color: var(--el-text-color-secondary);
        margin-top: 6px;
      }
    }

    .typing {
      display: flex;
      gap: 4px;
      span {
        width: 8px;
        height: 8px;
        background: var(--el-text-color-placeholder);
        border-radius: 50%;
        animation: typing 1.4s infinite ease-in-out;
        &:nth-child(2) { animation-delay: 0.2s; }
        &:nth-child(3) { animation-delay: 0.4s; }
      }
    }
  }
}

@keyframes typing {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
  40% { transform: scale(1); opacity: 1; }
}

.chat-input {
  padding: 16px 24px 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
