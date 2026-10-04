<template>
  <div class="dashboard">
    <header class="page-head">
      <div>
        <h1>{{ greeting }}，{{ displayName }}</h1>
        <p>{{ today }} · 以下数据均实时取自各业务服务</p>
      </div>
      <div class="head-actions">
        <el-button v-if="can('order:list')" @click="router.push('/order/list')">查看订单</el-button>
        <el-button v-if="can('course:publish')" type="primary" :icon="Plus" @click="router.push('/course/publish')">
          发布课程
        </el-button>
      </div>
    </header>

    <!-- 指标：每张卡片只在拥有对应权限时出现，避免无权限请求刷出 403 提示 -->
    <section v-if="metrics.length" class="metrics" aria-label="关键指标">
      <article v-for="m in metrics" :key="m.key" class="metric">
        <div class="metric-top">
          <span class="metric-label">{{ m.label }}</span>
          <span class="metric-icon" :class="`tone-${m.tone}`"><el-icon :size="16"><component :is="m.icon" /></el-icon></span>
        </div>
        <el-skeleton v-if="m.loading" :rows="0" animated class="metric-skeleton">
          <template #template><el-skeleton-item variant="h1" style="width: 60%; height: 30px" /></template>
        </el-skeleton>
        <template v-else>
          <div class="metric-value tabular-nums">
            <small v-if="m.prefix">{{ m.prefix }}</small>{{ m.value }}
          </div>
          <div class="metric-foot">{{ m.foot }}</div>
        </template>
      </article>
    </section>

    <section class="grid">
      <el-card v-if="can('order:list')" shadow="never" class="recent">
        <template #header>
          <div class="card-header">
            <span>最近订单</span>
            <el-button link type="primary" @click="router.push('/order/list')">全部订单</el-button>
          </div>
        </template>
        <el-table v-loading="ordersLoading" :data="orders" size="default">
          <el-table-column label="订单号" prop="orderNo" min-width="170" show-overflow-tooltip />
          <el-table-column label="用户" prop="userId" width="90" />
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }"><span class="tabular-nums">¥{{ formatMoney(row.amount) }}</span></template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="orderStatus(row).tag" size="small">{{ orderStatus(row).text }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="下单时间" width="160">
            <template #default="{ row }"><span class="tabular-nums muted">{{ formatTime(row.createTime) }}</span></template>
          </el-table-column>
          <template #empty>
            <div class="empty">还没有订单</div>
          </template>
        </el-table>
      </el-card>

      <div class="side">
        <el-card v-if="can('order:list')" shadow="never">
          <template #header><div class="card-header"><span>订单状态</span></div></template>
          <template v-if="stats && stats.totalCount > 0">
            <div class="status-bar" role="img" :aria-label="statusAria">
              <span
                v-for="s in statusSegments"
                :key="s.key"
                :style="{ width: s.percent + '%', background: s.color }"
                :title="`${s.label} ${s.count}`"
              />
            </div>
            <ul class="status-legend">
              <li v-for="s in statusSegments" :key="s.key">
                <span class="dot" :style="{ background: s.color }" />
                <span class="legend-label">{{ s.label }}</span>
                <span class="legend-value tabular-nums">{{ s.count }}</span>
                <span class="legend-percent tabular-nums">{{ s.percent }}%</span>
              </li>
            </ul>
          </template>
          <div v-else class="empty">{{ statsLoading ? '加载中…' : '暂无订单数据' }}</div>
        </el-card>

        <el-card shadow="never">
          <template #header><div class="card-header"><span>常用功能</span></div></template>
          <nav v-if="shortcuts.length" class="shortcuts" aria-label="常用功能">
            <router-link v-for="s in shortcuts" :key="s.path" :to="s.path" class="shortcut">
              <span class="shortcut-icon"><el-icon :size="17"><component :is="s.icon" /></el-icon></span>
              <span class="shortcut-text">{{ s.title }}</span>
            </router-link>
          </nav>
          <div v-else class="empty">当前账号还没有分配任何功能权限，请联系管理员。</div>
        </el-card>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Money, Clock, Reading, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/modules/user'
import { getOrderStatistics, listOrder } from '@/api/order/order'
import { listCourse } from '@/api/course/course'
import { listUser } from '@/api/system/user'

defineOptions({ name: 'Dashboard' })

const router = useRouter()
const userStore = useUserStore()

const can = (perm) => (userStore.permissions || []).includes(perm)
const displayName = computed(() => userStore.userInfo?.nickname || userStore.name || '你好')

const greeting = (() => {
  const h = new Date().getHours()
  if (h < 5) return '夜深了'
  if (h < 11) return '早上好'
  if (h < 13) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})()
const today = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'long' }).format(new Date())

// 与订单管理页保持一致
const ORDER_STATUS = {
  0: { text: '待支付', tag: 'warning', color: 'var(--el-color-warning)', key: 'pendingCount' },
  1: { text: '已支付', tag: 'success', color: 'var(--el-color-success)', key: 'paidCount' },
  2: { text: '已取消', tag: 'info', color: 'var(--el-border-color-darker)', key: 'canceledCount' },
  3: { text: '已退款', tag: 'danger', color: 'var(--el-color-danger)', key: 'refundedCount' }
}
const orderStatus = (row) => ORDER_STATUS[Number(row.status)] || { text: '未知', tag: 'info' }

const formatMoney = (v) => Number(v || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const formatTime = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')
const pageTotal = (res) => Number(res?.data?.total ?? res?.total ?? 0)

// ---- 指标 ----
const stats = ref(null)
const statsLoading = ref(false)
const courseCount = reactive({ loading: false, onSale: 0, total: 0 })
const userCount = reactive({ loading: false, total: 0 })

const metrics = computed(() => {
  const list = []
  if (can('order:list')) {
    const s = stats.value || {}
    list.push({
      key: 'paid', label: '成交金额', icon: Money, tone: 'primary', loading: statsLoading.value,
      prefix: '¥', value: formatMoney(s.paidAmount), foot: `已支付 ${s.paidCount ?? 0} 单`
    })
    list.push({
      key: 'pending', label: '待支付订单', icon: Clock, tone: 'warning', loading: statsLoading.value,
      value: s.pendingCount ?? 0, foot: `订单总数 ${s.totalCount ?? 0}`
    })
  }
  if (can('course:list')) {
    list.push({
      key: 'course', label: '在售课程', icon: Reading, tone: 'success', loading: courseCount.loading,
      value: courseCount.onSale, foot: `课程总数 ${courseCount.total}`
    })
  }
  if (can('system:user:list')) {
    list.push({
      key: 'user', label: '账号', icon: User, tone: 'info', loading: userCount.loading,
      value: userCount.total, foot: '含管理员、教师与学生'
    })
  }
  return list
})

const statusSegments = computed(() => {
  const s = stats.value
  if (!s || !s.totalCount) return []
  return Object.values(ORDER_STATUS).map((st) => {
    const count = Number(s[st.key] || 0)
    return { key: st.key, label: st.text, color: st.color, count, percent: Math.round((count / s.totalCount) * 1000) / 10 }
  })
})
const statusAria = computed(() => statusSegments.value.map((s) => `${s.label} ${s.count} 单`).join('，'))

// ---- 最近订单 ----
const orders = ref([])
const ordersLoading = ref(false)

// ---- 常用功能：取当前账号有权限的二级菜单 ----
const shortcuts = computed(() => {
  const modules = Object.values(import.meta.glob('/src/router/modules/*.js', { eager: true }))
    .map((m) => m.default)
    .filter(Boolean)
  const items = []
  for (const mod of modules) {
    for (const child of mod.children || []) {
      if (child.meta?.hidden) continue
      const perms = [].concat(child.meta?.permission || [])
      if (perms.length && !perms.some(can)) continue
      items.push({
        path: `${mod.path}/${child.path}`.replace(/\/+/g, '/'),
        title: child.meta?.title,
        icon: child.meta?.icon || mod.meta?.icon || 'Menu'
      })
    }
  }
  return items.slice(0, 8)
})

async function load() {
  const tasks = []
  if (can('order:list')) {
    statsLoading.value = true
    ordersLoading.value = true
    tasks.push(
      getOrderStatistics()
        .then((res) => { stats.value = res?.data || {} })
        .finally(() => { statsLoading.value = false }),
      listOrder({ pageNum: 1, pageSize: 6 })
        .then((res) => { orders.value = res?.data?.records || [] })
        .finally(() => { ordersLoading.value = false })
    )
  }
  if (can('course:list')) {
    courseCount.loading = true
    tasks.push(
      Promise.all([
        listCourse({ pageNum: 1, pageSize: 1, status: 1, shelfStatus: 1 }),
        listCourse({ pageNum: 1, pageSize: 1 })
      ])
        .then(([onSale, all]) => {
          courseCount.onSale = pageTotal(onSale)
          courseCount.total = pageTotal(all)
        })
        .finally(() => { courseCount.loading = false })
    )
  }
  if (can('system:user:list')) {
    userCount.loading = true
    tasks.push(
      listUser({ pageNum: 1, pageSize: 1 })
        .then((res) => { userCount.total = pageTotal(res) })
        .finally(() => { userCount.loading = false })
    )
  }
  // 单个接口失败只影响自己的卡片（请求层已经弹出错误提示）
  await Promise.allSettled(tasks)
}

onMounted(load)
</script>

<style lang="scss" scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 1440px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;

  h1 {
    font-size: 22px;
    line-height: 1.3;
  }

  p {
    margin-top: 4px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.head-actions {
  display: flex;
  gap: 8px;
}

.metrics {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
}

.metric {
  padding: 18px 20px 16px;
  background: var(--zx-surface);
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--zx-radius-card);
}

.metric-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.metric-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
}

.metric-icon {
  width: 30px;
  height: 30px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;

  &.tone-primary { color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
  &.tone-warning { color: var(--el-color-warning); background: var(--el-color-warning-light-9); }
  &.tone-success { color: var(--el-color-success); background: var(--el-color-success-light-9); }
  &.tone-info { color: var(--el-color-info); background: var(--el-fill-color); }
}

.metric-value {
  margin-top: 10px;
  font-size: 28px;
  line-height: 1.15;
  font-weight: 650;
  letter-spacing: -0.02em;
  color: var(--el-text-color-primary);

  small {
    margin-right: 2px;
    font-size: 16px;
    font-weight: 600;
    color: var(--el-text-color-secondary);
  }
}

.metric-skeleton {
  margin-top: 10px;
}

.metric-foot {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: start;
}

.side {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.recent :deep(.el-card__body) {
  padding: 4px 8px 8px;
}

.muted {
  color: var(--el-text-color-secondary);
}

.status-bar {
  display: flex;
  height: 10px;
  gap: 2px;
  overflow: hidden;
  border-radius: 5px;
  background: var(--el-fill-color);

  span {
    min-width: 0;
    transition: width 0.4s var(--zx-ease);

    &:first-child { border-radius: 5px 0 0 5px; }
    &:last-child { border-radius: 0 5px 5px 0; }
  }
}

.status-legend {
  list-style: none;
  margin-top: 16px;
  display: grid;
  gap: 10px;

  li {
    display: grid;
    grid-template-columns: 10px 1fr auto 52px;
    align-items: center;
    gap: 10px;
    font-size: 13px;
  }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 2px;
  }

  .legend-label { color: var(--el-text-color-regular); }
  .legend-value { color: var(--el-text-color-primary); font-weight: 600; }
  .legend-percent { color: var(--el-text-color-secondary); text-align: right; }
}

.shortcuts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.shortcut {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-regular);
  font-size: 13px;
  transition: border-color 0.16s var(--zx-ease), background-color 0.16s var(--zx-ease), color 0.16s var(--zx-ease);

  &:hover {
    border-color: var(--el-color-primary-light-7);
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);

    .shortcut-icon {
      color: var(--el-color-primary);
      background: var(--zx-surface);
    }
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.shortcut-icon {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 7px;
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  transition: inherit;
}

.shortcut-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty {
  padding: 28px 0;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
