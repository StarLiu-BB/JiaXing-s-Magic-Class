<template>
  <!-- renderMarkdown 禁用了原始 HTML、只放行 http(s) 链接、不加载图片，输出可以安全地用于 v-html -->
  <div class="answer-md" v-html="html" />
</template>

<script setup>
import { computed } from 'vue'
import 'highlight.js/styles/github.css'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps({
  content: { type: String, default: '' }
})

const html = computed(() => renderMarkdown(props.content))
</script>

<style lang="scss" scoped>
.answer-md {
  color: var(--el-text-color-primary);
  font-size: 15px;
  line-height: 1.75;
  word-break: break-word;

  :deep(p),
  :deep(ul),
  :deep(ol),
  :deep(pre),
  :deep(blockquote),
  :deep(table) {
    margin: 0 0 0.75em;
  }

  :deep(> :last-child) {
    margin-bottom: 0;
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 1.4em;
  }

  :deep(li) {
    margin: 0.2em 0;
  }

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 1em 0 0.5em;
    font-size: 16px;
  }

  :deep(code) {
    padding: 0.1em 0.35em;
    border-radius: 4px;
    background: var(--el-fill-color);
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 13px;
  }

  :deep(pre) {
    overflow-x: auto;
    padding: 12px 14px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
    background: var(--el-fill-color-lighter);
  }

  :deep(pre code) {
    padding: 0;
    background: none;
    line-height: 1.6;
  }

  :deep(a) {
    color: var(--el-color-primary);
    text-decoration: underline;
    text-underline-offset: 2px;
  }

  :deep(blockquote) {
    padding-left: 12px;
    border-left: 3px solid var(--el-border-color);
    color: var(--el-text-color-secondary);
  }

  :deep(table) {
    border-collapse: collapse;
  }

  :deep(th),
  :deep(td) {
    padding: 4px 10px;
    border: 1px solid var(--el-border-color-light);
  }
}
</style>
