# 对话分段（设计文档 7.3 第 2–8、10 项）：流式出字 → 推荐卡片 → 从卡片进课时 → 追问 → 停止 →
# 打开历史（卡片按最新「已购」状态）→ 重命名、删除 → 停掉 Ollama 看失败气泡并重试 → 只用键盘发送与停止。
# 依赖后端计划任务 9 的流式接口。创建的会话、为验收而买的课在结束时清理。
# shellcheck shell=bash

# 等最后一条回答结束（不再是 streaming），返回它的状态
WAIT_LAST_ANSWER='await page.waitForFunction(() => {
    const items = document.querySelectorAll(".answer")
    return items.length > 0 && items[items.length - 1].getAttribute("data-status") !== "streaming"
  }, null, { timeout: 150000 })'

remember_conversation() {
  [[ "$1" =~ ^[0-9]+$ ]] && CLEANUP_CONVERSATIONS+=("$1")
}

section_chat() {
  login_as_student
  require_not_purchased || return
  local r first

  # ---------- 限定课程提问（设计文档 3.3 第 5 项） ----------
  check "从课程详情「问这门课」进入：显示可清除的课程范围" "$(pwf <<'JS'
async page => {
  await page.goto('http://localhost:3001/course/2007')
  await page.getByRole('button', { name: '问这门课' }).click()
  await page.locator('.scope-tag').waitFor({ timeout: 5000 })
  const tag = await page.locator('.scope-tag').innerText()
  await page.getByRole('button', { name: '改为问全部课程' }).click()
  await page.locator('.scope-tag').waitFor({ state: 'detached', timeout: 5000 })
  return tag
}
JS
)" "正在问：Java 并发编程实战"

  # ---------- 第 2 项：逐段出字、推荐卡片、「最近」 ----------
  r=$(pwf <<'JS'
async page => {
  await page.goto('http://localhost:3001/chat')
  await page.getByRole('button', { name: 'HashMap 为什么线程不安全' }).click()
  const answer = page.locator('.answer').last()
  await answer.waitFor({ timeout: 10000 })
  const lengths = new Set()
  const deadline = Date.now() + 150000
  while (Date.now() < deadline) {
    const status = await answer.getAttribute('data-status')
    const text = await answer.locator('.answer-md').innerText().catch(() => '')
    if (text) lengths.add(text.length)
    if (status !== 'streaming') break
    await page.waitForTimeout(150)
  }
  await page.locator('.reco').first().waitFor({ timeout: 5000 }).catch(() => {})
  return JSON.stringify({
    id: page.url().split('/chat/')[1] || '',
    status: await answer.getAttribute('data-status'),
    growth: lengths.size,
    courses: await page.locator('.reco .course-title').allInnerTexts(),
    sources: await answer.locator('.sources').count(),
    recent: await page.locator('.recent-item.active .recent-link').innerText()
  })
}
JS
)
  echo "   $r"
  first=$(json_get "$r" id)
  remember_conversation "$first"
  check "回答正常完成" "$(json_get "$r" status)" "done"
  check "文字分多次出现（观察到 ≥ 3 种长度）" \
    "$(python3 -c 'import json,sys;print(json.loads(sys.argv[1])["growth"] >= 3)' "$r" 2>/dev/null)" "True"
  check "推荐了《Java 并发编程实战》" \
    "$(python3 -c 'import json,sys;print("Java 并发编程实战" in json.loads(sys.argv[1])["courses"])' "$r" 2>/dev/null)" "True"
  check "显示参考资料" "$(json_get "$r" sources)" "1"
  check "「最近」出现并选中该会话，标题取问题" "$(json_get "$r" recent)" "HashMap 为什么线程不安全"
  playwright-cli screenshot --filename="$OUT/chat-answer-1440.png" >/dev/null 2>&1

  # ---------- 第 3 项：从卡片进课时 ----------
  check "点卡片上锁定的课时 → 课程详情弹出购买提示" "$(pwf <<JS
async page => {
  await page.goto('http://localhost:3001/chat/$first')
  const locked = page.locator('.reco .lesson.locked').first()
  await locked.waitFor({ timeout: 10000 })
  await locked.click()
  const box = page.locator('.el-message-box')
  await box.waitFor({ timeout: 8000 })
  const text = await box.locator('.el-message-box__message').innerText()
  await box.getByRole('button', { name: '再看看' }).click()
  return String(text.includes('需要购买课程后观看'))
}
JS
)" "true"

  # ---------- 第 4 项：追问 ----------
  r=$(pwf <<JS
async page => {
  await page.goto('http://localhost:3001/chat/$first')
  await page.locator('.answer').first().waitFor({ timeout: 10000 })
  await page.getByLabel('输入问题').fill('那 ConcurrentHashMap 是怎么解决的？')
  await page.keyboard.press('Enter')
  await page.locator('.answer').nth(1).waitFor({ timeout: 10000 })
  $WAIT_LAST_ANSWER
  const answer = page.locator('.answer').nth(1)
  return JSON.stringify({ status: await answer.getAttribute('data-status'), text: await answer.locator('.answer-md').innerText() })
}
JS
)
  check "追问在同一会话中完成" "$(json_get "$r" status)" "done"
  local followed
  followed=$(python3 -c 'import json,sys;print("ConcurrentHashMap" in json.loads(sys.argv[1])["text"])' "$r" 2>/dev/null)
  check "回答承接上文（围绕 ConcurrentHashMap）" "$followed" "True"
  # 断言依赖模型原文：失败时留下回答开头，区分「模型没提到」与「取文本出错」
  if [[ "$followed" != "True" ]]; then
    echo "   回答开头：$(python3 -c 'import json,sys;print(json.loads(sys.argv[1])["text"][:300].replace("\n"," "))' "$r" 2>/dev/null || echo "$r" | head -c 300)"
  fi

  # ---------- 第 5 项：停止 ----------
  r=$(pwf <<'JS'
async page => {
  await page.getByLabel('输入问题').fill('详细讲讲 HashMap 的扩容过程，分步骤说明，并给出示例代码')
  await page.keyboard.press('Enter')
  const answer = page.locator('.answer').nth(2)
  await answer.locator('.answer-md').waitFor({ timeout: 60000 })
  await page.getByRole('button', { name: '停止生成' }).click()
  await answer.locator('.stopped').waitFor({ timeout: 10000 })
  return JSON.stringify({
    status: await answer.getAttribute('data-status'),
    kept: (await answer.locator('.answer-md').innerText()).length > 0,
    cards: await answer.locator('.reco').count()
  })
}
JS
)
  check "点停止：标记已停止" "$(json_get "$r" status)" "stopped"
  check "保留已生成的文字、不出推荐" "$(json_get "$r" kept) $(json_get "$r" cards)" "true 0"

  # ---------- 第 6 项：历史卡片按最新状态 ----------
  CLEANUP_REFUND_COURSES+=(2007)
  check "（准备）学生通过接口买下课程 2007" "$(purchase_course 2007)" "200"
  r=$(pwf <<'JS'
async page => {
  await page.goto('http://localhost:3001/chat')
  await page.locator('.recent-link', { hasText: 'HashMap 为什么线程不安全' }).first().click()
  await page.locator('.reco').first().waitFor({ timeout: 10000 })
  // 卡片按相关度排序：「HashMap 为什么线程不安全」时 2006 排在 2007 前面，
  // 验收只买了 2007，所以按课程标题定位，不能取第一张
  const card = page.locator('.reco .card', { hasText: 'Java 并发编程实战' }).first()
  return JSON.stringify({
    badge: await card.locator('.badge').first().innerText(),
    locked: await card.locator('.lesson.locked').count(),
    statuses: await page.locator('.answer').evaluateAll((list) => list.map((a) => a.getAttribute('data-status')))
  })
}
JS
)
  echo "   $r"
  check "刷新后打开历史：卡片显示已购、课时全部可播放" "$(json_get "$r" badge) $(json_get "$r" locked)" "已购 0"
  check "历史按服务端状态还原" "$(json_get "$r" statuses)" '["done", "done", "stopped"]'

  # ---------- 第 7 项：重命名、删除 ----------
  check "重命名" "$(pwf <<'JS'
async page => {
  const item = page.locator('.recent-item.active')
  await item.hover()
  await item.getByRole('button', { name: /更多操作/ }).click()
  await page.locator('.el-dropdown-menu__item:visible', { hasText: '重命名' }).click()
  const box = page.locator('.el-message-box')
  await box.locator('input').fill('并发笔记')
  await box.getByRole('button', { name: '保存' }).click()
  await page.locator('.recent-item.active .recent-link', { hasText: '并发笔记' }).waitFor({ timeout: 5000 })
  await page.reload()
  await page.locator('.recent-item.active .recent-link', { hasText: '并发笔记' }).waitFor({ timeout: 10000 })
  return 'renamed'
}
JS
)" "renamed"
  check "删除：回到新对话，列表中不再出现" "$(pwf <<'JS'
async page => {
  const item = page.locator('.recent-item.active')
  await item.hover()
  await item.getByRole('button', { name: /更多操作/ }).click()
  await page.locator('.el-dropdown-menu__item:visible', { hasText: '删除' }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '删除' }).click()
  await page.waitForURL(/\/chat$/, { timeout: 5000 })
  await page.reload()
  await page.getByRole('heading', { name: '有什么想学的？' }).waitFor({ timeout: 10000 })
  return String(await page.locator('.recent-link', { hasText: '并发笔记' }).count())
}
JS
)" "0"

  # ---------- 第 8 项：模型不可用 ----------
  brew services stop ollama >/dev/null 2>&1
  OLLAMA_STOPPED=1
  sleep 2
  r=$(pwf <<'JS'
async page => {
  await page.goto('http://localhost:3001/chat')
  await page.getByLabel('输入问题').fill('String 为什么是不可变的')
  await page.keyboard.press('Enter')
  await page.locator('.failure').waitFor({ timeout: 60000 })
  return JSON.stringify({ id: page.url().split('/chat/')[1] || '', text: await page.locator('.failure').innerText() })
}
JS
)
  remember_conversation "$(json_get "$r" id)"
  check "停掉 Ollama：红色失败气泡如实说明原因" \
    "$(python3 -c 'import json,sys;print("AI 服务暂不可用" in json.loads(sys.argv[1])["text"])' "$r" 2>/dev/null)" "True"
  playwright-cli screenshot --filename="$OUT/chat-failure-1440.png" >/dev/null 2>&1
  brew services start ollama >/dev/null 2>&1
  OLLAMA_STOPPED=0
  for _ in $(seq 1 30); do curl -sf -m 2 http://127.0.0.1:11434/api/version >/dev/null && break; sleep 1; done
  check "恢复 Ollama 后点重试成功" "$(pwf <<JS
async page => {
  await page.locator('.failure').getByRole('button', { name: '重试' }).click()
  await page.waitForFunction(() => document.querySelectorAll('.answer').length >= 2, null, { timeout: 10000 })
  $WAIT_LAST_ANSWER
  return await page.locator('.answer').last().getAttribute('data-status')
}
JS
)" "done"

  # ---------- 第 10 项：只用键盘 ----------
  r=$(pwf <<'JS'
async page => {
  await page.goto('http://localhost:3001/chat')
  await page.getByRole('heading', { name: '有什么想学的？' }).waitFor({ timeout: 10000 })
  await page.waitForTimeout(200)
  const focused = await page.evaluate(() => document.activeElement?.getAttribute('aria-label'))
  await page.keyboard.type('线程池参数怎么设，请分点详细说明')
  await page.keyboard.press('Enter')
  const answer = page.locator('.answer').last()
  await answer.locator('.answer-md').waitFor({ timeout: 60000 })
  await page.keyboard.press('Escape')
  await answer.locator('.stopped').waitFor({ timeout: 10000 })
  return JSON.stringify({ focused, id: page.url().split('/chat/')[1] || '', status: await answer.getAttribute('data-status') })
}
JS
)
  remember_conversation "$(json_get "$r" id)"
  check "进入新对话时输入框已获得焦点" "$(json_get "$r" focused)" "输入问题"
  check "只用键盘：Enter 发送、Esc 停止" "$(json_get "$r" status)" "stopped"
}
