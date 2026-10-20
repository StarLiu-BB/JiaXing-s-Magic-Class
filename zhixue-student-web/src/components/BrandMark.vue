<template>
  <svg
    class="brand-mark"
    :width="size"
    :height="size"
    viewBox="0 0 32 32"
    fill="none"
    aria-hidden="true"
    focusable="false"
  >
    <rect width="32" height="32" rx="8" :fill="inverse ? '#ffffff' : 'var(--el-color-primary)'" />
    <path
      d="M7.5 10.8c3-1.3 5.8-1.1 8.5.9 2.7-2 5.5-2.2 8.5-.9v10.6c-3-1.3-5.8-1.1-8.5.9-2.7-2-5.5-2.2-8.5-.9V10.8Z"
      :stroke="inverse ? 'var(--el-color-primary)' : '#ffffff'"
      stroke-width="1.8"
      stroke-linejoin="round"
    />
    <path d="M16 11.7v10.6" :stroke="inverse ? 'var(--el-color-primary)' : '#ffffff'" stroke-width="1.8" stroke-linecap="round" />
  </svg>
</template>

<script setup>
defineProps({
  /** 像素尺寸 */
  size: { type: Number, default: 32 },
  /** 反色：白底蓝线，用于深色背景 */
  inverse: { type: Boolean, default: false }
})
</script>

<style scoped>
.brand-mark {
  display: block;
  flex-shrink: 0;
}
</style>
