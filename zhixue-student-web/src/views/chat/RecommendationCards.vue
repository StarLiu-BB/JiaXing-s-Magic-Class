<template>
  <section class="reco" aria-label="相关视频课">
    <h3 class="reco-title">相关视频课</h3>
    <article v-for="card in cards" :key="card.courseId" class="card">
      <button type="button" class="course" @click="emit('open-course', card)">
        <img v-if="card.coverUrl" class="cover" :src="card.coverUrl" alt="" loading="lazy" />
        <span class="course-title">{{ card.title }}</span>
        <span class="course-meta">
          <span class="badge tabular-nums" :class="badgeOf(card).tone">{{ badgeOf(card).text }}</span>
          <el-tag v-if="badgeOf(card).preview" size="small" effect="plain">可试看</el-tag>
        </span>
      </button>
      <ul v-if="card.lessons?.length" class="lessons">
        <li v-for="lesson in card.lessons" :key="lesson.lessonId">
          <button
            type="button"
            class="lesson"
            :class="{ locked: !lesson.playable }"
            :data-lesson-id="lesson.lessonId"
            :aria-label="`${lesson.playable ? '播放' : '需购买'}：${lesson.chapterIndex}.${lesson.lessonIndex} ${lesson.title}`"
            @click="emit('open-lesson', card, lesson)"
          >
            <el-icon class="lesson-icon">
              <VideoPlay v-if="lesson.playable" />
              <Lock v-else />
            </el-icon>
            <span class="no tabular-nums">{{ lesson.chapterIndex }}.{{ lesson.lessonIndex }}</span>
            <span class="title">{{ lesson.title }}</span>
            <span class="dur">{{ formatDuration(lesson.duration) }}</span>
          </button>
        </li>
      </ul>
    </article>
  </section>
</template>

<script setup>
import { Lock, VideoPlay } from '@element-plus/icons-vue'
import { courseBadge as badgeOf, formatDuration } from '@/utils/course'

defineProps({
  /** done 事件或历史消息中的 recommendations */
  cards: { type: Array, required: true }
})
const emit = defineEmits(['open-course', 'open-lesson'])
</script>

<style lang="scss" scoped>
.reco {
  margin-top: 16px;
}

.reco-title {
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.card {
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--zx-radius-card);
  background: var(--zx-surface);

  & + .card {
    margin-top: 10px;
  }
}

.course {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: 0;
  background: transparent;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: var(--el-fill-color-lighter);
  }

  &:focus-visible {
    outline: none;
    box-shadow: inset var(--zx-ring);
  }
}

.cover {
  width: 72px;
  height: 41px;
  flex-shrink: 0;
  object-fit: cover;
  border-radius: 6px;
  background: var(--el-fill-color);
}

.course-title {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.course-meta {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
}

.badge {
  font-size: 13px;
  font-weight: 600;

  &.price {
    color: var(--el-color-danger);
  }

  &.success {
    color: var(--el-color-success);
  }
}

.lessons {
  list-style: none;
  border-top: 1px solid var(--el-border-color-lighter);
}

.lesson {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 12px 8px 20px;
  border: 0;
  background: transparent;
  color: var(--el-text-color-regular);
  font: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: var(--el-fill-color-lighter);
    color: var(--el-color-primary);
  }

  &:focus-visible {
    outline: none;
    box-shadow: inset var(--zx-ring);
  }

  &.locked {
    color: var(--el-text-color-secondary);
  }
}

.lesson-icon {
  flex-shrink: 0;
}

.no {
  min-width: 2.2em;
  color: var(--el-text-color-secondary);
}

.title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dur {
  flex-shrink: 0;
  color: var(--el-text-color-secondary);
}
</style>
