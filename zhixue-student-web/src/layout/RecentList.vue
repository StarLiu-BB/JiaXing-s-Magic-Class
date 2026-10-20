<template>
  <section class="recent" aria-labelledby="recent-title">
    <h2 id="recent-title" class="recent-title">最近</h2>

    <p v-if="store.loading && !store.items.length" class="hint">加载中…</p>
    <p v-else-if="store.error && !store.items.length" class="hint">
      加载失败
      <button type="button" class="text-btn" @click="store.load().catch(() => {})">重试</button>
    </p>
    <p v-else-if="!store.items.length" class="hint">还没有对话</p>

    <ul v-else class="recent-list">
      <li v-for="item in store.items" :key="item.id" class="recent-item" :class="{ active: item.id === activeId }">
        <router-link
          :to="{ name: 'conversation', params: { id: item.id } }"
          class="recent-link"
          :title="item.title"
          @click="emit('navigate')"
        >
          {{ item.title }}
        </router-link>
        <el-dropdown trigger="click" placement="bottom-end" @command="(command) => onCommand(command, item)">
          <button type="button" class="more-btn" :aria-label="`「${item.title}」的更多操作`">
            <el-icon><MoreFilled /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="rename" :icon="Edit">重命名</el-dropdown-item>
              <el-dropdown-item command="delete" :icon="Delete" divided>删除</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </li>
    </ul>

    <button v-if="store.hasMore" type="button" class="load-more" :disabled="store.loading" @click="store.loadMore().catch(() => {})">
      {{ store.loading ? '加载中…' : '加载更多' }}
    </button>
  </section>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, MoreFilled } from '@element-plus/icons-vue'
import { useConversationsStore } from '@/stores/conversations'

const TITLE_MAX = 64

const emit = defineEmits(['navigate'])
const store = useConversationsStore()
const route = useRoute()
const router = useRouter()
const activeId = computed(() => (route.name === 'conversation' ? String(route.params.id) : null))

onMounted(() => {
  store.ensureLoaded().catch(() => {})
})

function onCommand(command, item) {
  if (command === 'rename') {
    rename(item)
  } else if (command === 'delete') {
    remove(item)
  }
}

async function rename(item) {
  let value
  try {
    ;({ value } = await ElMessageBox.prompt('', '重命名对话', {
      inputValue: item.title,
      inputPlaceholder: `1–${TITLE_MAX} 个字`,
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValidator: (input) => {
        const length = (input || '').trim().length
        return (length >= 1 && length <= TITLE_MAX) || `标题为 1–${TITLE_MAX} 个字`
      }
    }))
  } catch {
    return
  }
  try {
    await store.rename(item.id, value)
    ElMessage.success('已重命名')
  } catch (e) {
    ElMessage.error(e.message || '重命名失败')
  }
}

async function remove(item) {
  try {
    await ElMessageBox.confirm(`删除「${item.title}」？删除后无法恢复。`, '删除对话', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger'
    })
  } catch {
    return
  }
  try {
    await store.remove(item.id)
    if (activeId.value === item.id) {
      router.replace({ name: 'chat' })
    }
    ElMessage.success('已删除')
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}
</script>

<style lang="scss" scoped>
.recent-title {
  padding: 0 10px 6px;
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.hint {
  padding: 4px 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.text-btn {
  border: 0;
  background: none;
  color: var(--el-color-primary);
  font: inherit;
  cursor: pointer;
}

.recent-list {
  list-style: none;
}

.recent-item {
  display: flex;
  align-items: center;
  border-radius: 8px;

  &:hover,
  &:focus-within,
  &.active {
    background: var(--el-fill-color);
  }

  &.active .recent-link {
    color: var(--el-text-color-primary);
    font-weight: 550;
  }

  &:hover .more-btn,
  &:focus-within .more-btn,
  &.active .more-btn {
    opacity: 1;
  }
}

.recent-link {
  flex: 1;
  min-width: 0;
  padding: 7px 10px;
  overflow: hidden;
  border-radius: 8px;
  color: var(--el-text-color-regular);
  font-size: 13.5px;
  text-overflow: ellipsis;
  white-space: nowrap;

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.more-btn {
  width: 28px;
  height: 28px;
  margin-right: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  opacity: 0;

  &:hover {
    background: var(--el-fill-color-dark);
    color: var(--el-text-color-primary);
  }

  &:focus-visible {
    opacity: 1;
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

// 触屏没有悬停，始终显示「更多」按钮
@media (hover: none) {
  .more-btn {
    opacity: 1;
  }
}

.load-more {
  width: 100%;
  margin-top: 6px;
  padding: 6px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-secondary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;

  &:hover:not(:disabled) {
    background: var(--el-fill-color);
  }
}
</style>
