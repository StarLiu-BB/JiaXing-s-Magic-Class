// 三端联合旅程：老师建课发布 -> 管理员建秒杀 -> 学生（微信沙箱登录）浏览/收藏/抢购/支付/学习/
// 发弹幕/问 AI -> 管理员审核弹幕、查订单、退款 -> 学生课时重新锁定。
// 全部经网关与真实服务（含本机 Ollama）。每次运行自建数据并在结束时清理，可反复执行。
//
// 用法：scripts/local/journeys.sh（由它加载 local.env 并提供 compose 路径）
import { execFileSync } from 'node:child_process'

const GW = `http://127.0.0.1:${process.env.ZHIXUE_GATEWAY_PORT || 19001}`
const WS = `ws://127.0.0.1:${process.env.ZHIXUE_INTERACTION_WS_PORT || 19999}/ws`
const TAG = `E2E-${Date.now().toString(36)}`
const compose = (...args) => execFileSync('docker', ['compose', '--env-file', process.env.ENV_FILE, '-f', process.env.COMPOSE_FILE, ...args]).toString().trim()

let failures = 0
const check = (label, actual, expected) => {
  const ok = JSON.stringify(actual) === JSON.stringify(expected)
  if (!ok) failures++
  console.log(`${ok ? '[ok]' : '[fail]'} ${label}${ok ? '' : `：期望 ${JSON.stringify(expected)}，实际 ${JSON.stringify(actual)}`}`)
  return ok
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
const waitFor = async (fn, timeout = 20000) => {
  const end = Date.now() + timeout
  for (;;) {
    const v = await fn()
    if (v || Date.now() > end) return v
    await sleep(500)
  }
}

async function api(method, path, token, body) {
  const res = await fetch(GW + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(120000)
  })
  return res.json()
}

async function passwordLogin(username, password) {
  const { data } = await api('GET', '/auth/captcha')
  const code = compose('exec', '-T', 'redis', 'redis-cli', '-a', process.env.ZHIXUE_REDIS_PASSWORD, '--no-auth-warning', 'GET', `zhixue:auth:captcha:${data.uuid}`)
  const res = await api('POST', '/auth/login', null, { username, password, code, uuid: data.uuid, loginType: 'password' })
  if (!res.data?.token) throw new Error(`${username} 登录失败：${res.msg}`)
  return res.data.token
}

function socket(roomId, token) {
  return new Promise((resolve) => {
    const ws = new WebSocket(`${WS}?roomId=${roomId}&token=${encodeURIComponent(token)}`)
    const received = []
    ws.onmessage = (e) => received.push(JSON.parse(e.data))
    ws.onopen = () => resolve({ ws, received })
    ws.onerror = () => resolve(null)
  })
}

const created = { courseId: null, seckillId: null }

async function main() {
  const admin = await passwordLogin('admin', '123456')
  const teacher = await passwordLogin('teacher', 'teacher123')
  const wx = await api('POST', '/auth/login', null, { loginType: 'wechat', wechatCode: `${TAG}-code` })
  if (!check('学生端微信登录（本地沙箱映射到 student）', wx.data?.user?.username, 'student')) return
  const student = wx.data.token
  const studentId = String(wx.data.user.userId)

  console.log('===== 老师：建课 -> 章节课时 -> 发布 =====')
  const course = await api('POST', '/course', teacher, { title: `${TAG} Redis 缓存实战`, subtitle: '穿透、击穿、雪崩', categoryId: 110, price: 59, originalPrice: 129, coverUrl: 'https://picsum.photos/750/422', description: '讲解 Redis 缓存三大问题', previewLessonCount: 1 })
  created.courseId = course.data?.id
  if (!check('新建课程返回 ID（草稿）', [course.code, !!created.courseId, course.data?.status], [200, true, 0])) return
  const cid = created.courseId
  const chapter = await api('POST', '/course/chapter', teacher, { courseId: cid, title: '第一章 缓存问题', orderNum: 1 })
  for (const [i, t] of ['1.1 缓存穿透', '1.2 缓存击穿'].entries()) {
    await api('POST', '/course/lesson', teacher, { courseId: cid, chapterId: chapter.data.id, title: t, videoUrl: `https://example.com/${i}.mp4`, duration: 600, orderNum: i + 1, status: 1 })
  }
  check('编辑接口不能改上下架状态', (await api('PUT', '/course', teacher, { id: cid, title: `${TAG} Redis 缓存实战`, status: 1, shelfStatus: 1 })).code === 200
    && (await api('GET', `/course/info/${cid}`, teacher)).data.status, 0)
  check('老师发布课程', (await api('POST', `/course/publish/${cid}`, teacher)).code, 200)

  console.log('===== 学生：浏览、试看、收藏 =====')
  const latest = await api('GET', '/course/latest/list', student)
  const latestList = Array.isArray(latest.data) ? latest.data : latest.data?.records || []
  check('最新课程里能看到老师刚发布的课', latestList.some((c) => String(c.id) === String(cid)), true)
  const lessons = async () => (await api('GET', `/course/${cid}/chapters`, student)).data.flatMap((c) => c.lessons || []).map((l) => [!!l.videoUrl, !!l.locked])
  check('未购买：第 1 课试看，第 2 课锁定', await lessons(), [[true, false], [false, true]])
  const fav = async () => (await api('GET', `/course/interaction/status/${cid}`, student)).data
  const f1 = await api('POST', `/course/interaction/favorite/${cid}`, student)
  const s1 = await fav()
  await api('POST', `/course/interaction/favorite/${cid}`, student)
  const s2 = await fav()
  check('收藏/取消：返回值、状态、计数一致', [f1.data, s1.favorited, s1.favoriteCount, s2.favorited, s2.favoriteCount], [true, true, 1, false, 0])
  check('收藏状态接口第二次查询不报错（计数缓存命中）', (await api('GET', `/course/interaction/status/${cid}`, student)).code, 200)

  console.log('===== 管理员建秒杀 -> 学生抢购并支付 =====')
  const fmt = (d) => new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 19)
  const sk = await api('POST', '/marketing/seckill', admin, { title: `${TAG} 9.9 元`, productId: cid, seckillPrice: 9.9, totalStock: 2, startTime: fmt(new Date(Date.now() - 60000)), endTime: fmt(new Date(Date.now() + 3600000)), status: 1 })
  created.seckillId = sk.data?.id
  check('管理员创建秒杀', sk.code, 200)
  const token = await api('POST', `/marketing/seckill/token?activityId=${created.seckillId}`, student)
  const order = await api('POST', '/marketing/seckill/do', student, { activityId: created.seckillId, token: token.data })
  check('抢到后生成按秒杀价的待支付订单', [order.code, Number(order.data?.amount)], [200, 9.9])
  check('令牌不能重放', (await api('POST', '/marketing/seckill/do', student, { activityId: created.seckillId, token: token.data })).code !== 200, true)
  await api('POST', '/pay', student, { orderNo: order.data.orderNo, payChannel: 'mock' })
  check('模拟收银台支付', (await api('POST', '/pay/mock/confirm', student, { orderNo: order.data.orderNo })).code, 200)
  check('支付后两个课时都解锁', await lessons(), [[true, false], [true, false]])

  console.log('===== 弹幕：鉴权、旁观接收、本地模型审核、管理员复核 =====')
  check('不带令牌握手被拒', await socket(cid, 'bad.token.x'), null)
  const viewer = await socket(cid, teacher)
  const sender = await socket(cid, student)
  check('学生与旁观的老师都连上弹幕', !!(viewer && sender), true)
  if (viewer && sender) {
    sender.ws.send(JSON.stringify({ userId: 1, roomId: 1, content: '缓存击穿讲得真清楚', timePoint: 10 }))
    sender.ws.send(JSON.stringify({ content: '加微信 vx123456 领取破解版全套课程', timePoint: 12 }))
    const got = await waitFor(() => viewer.received.filter((m) => m.content).length >= 2 && viewer.received, 5000)
    check('旁观者收到弹幕，发送者身份以登录态为准（伪造的 userId=1 无效）', (got || []).filter((m) => m.content).map((m) => String(m.userId)), [studentId, studentId])
    viewer.ws.close(); sender.ws.close()
  }
  const audited = await waitFor(async () => {
    const page = await api('GET', `/danmaku/page?pageNum=1&pageSize=10&roomId=${cid}`, admin)
    const recs = page.data?.records || []
    return recs.length >= 2 && recs.every((r) => r.auditStatus != null) && recs
  }, 30000)
  // 两条弹幕几乎同时发送，发送时间相同，不能按列表顺序断言，按内容对应
  const statusOf = (text) => (audited || []).find((r) => r.content.includes(text))?.auditStatus
  check('本地模型审核：正常通过、引流被拦', [statusOf('缓存击穿'), statusOf('领取破解版')], [1, 2])
  check('学生拉历史弹幕只含已通过的', (await api('GET', `/danmaku/history?roomId=${cid}`, student)).data.map((d) => d.content), ['缓存击穿讲得真清楚'])
  check('老师没有弹幕审核权限', (await api('GET', '/danmaku/page?pageNum=1&pageSize=1', teacher)).code, 403)

  console.log('===== 学生问 AI（本机 Ollama + 课程知识库）=====')
  const t0 = Date.now()
  const ai = await api('GET', `/ai/chat/course?question=${encodeURIComponent('网关在这门课里是怎么做鉴权的？')}&courseId=2001`, student)
  check('AI 回答引用课程资料', [ai.code, /JWT|令牌|请求头/.test(ai.data || '')], [200, true])
  console.log(`   用时 ${((Date.now() - t0) / 1000).toFixed(1)}s：${(ai.data || ai.msg || '').replace(/\s+/g, ' ').slice(0, 80)}…`)
  // 必须发真正的 multipart：格式错误的请求在参数解析阶段就返回 400，到不了权限校验
  const form = new FormData()
  form.append('file', new Blob(['学生试图写入的知识'], { type: 'text/plain' }), 'note.txt')
  form.append('title', `${TAG} 学生上传`)
  const upload = await fetch(`${GW}/ai/kb/upload`, { method: 'POST', headers: { Authorization: `Bearer ${student}` }, body: form }).then((r) => r.json())
  check('学生不能上传知识库', upload.code, 403)

  console.log('===== 管理员查订单、退款 -> 学生课时重新锁定 =====')
  const orders = await api('GET', `/order/list?pageNum=1&pageSize=10&userId=${studentId}`, admin)
  const mine = (orders.data?.records || []).find((o) => o.orderNo === order.data.orderNo)
  check('管理员看到这笔 9.9 元已支付订单', [!!mine, Number(mine?.status), Number(mine?.amount)], [true, 1, 9.9])
  check('学生不能退款', (await api('PUT', `/order/${mine.id}/refund`, student, { reason: 'x' })).code, 403)
  check('管理员退款', (await api('PUT', `/order/${mine.id}/refund`, admin, { reason: `${TAG} 联调退款` })).code, 200)
  check('退款后第 2 课重新锁定', await lessons(), [[true, false], [false, true]])
}

async function cleanup() {
  const admin = await passwordLogin('admin', '123456').catch(() => null)
  if (!admin) return
  if (created.seckillId) await api('DELETE', `/marketing/seckill/${created.seckillId}`, admin).catch(() => {})
  if (created.courseId) {
    await api('DELETE', `/course/${created.courseId}`, admin).catch(() => {})
    // 订单与弹幕没有删除接口（订单按业务规则不可删）：本地联调数据直接清理
    const pw = process.env.ZHIXUE_DB_PASSWORD
    const sql = (db, q) => compose('exec', '-T', 'mysql', 'mysql', '--default-character-set=utf8mb4', '-uroot', `-p${pw}`, db, '-e', q)
    try {
      sql('zhixue_order', `DELETE FROM \`order\` WHERE product_id=${created.courseId};`)
      sql('zhixue_interaction', `DELETE FROM danmaku WHERE room_id=${created.courseId}; DELETE FROM interaction_course_favorite WHERE course_id=${created.courseId}; DELETE FROM interaction_course_stats WHERE course_id=${created.courseId};`)
      sql('zhixue_marketing', `DELETE FROM seckill_order WHERE activity_id=${created.seckillId || 0};`)
    } catch (e) {
      console.log(`[warn] 清理联调数据失败：${e.message.split('\n')[0]}`)
    }
  }
  console.log(`[info] 已清理本次联调数据（${TAG}）`)
}

try {
  await main()
} catch (e) {
  failures++
  console.log(`[fail] 旅程中断：${e.stack || e.message}`)
} finally {
  await cleanup()
}
console.log(failures ? `[fail] 三端联合旅程：${failures} 项失败` : '[ok] 三端联合旅程全部通过')
process.exit(failures ? 1 : 0)
