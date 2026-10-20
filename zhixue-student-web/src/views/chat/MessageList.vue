<template>
  <ol class="message-list" aria-live="polite">
    <li v-for="message in messages" :key="message.key" class="row" :class="message.role">
      <template v-if="message.role === 'user'">
        <div class="bubble">{{ message.content }}</div>
        <div v-if="message.unanswered" class="unanswered">
          此问题未完成回答
          <el-button link type="primary" :disabled="busy" @click="emit('retry', message.content)">重试</el-button>
        </div>
      </template>

      <article v-else class="answer" :data-status="message.status" :aria-busy="message.status === 'streaming'">
        <div class="avatar" aria-hidden="true">AI</div>
        <div class="body">
          <p v-if="message.status === 'streaming' && !message.content" class="thinking">
            正在思考<span class="dots" aria-hidden="true" />
          </p>
          <AnswerMarkdown v-if="message.content" :content="message.content" />
          <span v-if="message.status === 'streaming' && message.content" class="cursor" aria-hidden="true" />

          <p v-if="message.status === 'stopped'" class="state stopped">已停止</p>

          <div v-if="message.status === 'failed' || message.status === 'interrupted'" class="failure" role="alert">
            <el-icon><WarningFilled /></el-icon>
            <span>{{ message.error?.msg || 'AI 服务暂不可用，请稍后再试' }}</span>
            <el-button
              v-if="message.question"
              link
              type="primary"
              :icon="RefreshRight"
              :disabled="busy"
              @click="emit('retry', message.question)"
            >
              重试
            </el-button>
          </div>

          <template v-if="message.status === 'done'">
            <p v-if="message.sources.length" class="sources">
              参考资料：{{ message.sources.map((source) => source.title).join('、') }}
            </p>
            <p v-if="message.notice" class="notice">{{ message.notice }}</p>
            <RecommendationCards
              v-if="message.recommendations.length"
              :cards="message.recommendations"
              @open-course="(card) => emit('open-course', card)"
              @open-lesson="(card, lesson) => emit('open-lesson', card, lesson)"
            />
          </template>
        </div>
      </article>
    </li>
  </ol>
</template>

<script setup>
import { RefreshRight, WarningFilled } from '@element-plus/icons-vue'
import AnswerMarkdown from './AnswerMarkdown.vue'
import RecommendationCards from './RecommendationCards.vue'

defineProps({
  /** chatSession 的 state.messages */
  messages: { type: Array, required: true },
  /** 正在生成或冷却中：重试按钮暂不可用 */
  busy: { type: Boolean, default: false }
})
const emit = defineEmits(['retry', 'open-course', 'open-lesson'])
</script>

<style lang="scss" scoped>
.message-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.row.user {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.bubble {
  max-width: 82%;
  padding: 10px 14px;
  border-radius: 16px 16px 4px 16px;
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
  font-size: 15px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.unanswered {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.answer {
  display: flex;
  gap: 12px;
}

.avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
  border-radius: 8px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.body {
  flex: 1;
  min-width: 0;
}

.thinking {
  color: var(--el-text-color-secondary);
}

.dots::after {
  content: '…';
  animation: blink 1.2s steps(1) infinite;
}

.cursor {
  display: inline-block;
  width: 7px;
  height: 1.1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  border-radius: 1px;
  background: var(--el-color-primary);
  animation: blink 1s steps(1) infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.state {
  margin-top: 8px;
  font-size: 12px;
}

.stopped {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}

.failure {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 12px;
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: 10px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  font-size: 14px;
}

.sources,
.notice {
  margin-top: 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.notice {
  color: var(--el-text-color-placeholder);
}
</style>
