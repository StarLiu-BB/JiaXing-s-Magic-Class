<template>
  <div class="composer">
    <div v-if="scope" class="scope">
      <span class="scope-tag">
        正在问：{{ scope.title || '指定课程' }}
        <button
          v-if="!scopeLocked"
          type="button"
          class="scope-clear"
          aria-label="改为问全部课程"
          @click="emit('clear-scope')"
        >
          <el-icon><Close /></el-icon>
        </button>
      </span>
      <span v-if="scopeLocked" class="scope-hint">想换范围请开新对话</span>
    </div>

    <div class="box" :class="{ invalid: !!error }">
      <textarea
        ref="inputRef"
        :value="modelValue"
        class="input"
        rows="1"
        :placeholder="generating ? '回答生成中，按 Esc 停止' : '问点什么…（Enter 发送，Shift + Enter 换行）'"
        aria-label="输入问题"
        :aria-invalid="!!error"
        :aria-describedby="error ? 'composer-error' : undefined"
        @input="onInput"
        @keydown="onKeydown"
      />
      <span v-if="nearLimit" class="count tabular-nums" :class="{ over: overLimit }">{{ length }}/{{ max }}</span>
      <el-button
        v-if="generating"
        class="action"
        :icon="VideoPause"
        aria-label="停止生成"
        @click="emit('stop')"
      >
        停止
      </el-button>
      <el-button
        v-else
        type="primary"
        class="action"
        :icon="Promotion"
        :disabled="disabled || !modelValue.trim()"
        aria-label="发送"
        @click="emit('send')"
      >
        发送
      </el-button>
    </div>
    <p v-if="error" id="composer-error" class="error" role="alert">{{ error }}</p>
    <p v-else class="tip">AI 回答可能有误，请结合课程内容判断</p>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { Close, Promotion, VideoPause } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  generating: { type: Boolean, default: false },
  /** 冷却中（409 后）或会话加载中 */
  disabled: { type: Boolean, default: false },
  /** { id, title } 限定课程；null 表示全部课程 */
  scope: { type: Object, default: null },
  scopeLocked: { type: Boolean, default: false },
  error: { type: String, default: '' },
  max: { type: Number, default: 500 }
})
const emit = defineEmits(['update:modelValue', 'send', 'stop', 'clear-scope'])

const MAX_HEIGHT = 200
const inputRef = ref(null)
const length = computed(() => props.modelValue.trim().length)
const nearLimit = computed(() => length.value > props.max - 100)
const overLimit = computed(() => length.value > props.max)

function onInput(event) {
  emit('update:modelValue', event.target.value)
}

function onKeydown(event) {
  if (event.key === 'Escape' && props.generating) {
    event.preventDefault()
    emit('stop')
    return
  }
  // 输入法组字时的 Enter 是确认候选词，不是发送
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) {
    return
  }
  event.preventDefault()
  if (!props.generating && !props.disabled) {
    emit('send')
  }
}

/** 高度随内容增长，最多 200px 后出现滚动条 */
function autosize() {
  const el = inputRef.value
  if (!el) {
    return
  }
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, MAX_HEIGHT)}px`
}

watch(() => props.modelValue, () => nextTick(autosize))

defineExpose({ focus: () => inputRef.value?.focus() })
</script>

<style lang="scss" scoped>
.composer {
  width: 100%;
}

.scope {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 13px;
}

.scope-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px 3px 10px;
  border-radius: 999px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary-dark-2);
}

.scope-clear {
  display: inline-flex;
  padding: 2px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: inherit;
  cursor: pointer;

  &:hover {
    background: var(--el-color-primary-light-8);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.scope-hint {
  color: var(--el-text-color-secondary);
}

.box {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 10px 10px 10px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 14px;
  background: var(--zx-surface);
  box-shadow: var(--el-box-shadow-lighter);
  transition: border-color 0.16s var(--zx-ease), box-shadow 0.16s var(--zx-ease);

  &:focus-within {
    border-color: var(--el-color-primary-light-5);
    box-shadow: var(--zx-ring);
  }

  &.invalid {
    border-color: var(--el-color-danger);
  }
}

.input {
  flex: 1;
  min-width: 0;
  max-height: 200px;
  padding: 6px 0;
  border: 0;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  font-size: 15px;
  line-height: 1.6;

  &::placeholder {
    color: var(--el-text-color-placeholder);
  }
}

.count {
  align-self: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);

  &.over {
    color: var(--el-color-danger);
  }
}

.action {
  flex-shrink: 0;
  height: 36px;
  border-radius: 10px;
}

.error,
.tip {
  margin-top: 6px;
  padding-left: 4px;
  font-size: 12px;
}

.error {
  color: var(--el-color-danger);
}

.tip {
  color: var(--el-text-color-placeholder);
}
</style>
