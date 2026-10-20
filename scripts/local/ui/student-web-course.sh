# 课程详情分段（设计文档 3.3 第 9 项、7.3 第 3 项）：目录的锁定/试看、试看播放、锁定提示、
# 模拟收银台购买后全部解锁并直接播放、「问这门课」。购买的订单在分段结束时退款。
# shellcheck shell=bash

section_course() {
  login_as_student
  require_not_purchased || return

  check "未购课程：试看前 2 节可播放，其余锁定" "$(pwf <<'JS'
async page => {
  await page.goto('http://localhost:3001/course/2007')
  await page.getByRole('heading', { name: 'Java 并发编程实战' }).waitFor({ timeout: 10000 })
  const rows = page.locator('.lesson')
  const marks = []
  for (let i = 0; i < await rows.count(); i++) {
    const locked = (await rows.nth(i).getAttribute('class')).includes('locked')
    marks.push(`${await rows.nth(i).locator('.lesson-no').innerText()}${locked ? '锁' : '可'}`)
  }
  return `${await page.locator('.price').innerText()} ${marks.join(' ')}`
}
JS
)" "¥99 1.1可 1.2可 2.1锁 2.2锁 3.1锁"
  playwright-cli screenshot --filename="$OUT/course-1440.png" >/dev/null 2>&1

  check "点试看课时：在详情页内播放" "$(pwf <<'JS'
async page => {
  await page.locator('.lesson').first().click()
  await page.locator('.el-dialog video').waitFor({ timeout: 8000 })
  const title = await page.locator('.el-dialog__title').innerText()
  await page.keyboard.press('Escape')
  return title
}
JS
)" "1.1 线程的创建与生命周期"

  check "点锁定课时：提示购买，可以取消" "$(pwf <<'JS'
async page => {
  await page.locator('.lesson.locked').first().click()
  const box = page.locator('.el-message-box')
  await box.waitFor({ timeout: 5000 })
  const text = await box.locator('.el-message-box__message').innerText()
  await box.getByRole('button', { name: '再看看' }).click()
  return text
}
JS
)" "「2.1 synchronized 与 ReentrantLock」需要购买课程后观看。"

  CLEANUP_REFUND_COURSES+=(2007)
  check "从推荐卡片进入锁定课时 → 购买 → 模拟支付 → 全部解锁并直接播放该课时" "$(pwf <<'JS'
async page => {
  await page.goto('http://localhost:3001/course/2007?lesson=4024')
  const ask = page.locator('.el-message-box')
  await ask.waitFor({ timeout: 8000 })
  await ask.getByRole('button', { name: /^购买/ }).click()
  const cashier = page.locator('.el-message-box', { hasText: '模拟收银台' })
  await cashier.waitFor({ timeout: 8000 })
  await cashier.getByRole('button', { name: '确认支付' }).click()
  await page.locator('.el-dialog video').waitFor({ timeout: 10000 })
  const playing = await page.locator('.el-dialog__title').innerText()
  await page.keyboard.press('Escape')
  const locked = await page.locator('.lesson.locked').count()
  const owned = await page.locator('.price-row .el-tag').innerText()
  return `${playing} 锁定${locked} ${owned}`
}
JS
)" "2.2 ConcurrentHashMap 原理 锁定0 已购买"

  check "问这门课：新对话限定在该课程" "$(pwf <<'JS'
async page => {
  await page.getByRole('button', { name: '问这门课' }).click()
  await page.waitForURL(/\/chat\?/)
  return await page.evaluate(() => {
    const query = new URLSearchParams(location.search)
    return `${query.get('courseId')} ${query.get('courseTitle')}`
  })
}
JS
)" "2007 Java 并发编程实战"

  refund_course 2007
  CLEANUP_REFUND_COURSES=()
  check "退款后课程恢复为未购（3 节锁定）" "$(pwf <<'JS'
async page => {
  await page.goto('http://localhost:3001/course/2007')
  await page.locator('.lesson').first().waitFor({ timeout: 10000 })
  return String(await page.locator('.lesson.locked').count())
}
JS
)" "3"

  # 课程路由在任务 2 才加入：带参数的课程地址也要能把原地址带到登录页
  check "令牌失效：课程地址被带到登录页" "$(pwf <<'JS'
async page => {
  await page.evaluate(() => localStorage.setItem('zx_student_token', 'expired-token'))
  await page.goto('http://localhost:3001/course/2007')
  await page.waitForURL(/\/login/)
  return page.url().replace('http://localhost:3001', '')
}
JS
)" "/login?redirect=/course/2007"
  login_as_student
}
