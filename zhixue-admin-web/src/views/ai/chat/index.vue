<template>
  <div class="ai-chat-container">
    <!-- 左侧知识库管理 -->
    <div class="sidebar">
      <div class="sidebar-header">
        <h3>知识库管理</h3>
      </div>
      <div class="upload-section">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :on-change="handleFileChange"
          accept=".txt,.md"
        >
          <template #trigger>
            <el-button type="primary" size="small">选择文件</el-button>
          </template>
        </el-upload>
        <el-input
          v-model="uploadForm.title"
          placeholder="文档标题"
          size="small"
          style="margin-top: 10px"
        />
        <el-input
          v-model="uploadForm.tags"
          placeholder="标签（逗号分隔）"
          size="small"
          style="margin-top: 10px"
        />
        <el-button
          type="success"
          size="small"
          style="margin-top: 10px; width: 100%"
          :loading="uploading"
          @click="handleUpload"
        >
          上传到知识库
        </el-button>
      </div>
    </div>

    <!-- 右侧对话区域 -->
    <div class="chat-area">
      <div class="chat-header">
        <h2>AI 智能助手</h2>
        <span class="subtitle">基于通义千问，为您提供课程答疑服务</span>
      </div>

      <!-- 对话消息列表 -->
      <div class="chat-messages" ref="messagesRef">
        <div v-if="messages.length === 0" class="welcome-message">
          <div class="welcome-icon"><el-icon :size="24"><ChatDotRound /></el-icon></div>
          <h3>欢迎使用 AI 智能助手</h3>
          <p>您可以问我任何关于课程的问题，例如：</p>
          <div class="suggestions">
            <el-tag 
              v-for="suggestion in suggestions" 
              :key="suggestion"
              @click="askQuestion(suggestion)"
              class="suggestion-tag"
            >
              {{ suggestion }}
            </el-tag>
          </div>
        </div>

        <div
          v-for="(msg, index) in messages"
          :key="index"
          :class="['message', msg.role]"
        >
          <div class="avatar" :aria-label="msg.role === 'user' ? '我' : 'AI 助手'">
            <el-icon :size="18"><User v-if="msg.role === 'user'" /><Cpu v-else /></el-icon>
          </div>
          <div class="content">
            <div class="text" v-html="formatMessage(msg.content)"></div>
            <div class="time">{{ msg.time }}</div>
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

      <!-- 输入区域 -->
      <div class="chat-input">
        <el-input
          v-model="inputMessage"
          placeholder="请输入您的问题..."
          :disabled="loading"
          @keyup.enter="sendMessage"
        >
          <template #append>
            <el-button
              type="primary"
              :loading="loading"
              @click="sendMessage"
            >
              发送
            </el-button>
          </template>
        </el-input>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, User, Cpu } from '@element-plus/icons-vue'
import request from '@/utils/request'

// 对话消息
const messages = ref([])
const inputMessage = ref('')
const loading = ref(false)
const messagesRef = ref(null)

// 知识库上传
const uploadRef = ref(null)
const uploading = ref(false)
const uploadForm = ref({
  title: '',
  tags: '',
  file: null
})

// 建议问题
const suggestions = [
  '什么是 Java？',
  '如何学习 Spring Boot？',
  'MySQL 和 Redis 有什么区别？',
  '什么是微服务架构？'
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

// 滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (messagesRef.value) {
      messagesRef.value.scrollTop = messagesRef.value.scrollHeight
    }
  })
}

// 获取当前时间
const getCurrentTime = () => {
  const now = new Date()
  return now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

// 发送消息
const sendMessage = async () => {
  if (!inputMessage.value.trim() || loading.value) return

  const question = inputMessage.value.trim()
  inputMessage.value = ''

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: question,
    time: getCurrentTime()
  })
  scrollToBottom()

  // 调用 AI 接口
  loading.value = true
  try {
    const res = await request({
      url: '/ai/chat/course',
      method: 'get',
      params: { question }
    })
    
    // 添加 AI 回复
    messages.value.push({
      role: 'assistant',
      content: res.data || res || '抱歉，我暂时无法回答这个问题。',
      time: getCurrentTime()
    })
  } catch (error) {
    messages.value.push({
      role: 'assistant',
      content: '抱歉，服务暂时不可用，请稍后再试。',
      time: getCurrentTime()
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

// 点击建议问题
const askQuestion = (question) => {
  inputMessage.value = question
  sendMessage()
}

// 处理文件选择
const handleFileChange = (file) => {
  uploadForm.value.file = file.raw
}

// 上传文件到知识库
const handleUpload = async () => {
  if (!uploadForm.value.file) {
    ElMessage.warning('请先选择文件')
    return
  }
  if (!uploadForm.value.title) {
    ElMessage.warning('请输入文档标题')
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', uploadForm.value.file)
    formData.append('title', uploadForm.value.title)
    formData.append('source', 'manual')
    formData.append('tags', uploadForm.value.tags || '')

    await request({
      url: '/ai/kb/upload',
      method: 'post',
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    ElMessage.success('文档上传成功')
    uploadForm.value = { title: '', tags: '', file: null }
    uploadRef.value?.clearFiles()
  } catch (error) {
    ElMessage.error('上传失败，请重试')
  } finally {
    uploading.value = false
  }
}
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
  background: var(--el-fill-color-lighter);
  border-right: 1px solid var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;

  .sidebar-header {
    padding: 20px;
    border-bottom: 1px solid var(--el-border-color-lighter);
    h3 {
      margin: 0;
      font-size: 15px;
      color: var(--el-text-color-primary);
    }
  }

  .upload-section {
    padding: 20px;
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
        color: rgba(255,255,255,0.7);
      }
    }

    &.assistant {
      .content {
        background: var(--el-fill-color-light);
        color: var(--el-text-color-primary);
        border-radius: 12px 12px 12px 4px;
      }
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
