<template>
  <el-dialog
    :model-value="modelValue"
    :title="lesson ? `${lesson.no} ${lesson.title}` : ''"
    width="min(880px, 94vw)"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="player">
      <video v-if="lesson?.videoUrl && !failed" :src="lesson.videoUrl" controls autoplay playsinline @error="failed = true" />
      <p v-else class="unavailable">视频暂不可用</p>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** buildLessonRows 产出的课时：{ no, title, videoUrl } */
  lesson: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue'])
const failed = ref(false)

watch(() => props.lesson, () => {
  failed.value = false
})
</script>

<style lang="scss" scoped>
.player {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  border-radius: 8px;
  background: #0f172a;

  video {
    width: 100%;
    height: 100%;
  }
}

.unavailable {
  color: #cbd5e1;
}
</style>
