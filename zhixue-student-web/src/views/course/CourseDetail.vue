<template>
  <div class="course-page">
    <div class="inner">
      <el-button text :icon="ArrowLeft" class="back" @click="goBack">返回</el-button>

      <p v-if="loading" class="state">加载中…</p>
      <p v-else-if="loadError" class="state error">
        {{ loadError }}
        <el-button link type="primary" @click="load">重试</el-button>
      </p>

      <template v-else-if="course">
        <section class="hero">
          <img v-if="course.coverUrl && !coverFailed" class="cover" :src="course.coverUrl" :alt="course.title" @error="coverFailed = true" />
          <div v-else class="cover cover-fallback" aria-hidden="true">{{ course.title }}</div>
          <div class="hero-body">
            <h1>{{ course.title }}</h1>
            <p v-if="course.subtitle" class="subtitle">{{ course.subtitle }}</p>
            <div class="price-row">
              <span class="price tabular-nums">{{ priceText }}</span>
              <el-tag v-if="purchased" type="success" effect="plain">已购买</el-tag>
            </div>
            <div class="actions">
              <el-button v-if="!course.canStudy" type="primary" size="large" :loading="buying" @click="purchase()">立即购买</el-button>
              <el-button v-else type="primary" size="large" @click="playFirst">开始学习</el-button>
              <el-button size="large" :icon="ChatLineSquare" @click="askThisCourse">问这门课</el-button>
            </div>
          </div>
        </section>

        <section v-if="course.description" class="block">
          <h2>课程简介</h2>
          <p class="desc">{{ course.description }}</p>
        </section>

        <section class="block">
          <h2>课程目录</h2>
          <p v-if="!chapters.length" class="muted">暂无课时</p>
          <div v-for="chapter in chapters" :key="chapter.chapterId" class="chapter">
            <h3>第 {{ chapter.chapterNo }} 章 · {{ chapter.chapterTitle }}</h3>
            <ul>
              <li v-for="lesson in chapter.lessons" :key="lesson.id">
                <button
                  type="button"
                  class="lesson"
                  :class="{ locked: !lesson.playable, current: lesson.id === highlightId }"
                  :data-lesson-id="lesson.id"
                  @click="openLesson(lesson)"
                >
                  <el-icon class="lesson-icon">
                    <VideoPlay v-if="lesson.playable" />
                    <Lock v-else />
                  </el-icon>
                  <span class="lesson-no tabular-nums">{{ lesson.no }}</span>
                  <span class="lesson-title">{{ lesson.title }}</span>
                  <el-tag v-if="lesson.preview && !course.canStudy" size="small" type="success" effect="plain">试看</el-tag>
                  <span class="lesson-dur">{{ lesson.durationText }}</span>
                </button>
              </li>
            </ul>
          </div>
        </section>
      </template>
    </div>

    <LessonPlayer v-model="playerOpen" :lesson="playing" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ChatLineSquare, Lock, VideoPlay } from '@element-plus/icons-vue'
import LessonPlayer from './LessonPlayer.vue'
import { getCourseChapters, getCourseDetail } from '@/api/course'
import { confirmMockPay, createOrder, payOrder } from '@/api/order'
import { buildLessonRows, formatPrice } from '@/utils/course'

const route = useRoute()
const router = useRouter()

const course = ref(null)
const chapters = ref([])
const loading = ref(false)
const loadError = ref('')
const coverFailed = ref(false)
const buying = ref(false)
const playerOpen = ref(false)
const playing = ref(null)
const highlightId = ref(null)

const priceText = computed(() => formatPrice(course.value?.price))
const purchased = computed(() => !!course.value?.canStudy && Number(course.value?.price) > 0)
const allLessons = computed(() => chapters.value.flatMap((chapter) => chapter.lessons))

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const id = String(route.params.id)
    const [detail, list] = await Promise.all([getCourseDetail(id), getCourseChapters(id)])
    course.value = detail
    chapters.value = buildLessonRows(list)
  } catch (e) {
    loadError.value = e.message || '课程加载失败'
  } finally {
    loading.value = false
  }
}

function play(lesson) {
  playing.value = lesson
  playerOpen.value = true
}

/** 可播放（已购或在试看范围内）直接播放；否则先确认是否购买 */
async function openLesson(lesson) {
  highlightId.value = lesson.id
  if (lesson.playable) {
    play(lesson)
    return
  }
  try {
    await ElMessageBox.confirm(`「${lesson.no} ${lesson.title}」需要购买课程后观看。`, '购买后观看', {
      confirmButtonText: `购买（${priceText.value}）`,
      cancelButtonText: '再看看',
      type: 'info'
    })
  } catch {
    return
  }
  await purchase(lesson.id)
}

function playFirst() {
  const first = allLessons.value.find((lesson) => lesson.playable)
  if (first) {
    play(first)
  }
}

/**
 * 下单 → 模拟收银台 → 确认付款 → 以服务端为准刷新目录。
 * pendingLessonId：从锁定课时进来时，付款后直接播放这一节。
 */
async function purchase(pendingLessonId) {
  if (buying.value) {
    return
  }
  buying.value = true
  try {
    const order = await createOrder(course.value.id)
    const pay = await payOrder(order.orderNo)
    const amount = Number(pay?.amount ?? order.amount ?? 0).toFixed(2)
    try {
      await ElMessageBox.confirm(`应付金额 ¥${amount}。本地联调环境，不会产生真实扣款。`, '模拟收银台', {
        confirmButtonText: '确认支付',
        cancelButtonText: '暂不支付'
      })
    } catch {
      ElMessage.info('订单已保留，可稍后继续支付')
      return
    }
    await confirmMockPay(order.orderNo)
    ElMessage.success('支付成功')
    await load()
    const lesson = pendingLessonId && allLessons.value.find((item) => item.id === String(pendingLessonId))
    if (lesson?.playable) {
      play(lesson)
    }
  } catch (e) {
    ElMessage.error(e.message || '购买失败')
  } finally {
    buying.value = false
  }
}

function askThisCourse() {
  router.push({ name: 'chat', query: { courseId: String(course.value.id), courseTitle: course.value.title } })
}

function goBack() {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.push({ name: 'chat' })
  }
}

// 从推荐卡片的课时进来：/course/:id?lesson=xxx，加载完后直接打开该课时（锁定则提示购买）
watch(
  () => route.params.id,
  async (id) => {
    if (!id) {
      return
    }
    playerOpen.value = false
    coverFailed.value = false
    await load()
    const target = route.query.lesson && allLessons.value.find((lesson) => lesson.id === String(route.query.lesson))
    if (target) {
      openLesson(target)
    }
  },
  { immediate: true }
)
</script>

<style lang="scss" scoped>
.course-page {
  height: 100%;
  overflow-y: auto;
}

.inner {
  max-width: 880px;
  margin: 0 auto;
  padding: 18px 24px 48px;
}

.back {
  margin: 0 0 12px -12px;
}

.state {
  padding: 60px 0;
  text-align: center;
  color: var(--el-text-color-secondary);

  &.error {
    color: var(--el-color-danger);
  }
}

.hero {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.cover {
  width: 100%;
  aspect-ratio: 16 / 9;
  object-fit: cover;
  border-radius: var(--zx-radius-card);
  background: var(--el-fill-color);
}

.cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: linear-gradient(135deg, var(--el-color-primary-light-8), var(--el-color-primary-light-9));
  color: var(--el-color-primary-dark-2);
  font-size: 18px;
  font-weight: 650;
  text-align: center;
}

.hero-body h1 {
  font-size: 24px;
  line-height: 1.35;
}

.subtitle {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
}

.price-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 16px;
}

.price {
  font-size: 24px;
  font-weight: 650;
  color: var(--el-color-danger);
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.block {
  margin-top: 28px;

  h2 {
    margin-bottom: 10px;
    font-size: 17px;
  }
}

.desc {
  line-height: 1.75;
  white-space: pre-wrap;
}

.muted {
  color: var(--el-text-color-secondary);
}

.chapter + .chapter {
  margin-top: 14px;
}

.chapter h3 {
  margin-bottom: 6px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}

.chapter ul {
  list-style: none;
  overflow: hidden;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--zx-radius-card);
}

.chapter li + li {
  border-top: 1px solid var(--el-border-color-lighter);
}

.lesson {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 11px 14px;
  border: 0;
  background: var(--zx-surface);
  color: var(--el-text-color-primary);
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

  &.locked {
    color: var(--el-text-color-secondary);
  }

  &.current {
    background: var(--el-color-primary-light-9);
  }
}

.lesson-icon {
  flex-shrink: 0;
}

.lesson-no {
  min-width: 2.4em;
  color: var(--el-text-color-secondary);
}

.lesson-title {
  flex: 1;
  min-width: 0;
}

.lesson-dur {
  flex-shrink: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

@media (max-width: 959px) {
  .inner {
    padding: 12px 14px 40px;
  }

  .hero {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}
</style>
