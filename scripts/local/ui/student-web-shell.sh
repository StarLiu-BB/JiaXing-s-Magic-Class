# 外壳分段（设计文档 3.2、3.3 第 6、7 项、7.3 第 9 项）：侧边栏「最近」能加载；窄屏（< 960px）侧边栏收起为抽屉
# shellcheck shell=bash

section_shell() {
  login_as_student

  check "1440 宽：固定侧边栏，「最近」加载成功" "$(pwf <<'JS'
async page => {
  await page.goto('http://localhost:3001/chat')
  await page.getByRole('heading', { name: '有什么想学的？' }).waitFor({ timeout: 10000 })
  await page.waitForFunction(() => !document.querySelector('.recent')?.innerText.includes('加载中'), null, { timeout: 10000 })
  const recent = await page.locator('.recent').innerText()
  return `${await page.locator('.sidebar').count()} ${recent.includes('加载失败') ? '加载失败' : '加载成功'}`
}
JS
)" "1 加载成功"
  playwright-cli screenshot --filename="$OUT/shell-1440.png" >/dev/null 2>&1

  playwright-cli resize 390 844 >/dev/null 2>&1
  check "390 宽：不显示固定侧边栏，左上角按钮唤出抽屉" "$(pwf <<'JS'
async page => {
  await page.goto('http://localhost:3001/chat')
  await page.getByRole('heading', { name: '有什么想学的？' }).waitFor({ timeout: 10000 })
  const fixed = await page.locator('.sidebar').count()
  await page.getByRole('button', { name: '打开导航' }).click()
  await page.locator('.el-drawer .new-chat').waitFor({ timeout: 5000 })
  return `${fixed} 抽屉已打开`
}
JS
)" "0 抽屉已打开"
  playwright-cli screenshot --filename="$OUT/shell-390-drawer.png" >/dev/null 2>&1

  check "抽屉里点「新对话」后抽屉关闭" "$(pwf <<'JS'
async page => {
  await page.locator('.el-drawer .new-chat').click()
  await page.locator('.el-drawer').waitFor({ state: 'hidden', timeout: 5000 })
  return 'closed'
}
JS
)" "closed"
  playwright-cli screenshot --filename="$OUT/shell-390.png" >/dev/null 2>&1
  playwright-cli resize 1440 900 >/dev/null 2>&1
}
