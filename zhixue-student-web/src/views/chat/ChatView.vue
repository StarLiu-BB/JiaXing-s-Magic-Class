<template>
  <div class="chat-view">
    <div ref="scrollRef" class="scroll" @scroll="onScroll">
      <div class="column">
        <p v-if="state.loading" class="loading">加载中…</p>

        <section v-else-if="!state.messages.length" class="empty">
          <h1>有什么想学的？</h1>
          <p>问编程问题，AI 课程助教会结合课程资料回答，并推荐相关的视频课</p>
          <div class="suggestions">
            <button
              v-for="question in SUGGESTIONS"
              :key="question"
              type="button"
              class="suggestion"
              :disabled="busy"
              @click="session.send(question)"
            >
              {{ question }}
            </button>
          </div>
        </section>

        <MessageList
          v-else
          :messages="state.messages"
          :busy="busy"
          @retry="(question) => session.retry(question)"
          @open-course="openCourse"
          @open-lesson="openLesson"
        />
      </div>
    </div>

    <footer class="footer">
      <div class="column">
        <Composer
          ref="composerRef"
          v-model="state.draft"
          :generating="state.generating"
          :disabled="state.cooldown || state.loading"
          :scope="state.courseScope"
          :scope-locked="state.scopeLocked"
          :error="state.inputError"
          :max="MAX_QUESTION"
          @send="session.send()"
          @stop="session.stop()"
          @clear-scope="clearScope"
        />
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Composer from './Composer.vue'
import MessageList from './MessageList.vue'
import { MAX_QUESTION, createChatSession } from './chatSession'
import { askStream } from '@/api/chat'
import { getConversation, getConversationMessages } from '@/api/conversations'
import { getCourseDetail } from '@/api/course'
import { useConversationsStore } from '@/stores/conversations'
import { useUserStore } from '@/stores/user'
import { CHAT_DRAFT_KEY } from '@/utils/auth'
import { sessionExpired } from '@/utils/session'

const SUGGESTIONS = ['HashMap 为什么线程不安全', '线程池参数怎么设', 'String 为什么是不可变的']
/** 距底部多少像素以内算「在底部」：在底部时新内容自动跟随，学生往上翻看时不打扰 */
const STICK_THRESHOLD = 80

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const conversations = useConversationsStore()

const scrollRef = ref(null)
const composerRef = ref(null)
let stickToBottom = true

const session = createChatSession({
  askStream: (body, options) => askStream(body, { ...options, token: user.token }),
  loadMessages: getConversationMessages,
  onConversationCreated(meta) {
    const id = String(meta.conversationId)
    conversations.upsert({ id, title: meta.title, courseId: state.courseScope?.id ?? null })
    // 只换地址不重建页面：两条路由共用这个组件实例，watch 发现 ID 与当前会话相同就不会重新加载
    router.replace({ name: 'conversation', params: { id } })
  },
  onActivity: (id) => conversations.bump(id),
  onUnauthorized() {
    // 问题已放回输入框；登录回来后从这里恢复
    sessionStorage.setItem(CHAT_DRAFT_KEY, state.draft)
    sessionExpired()
  },
  onConversationGone() {
    conversations.load().catch(() => {})
    router.replace({ name: 'chat' })
  },
  notify: (type, message) => ElMessage({ type, message })
})
const state = session.state
const busy = computed(() => state.generating || state.cooldown)

/** 历史会话只存了课程 ID：取一次课程标题用于「正在问：…」标签 */
async function fillScopeTitle(conversationId, courseId) {
  try {
    const detail = await getCourseDetail(courseId)
    if (state.conversationId === conversationId && state.courseScope) {
      state.courseScope.title = detail.title
    }
  } catch {
    // 取不到标题时显示「指定课程」
  }
}

/** 会话的课程范围：「最近」只加载了前 50 条，不在列表里（直接输入地址或很久以前）时再问一次接口 */
async function courseOf(conversationId) {
  await conversations.ensureLoaded().catch(() => {})
  const known = conversations.find(conversationId)
  if (known) {
    return known.courseId
  }
  try {
    const conversation = await getConversation(conversationId)
    return conversation?.courseId ?? null
  } catch {
    return null
  }
}

async function openConversation(id) {
  // 先同步中止旧回答：查课程范围要等网络，期间旧流的 meta 不能再改地址
  session.detach()
  const courseId = await courseOf(id)
  // 等待期间学生又点了别的会话或「新对话」：以最新的地址为准
  if (route.name !== 'conversation' || String(route.params.id) !== id) {
    return
  }
  await session.open(id, courseId ? { id: courseId, title: '' } : null)
  if (courseId) {
    fillScopeTitle(id, courseId)
  }
  scrollToBottom(true)
}

function startNew() {
  session.reset()
  if (route.query.courseId) {
    session.setScope({ id: route.query.courseId, title: route.query.courseTitle || '' })
  }
  nextTick(() => composerRef.value?.focus())
}

watch(
  () => [route.name, route.params.id, route.query.courseId],
  ([name, id]) => {
    if (name === 'conversation') {
      if (String(id) !== state.conversationId) {
        openConversation(String(id))
      }
    } else if (name === 'chat') {
      startNew()
    }
  },
  { immediate: true }
)

function clearScope() {
  session.clearScope()
  if (route.query.courseId) {
    router.replace({ name: 'chat' })
  }
}

function openCourse(card) {
  router.push({ name: 'course', params: { id: card.courseId } })
}

/** 课程详情页按 lesson 参数打开该课时：可播放直接播放，否则提示购买 */
function openLesson(card, lesson) {
  router.push({ name: 'course', params: { id: card.courseId }, query: { lesson: lesson.lessonId } })
}

function onScroll() {
  const el = scrollRef.value
  stickToBottom = el.scrollHeight - el.scrollTop - el.clientHeight < STICK_THRESHOLD
}

function scrollToBottom(force = false) {
  nextTick(() => {
    const el = scrollRef.value
    if (el && (force || stickToBottom)) {
      el.scrollTop = el.scrollHeight
    }
  })
}

watch(
  () => {
    const last = state.messages[state.messages.length - 1]
    return [state.messages.length, last?.content.length, last?.status, last?.recommendations?.length]
  },
  ([length], [previousLength] = []) => scrollToBottom(length > (previousLength ?? 0))
)

onMounted(() => {
  const draft = sessionStorage.getItem(CHAT_DRAFT_KEY)
  if (draft) {
    sessionStorage.removeItem(CHAT_DRAFT_KEY)
    state.draft = draft
  }
  // 首次进入时 watch 先于挂载执行，那时输入框还不存在，这里补一次聚焦
  if (!state.conversationId) {
    composerRef.value?.focus()
  }
})

// 离开对话页（例如点推荐卡片去课程详情）：进行中的回答按「已停止」保存，回来时从服务端重新加载；
// 同时取消 409 冷却后的自动重发，否则它会在后台发起一条没人能中止的流
onBeforeUnmount(() => {
  session.stop()
  session.detach()
})
</script>

<style lang="scss" scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 28px 24px 16px;
}

.column {
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
}

.loading {
  padding: 60px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.empty {
  padding: 14vh 0 24px;
  text-align: center;

  h1 {
    font-size: 28px;
    letter-spacing: -0.02em;
  }

  p {
    margin-top: 8px;
    color: var(--el-text-color-secondary);
  }
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 28px;
}

.suggestion {
  padding: 8px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 999px;
  background: var(--zx-surface);
  color: var(--el-text-color-regular);
  font: inherit;
  cursor: pointer;
  transition: border-color 0.16s var(--zx-ease), color 0.16s var(--zx-ease);

  &:hover:not(:disabled) {
    border-color: var(--el-color-primary-light-5);
    color: var(--el-color-primary);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
}

.footer {
  flex-shrink: 0;
  padding: 8px 24px 16px;
  background: linear-gradient(to bottom, transparent, var(--zx-surface) 24px);
}

@media (max-width: 959px) {
  .scroll {
    padding: 16px 14px 12px;
  }

  .footer {
    padding: 6px 12px 12px;
  }

  .empty {
    padding-top: 8vh;

    h1 {
      font-size: 22px;
    }
  }
}
</style>
