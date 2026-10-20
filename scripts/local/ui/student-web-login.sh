# 登录分段（设计文档 7.3 第 1 项）：未登录跳登录页 → 账号密码 + 验证码登录 → 回到原地址；退出登录
# shellcheck shell=bash

section_login() {
  playwright-cli goto "$WEB/login" >/dev/null 2>&1
  playwright-cli localstorage-clear >/dev/null 2>&1

  check "未登录访问被带到登录页并记住地址" "$(pwf <<'JS'
async page => {
  await page.goto('http://localhost:3001/chat')
  await page.waitForURL(/\/login/)
  return page.url().replace('http://localhost:3001', '')
}
JS
)" "/login?redirect=/chat"

  local uuid code
  uuid=$(pwf <<'JS'
async page => {
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/auth/captcha')),
    page.getByRole('button', { name: '刷新验证码' }).click()
  ])
  return (await response.json()).data.uuid
}
JS
)
  code=$(redis_get "zhixue:auth:captcha:$uuid")

  check "验证码错误时提示原因并换一张" "$(pwf <<'JS'
async page => {
  await page.getByPlaceholder('请输入账号').fill('student')
  await page.getByPlaceholder('请输入密码').fill('student123')
  await page.getByPlaceholder('右侧字符').fill('XXXX')
  await page.getByRole('button', { name: '登录' }).click()
  await page.locator('.form-error').waitFor({ timeout: 8000 })
  return String((await page.locator('.form-error').innerText()).length > 0)
}
JS
)" "true"

  uuid=$(pwf <<'JS'
async page => {
  const [response] = await Promise.all([
    page.waitForResponse((r) => r.url().includes('/auth/captcha')),
    page.getByRole('button', { name: '刷新验证码' }).click()
  ])
  return (await response.json()).data.uuid
}
JS
)
  code=$(redis_get "zhixue:auth:captcha:$uuid")
  check "登录后回到原地址，显示空状态" "$(pwf <<JS
async page => {
  await page.getByPlaceholder('右侧字符').fill('$code')
  await page.getByRole('button', { name: '登录' }).click()
  await page.getByRole('heading', { name: '有什么想学的？' }).waitFor({ timeout: 10000 })
  return page.url().replace('http://localhost:3001', '')
}
JS
)" "/chat"
  check "侧边栏显示学生昵称" "$(pwf <<'JS'
async page => await page.locator('.user-name').innerText()
JS
)" "示例学生"
  playwright-cli screenshot --filename="$OUT/login-landing-1440.png" >/dev/null 2>&1

  check "退出登录：清掉令牌，回到登录页" "$(pwf <<'JS'
async page => {
  await page.getByRole('button', { name: '账号菜单' }).click()
  await page.getByText('退出登录').click()
  await page.waitForURL(/\/login/)
  return String(await page.evaluate(() => localStorage.getItem('zx_student_token')))
}
JS
)" "null"

  # 这里只验证「带着失效令牌访问受保护页面 → 记住地址去登录页」；带参数与课时的地址
  # 由任务 2 的课程分段与守卫单测覆盖（本任务还没有课程路由）
  check "令牌失效：带上原地址去登录页" "$(pwf <<'JS'
async page => {
  await page.evaluate(() => localStorage.setItem('zx_student_token', 'expired-token'))
  await page.goto('http://localhost:3001/chat')
  await page.waitForURL(/\/login/)
  return page.url().replace('http://localhost:3001', '')
}
JS
)" "/login?redirect=/chat"
}
