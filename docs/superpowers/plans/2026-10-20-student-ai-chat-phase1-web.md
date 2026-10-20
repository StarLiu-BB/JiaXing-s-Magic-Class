# 学生端网页版一期（AI 对话 + 课程推荐）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use incremental-implementation（模式 B 子代理控制器优先，否则模式 A 本会话执行）to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `zhixue-student-web`：学生登录后向 AI 课程助教提问，回答逐字流式显示，回答下方推荐相关视频课；可从推荐卡片进入课程详情试看、购买与播放；侧边栏「最近」管理历史对话。

**Architecture:** Vue 3 + Vite 单页应用，与管理端同栈、同设计令牌（直接复制，不抽公共包）。`/api` 经 Vite 代理同源转给网关。流式回答用 `fetch` + `ReadableStream` 自行解析 SSE（`EventSource` 发不了 POST、带不了令牌）。业务逻辑（SSE 解析、流式客户端、会话状态机、请求封装、路由守卫、Markdown 安全渲染、「最近」store）全部写成不依赖组件的模块并有单元测试；页面只做展示与转发。界面行为由 `scripts/local/student-web-check.sh` 在真实浏览器、真实网关、本机 Ollama 上验收。

**Tech Stack:** Vue 3.5.26、vue-router 4.6.4、Pinia 2.3.1、Element Plus 2.13.0、axios 1.13.2、markdown-it 14.3.3、highlight.js 11.12.0、Vite 5.4.21、Vitest 2.1.9、playwright-cli（界面验收）。

**依据：** 设计文档 `docs/superpowers/specs/2026-10-20-student-web-ai-chat-design.md`（3 节布局与交互、4.1/4.2 接口与事件、6 节异常与内容安全、7.3 界面验收）；后端契约以后端计划 `docs/superpowers/plans/2026-10-20-student-ai-chat-phase1-backend.md` 任务 8、9 为准。

## Global Constraints

- 目录 `zhixue-student-web/`，开发端口 **3001**（`strictPort`），`/api` 代理到网关 `http://127.0.0.1:19001`（可用 `VITE_PROXY_TARGET` 覆盖），同源访问。
- 依赖版本**精确锁定**（见任务 1 的 `package.json`），提交 `package-lock.json`；Node.js ≥ 20（本机 22.22.3）。
- `src/styles/tokens.scss`、`src/styles/element.scss`、`src/components/BrandMark.vue`、`public/favicon.svg` **原样复制**自管理端，不得修改；学生端专属样式写在 `src/styles/index.scss` 或组件内。
- 所有 ID 在前端一律按**字符串**处理（雪花 ID 超出 JS 安全整数）；不得对 ID 做 `Number()`。
- 业务错误以 `body.code` 为准（后端业务错误是 HTTP 200）；网关鉴权失败是 HTTP 401。登录失效统一走 `sessionExpired()`：清登录态、带上原地址去登录页。
- 问题长度：去掉首尾空白后 1–500 字（与后端一致）。
- `v-html` 只允许出现在 `AnswerMarkdown.vue`，且内容必须来自 `renderMarkdown()`（禁用原始 HTML、只放行 http/https 链接、图片只显示为链接）。
- 前端请求路径必须写成 `url: '/…'` 字面量（`scripts/local/contract-check.sh` 据此核对网关路由）；单元测试放在 `tests/unit/`，不放进 `src/`（契约检查只扫 `src/`）。
- 一期侧边栏不显示「课程库 / 我的课程 / 商城 / 订单 / 收藏」（二期上线前点进去是空页面）；不做注册页（二期）。
- 界面文案为简体中文；按钮与图标按钮都有可读的 `aria-label`。
- 本机命令行是 zsh：凡是 `source scripts/local/common.sh && load_env` 的命令都放进脚本文件用 `bash x.sh` 执行；不要用 `bash <<'SH'` 从标准输入喂脚本（`docker exec -i`、`playwright-cli` 会读走标准输入）。

## 前置条件与执行顺序

| 任务 | 依赖的后端 | 可以开始的时间 |
|---|---|---|
| 1 工程骨架、登录与验收框架 | 现有 `/auth/*` | 任何时候 |
| 2 课程详情：试看、购买、播放 | 现有 `/course/*`、`/order/create`、`/pay` | 任务 1 之后 |
| 3 外壳与「最近」 | 后端计划**任务 8**（会话 4 个接口）已部署 | 后端检查点 B 之后、任务 8 部署后 |
| 4 流式对话核心逻辑（无界面） | 无（以后端任务 9 的事件格式为契约，用本地 HTTP 服务测试） | 任务 1 之后 |
| 5 对话页 | 后端计划**检查点 C**（任务 9 流式接口、任务 10 验收脚本）已通过 | 最后 |

---

## 文件结构

| 文件（`zhixue-student-web/` 下） | 职责 | 任务 |
|---|---|---|
| `package.json`、`vite.config.js`、`index.html`、`.env.*`、`.gitignore` | 工程配置；Vitest 配置写在 `vite.config.js` 的 `test` 段 | 1 |
| `src/styles/tokens.scss`、`element.scss`、`components/BrandMark.vue`、`public/favicon.svg` | 复制自管理端的设计令牌与品牌标识 | 1 |
| `src/styles/index.scss` | 全局基础样式 | 1 |
| `src/main.js`、`src/App.vue` | 入口；注册「登录失效」处理 | 1（3 补「最近」清理） |
| `src/utils/auth.js` | 令牌存取；输入框草稿的 sessionStorage 键 | 1 |
| `src/utils/session.js` | 登录失效的统一出口（请求层不依赖路由） | 1 |
| `src/utils/request.js` | axios 封装：带令牌、以 `body.code` 为准、`ApiError` | 1 |
| `src/utils/media.js` | 响应式媒体查询 | 1 |
| `src/api/auth.js`、`src/stores/user.js` | 登录、用户信息、退出 | 1 |
| `src/router/guard.js`、`src/router/index.js` | 登录守卫、防开放重定向；路由表 | 1（2、3 加路由） |
| `src/views/login/LoginView.vue` | 账号密码 + 验证码登录 | 1 |
| `src/layout/AppShell.vue`、`SideNav.vue` | 外壳：宽屏固定侧边栏，< 960px 抽屉 | 1（3 接入「最近」） |
| `src/utils/course.js` | 课程目录编号、时长、价格、卡片状态 | 2 |
| `src/api/course.js`、`src/api/order.js` | 课程详情与目录；下单、模拟收银台 | 2 |
| `src/views/course/CourseDetail.vue`、`LessonPlayer.vue` | 课程详情、试看/购买/播放、「问这门课」 | 2 |
| `src/api/conversations.js`、`src/stores/conversations.js` | 会话列表、重命名、删除；「最近」状态 | 3 |
| `src/layout/RecentList.vue` | 「最近」列表与 ⋯ 菜单 | 3 |
| `src/api/sse.js` | SSE 字节流解析（跨块、CRLF、多字节字符） | 4 |
| `src/api/chat.js` | 流式提问客户端：区分流前错误与流内事件，返回结束方式 | 4 |
| `src/views/chat/chatSession.js` | 对话状态机：发送、停止、重试、打开历史、撤回被拒绝的提问 | 4 |
| `src/utils/markdown.js` | 回答的安全 Markdown 渲染与代码高亮 | 5 |
| `src/views/chat/ChatView.vue`、`MessageList.vue`、`Composer.vue`、`AnswerMarkdown.vue`、`RecommendationCards.vue` | 对话页 | 5（1 先放落地页） |
| `tests/unit/**` | 单元测试（Vitest，node 环境） | 1–5 |

仓库根目录下：

| 文件 | 职责 | 任务 |
|---|---|---|
| `scripts/local/lib/ui.sh` | 界面验收共用函数：`pwf`（执行一段 Playwright 代码）、`check`、登录、接口购买与退款复原、启动开发服务器 | 1（2 起使用购买/退款） |
| `scripts/local/student-web-check.sh` | 界面验收入口：按 login → course → shell → chat 分段执行，退出时复原数据 | 1 |
| `scripts/local/ui/student-web-{login,course,shell,chat}.sh` | 各分段 | 1、2、3、5 |
| `README.md` | 模块结构、启动、回归命令 | 1 |

## 架构决策

1. **不用 `EventSource`**：只能 GET、不能带 `Authorization`；问题最长 500 字也不适合放查询串。改用 `fetch` + `ReadableStream`，`readSse` 自行解析，兼容 Spring 输出的 `event:x`（冒号后无空格）。
2. **`askStream` 不抛异常，返回结束方式**：`done / error / rejected / aborted / interrupted` 五种，调用方用一个 `switch` 处理全部 6.1 节异常矩阵；比 `onDone / onError` 回调更容易测，也不会漏掉「流在 done 之前就断了」这种情况。（设计文档 3.4 写的是回调形式，任务 5 同步更新文档。）
3. **会话状态机独立于组件**（`chatSession.js`）：依赖全部注入，23 个单元测试覆盖发送、追问、校验、停止后 409 自动重发一次、失败、断线、409 冷却、401 保留草稿、404 回到新对话、切换会话中止旧回答、历史状态还原。组件只负责展示。
4. **新会话不重建页面**：`/chat` 与 `/chat/:id` 共用同一个 `ChatView` 实例；收到 `meta` 后 `router.replace` 到 `/chat/:id`，watch 发现 ID 与当前会话相同就不重新加载，流式输出不中断。
5. **从推荐卡片点课时 → 进入课程详情 `/course/:id?lesson=…`**：详情页按服务端判定直接播放或弹出购买提示，播放器与购买流程只有一份。离开对话页时进行中的回答按「已停止」保存，返回时从服务端重新加载，卡片就是最新的「已购」状态（设计文档 4.5）。
6. **Element Plus 与管理端一样全量引入**：一期页面用到的组件少，但按需引入需要额外的自动导入插件，且 `ElMessage` 等函数式调用的样式要单独处理；全量引入单独成块（gzip 约 250KB），以后需要再优化。
7. **测试只用 node 环境**：不引入 jsdom 与组件测试库。组件行为（键盘、抽屉、渲染、菜单）由界面验收脚本在真实浏览器里验证。

## 风险与对策

| 风险 | 对策 |
|---|---|
| 代理或网关缓冲导致「流式」变成一次性返回 | Vite 代理默认不缓冲；后端已带 `X-Accel-Buffering: no`。验收脚本检查回答文字至少出现 3 种不同长度 |
| 模型输出中的 HTML/脚本 | `html:false`，链接与图片白名单，8 个单元测试；`v-html` 仅此一处 |
| 雪花 ID 精度丢失 | 全程字符串；store 与状态机测试使用 19 位 ID |
| 验收脚本改动数据（购买、会话） | 每个分段登记需要复原的数据，`trap` 在退出时退款、删会话、恢复 Ollama；开始前检查课程 2007 必须是未购 |
| 测试代码被契约检查当成真实请求 | 测试放在 `tests/unit/`，契约检查只扫 `src/` |

---

### Task 1: 工程骨架、登录与界面验收框架

> 体量说明：文件多，但大半是工程配置、四个原样复制的文件和验收脚本框架，它们只有和「能登录」这个可验证结果放在一起才有意义；真正需要审查的逻辑是请求封装、守卫与登录页，各有测试或界面验收覆盖。

**Files:**
- Create: `zhixue-student-web/.gitignore`
- Create: `zhixue-student-web/package.json`
- Create: `zhixue-student-web/.env.development`
- Create: `zhixue-student-web/.env.production`
- Create: `zhixue-student-web/index.html`
- Create: `zhixue-student-web/vite.config.js`
- Create: `zhixue-student-web/public/favicon.svg`
- Create: `zhixue-student-web/src/styles/tokens.scss`
- Create: `zhixue-student-web/src/styles/element.scss`
- Create: `zhixue-student-web/src/components/BrandMark.vue`
- Create: `zhixue-student-web/src/styles/index.scss`
- Create: `zhixue-student-web/src/main.js`
- Create: `zhixue-student-web/src/App.vue`
- Create: `zhixue-student-web/src/utils/media.js`
- Create: `zhixue-student-web/src/api/auth.js`
- Create: `zhixue-student-web/src/stores/user.js`
- Create: `zhixue-student-web/src/router/index.js`
- Create: `zhixue-student-web/src/views/login/LoginView.vue`
- Create: `zhixue-student-web/src/layout/AppShell.vue`
- Create: `zhixue-student-web/src/layout/SideNav.vue`
- Create: `zhixue-student-web/src/views/chat/ChatView.vue`
- Create: `zhixue-student-web/src/utils/auth.js`
- Create: `zhixue-student-web/src/utils/session.js`
- Create: `zhixue-student-web/src/utils/request.js`
- Create: `zhixue-student-web/src/router/guard.js`
- Test: `zhixue-student-web/tests/unit/utils/request.test.js`
- Test: `zhixue-student-web/tests/unit/router/guard.test.js`
- Create: `scripts/local/lib/ui.sh`
- Create: `scripts/local/student-web-check.sh`
- Create: `scripts/local/ui/student-web-login.sh`
- Modify: `scripts/local/contract-check.sh`（确认有 `scan_dir "student-web"` 一行）
- Modify: `README.md`（技术栈、模块结构、启动前端、本地回归）

**Interfaces:**
- Produces:
  - `request`（默认导出）：成功时 resolve 为 `body.data`；失败 reject `ApiError{code, message}`，`code` 为 `body.code` 或 HTTP 状态码，网络不通/超时为 0；请求配置 `skipAuthRedirect: true` 时 401 不触发全局跳转
  - `createRequest({ baseURL, getToken, onUnauthorized, timeout })`、`class ApiError`
  - `getToken() / setToken(token) / removeToken()`（localStorage 键 `zx_student_token`）、`CHAT_DRAFT_KEY = 'zx_chat_draft'`
  - `setSessionExpiredHandler(fn) / sessionExpired()`
  - `useUserStore()`：`token`、`profile`、`isLoggedIn`、`displayName`、`login({username, password, code, uuid})`、`loadProfile()`、`logout()`、`reset()`
  - `createAuthGuard(getUser, notify)`、`safeRedirect(target)`、`ID_PATTERN = /^\d{1,19}$/`：路径参数不是合法 ID 时提示并回到 `/chat`（后端会把它当参数错误返回 400）；路由名 `login`、`chat`（任务 2 加 `course`，任务 3 加 `conversation`）
  - `useMediaQuery(query)`
  - 验收：`scripts/local/lib/ui.sh` 的 `pwf`、`check`、`json_get`、`redis_get`、`student_token`、`admin_token`、`purchase_course`、`refund_course`、`ensure_student_web`；`student-web-check.sh [分段…]`，分段文件定义 `section_<名称>` 函数

- [ ] **Step 1: 建工程与依赖**

`zhixue-student-web/.gitignore`：

```
node_modules
dist
*.local
*.log
.DS_Store
```

`zhixue-student-web/package.json`：

```json
{
  "name": "zhixue-student-web",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "description": "智学云学生端网页版：AI 课程助教对话与视频课推荐",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview --port 3001",
    "test": "vitest run"
  },
  "engines": {
    "node": ">=20"
  },
  "dependencies": {
    "@element-plus/icons-vue": "2.3.2",
    "@fontsource-variable/geist": "5.3.0",
    "axios": "1.13.2",
    "element-plus": "2.13.0",
    "highlight.js": "11.12.0",
    "markdown-it": "14.3.3",
    "pinia": "2.3.1",
    "vue": "3.5.26",
    "vue-router": "4.6.4"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "5.2.4",
    "sass": "1.97.1",
    "vite": "5.4.21",
    "vitest": "2.1.9"
  }
}
```

`zhixue-student-web/.env.development`：

```
VITE_APP_BASE_API=/api
VITE_APP_TITLE=智学云
```

`zhixue-student-web/.env.production`：

```
VITE_APP_BASE_API=/api
VITE_APP_TITLE=智学云
```

`zhixue-student-web/index.html`：

```html
<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/favicon.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <meta name="theme-color" content="#2563EB" />
    <meta name="description" content="智学云：向 AI 课程助教提问，获得相关视频课推荐" />
    <title>%VITE_APP_TITLE%</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
  </body>
</html>
```

`zhixue-student-web/vite.config.js`：

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

const proxyTarget = process.env.VITE_PROXY_TARGET || 'http://127.0.0.1:19001'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },
  css: {
    // Vite 5 默认走 Sass 旧版 JS API，会刷屏弃用告警
    preprocessorOptions: { scss: { api: 'modern-compiler' } }
  },
  server: {
    port: 3001,
    strictPort: true,
    proxy: {
      // 与管理端相同：/api 去掉前缀后转给网关，同源访问，不涉及跨域
      '/api': {
        target: proxyTarget,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // Element Plus 与管理端一致为全量引入（约 790KB，gzip 后约 250KB），单独成块并放宽告警阈值
    chunkSizeWarningLimit: 900,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) {
            return undefined
          }
          if (id.includes('element-plus')) {
            return 'element-plus'
          }
          if (id.includes('highlight.js') || id.includes('markdown-it')) {
            return 'markdown'
          }
          return 'vendor'
        }
      }
    }
  },
  test: {
    environment: 'node',
    include: ['tests/unit/**/*.test.js']
  }
})
```

安装依赖并复制管理端的设计令牌与品牌标识（原样复制，之后不要改这四个文件）：

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web
npm install --no-audit --no-fund
mkdir -p public src/styles src/components
cp ../zhixue-admin-web/public/favicon.svg public/
cp ../zhixue-admin-web/src/styles/tokens.scss ../zhixue-admin-web/src/styles/element.scss src/styles/
cp ../zhixue-admin-web/src/components/BrandMark/index.vue src/components/BrandMark.vue
ls node_modules/.bin | grep -E '^(vite|vitest)$'
```

Expected: `added … packages`；最后一行输出 `vite`、`vitest`。

- [ ] **Step 2: 写失败测试**

`zhixue-student-web/tests/unit/utils/request.test.js`：

```js
import http from 'node:http'
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { ApiError, createRequest } from '@/utils/request'

function json(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(body))
}

const routes = {
  '/ok': (req, res) => json(res, 200, { code: 200, msg: 'ok', data: { auth: req.headers.authorization || null } }),
  '/biz-error': (req, res) => json(res, 200, { code: 409, msg: '上一个回答还在生成' }),
  '/body-401': (req, res) => json(res, 200, { code: 401, msg: '未登录或令牌已失效' }),
  '/http-401': (req, res) => json(res, 401, { code: 401, msg: '令牌无效或已过期' }),
  '/http-500': (req, res) => json(res, 500, { code: 500, msg: '系统异常' })
}

let server
let baseURL

beforeAll(async () => {
  server = http.createServer((req, res) => (routes[req.url] || ((q, r) => json(r, 404, {})))(req, res))
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  baseURL = `http://127.0.0.1:${server.address().port}`
})

afterAll(() => new Promise((resolve) => {
  server.closeAllConnections()
  server.close(resolve)
}))

const make = (overrides = {}) => createRequest({ baseURL, getToken: () => '', onUnauthorized: vi.fn(), ...overrides })

describe('createRequest', () => {
  it('带上令牌，成功时直接返回 data', async () => {
    await expect(make({ getToken: () => 't-1' })({ url: '/ok' })).resolves.toEqual({ auth: 'Bearer t-1' })
  })

  it('业务错误是 HTTP 200 + body.code，以 body.code 为准', async () => {
    const error = await make()({ url: '/biz-error' }).catch((e) => e)
    expect(error).toBeInstanceOf(ApiError)
    expect([error.code, error.message]).toEqual([409, '上一个回答还在生成'])
  })

  it.each(['/body-401', '/http-401'])('%s：视为登录失效', async (url) => {
    const onUnauthorized = vi.fn()
    const error = await make({ onUnauthorized })({ url }).catch((e) => e)
    expect(error.code).toBe(401)
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('skipAuthRedirect：401 交给调用方处理，不触发全局跳转', async () => {
    const onUnauthorized = vi.fn()
    const error = await make({ onUnauthorized })({ url: '/http-401', skipAuthRedirect: true }).catch((e) => e)
    expect(error.code).toBe(401)
    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  it('HTTP 错误带上服务端的说明', async () => {
    const error = await make()({ url: '/http-500' }).catch((e) => e)
    expect([error.code, error.message]).toEqual([500, '系统异常'])
  })

  it('连不上服务：提示网络问题', async () => {
    const request = createRequest({ baseURL: 'http://127.0.0.1:1', getToken: () => '', onUnauthorized: vi.fn() })
    const error = await request({ url: '/ok' }).catch((e) => e)
    expect([error.code, error.message]).toEqual([0, '网络连接失败，请检查网络'])
  })
})
```

`zhixue-student-web/tests/unit/router/guard.test.js`：

```js
import { describe, expect, it, vi } from 'vitest'
import { ID_PATTERN, createAuthGuard, safeRedirect } from '@/router/guard'

const route = (fullPath, meta = {}) => ({ fullPath, meta })
const user = (overrides = {}) => ({
  isLoggedIn: true,
  profile: { userId: 1003 },
  loadProfile: vi.fn().mockResolvedValue({ userId: 1003 }),
  reset: vi.fn(),
  ...overrides
})

describe('createAuthGuard', () => {
  it('登录页等公开页面直接放行', async () => {
    expect(await createAuthGuard(() => user({ isLoggedIn: false }))(route('/login', { public: true }))).toBe(true)
  })

  it('未登录：去登录页并记住要回到的地址', async () => {
    const guard = createAuthGuard(() => user({ isLoggedIn: false, profile: null }))
    expect(await guard(route('/chat/2112366523950714882'))).toEqual({
      name: 'login',
      query: { redirect: '/chat/2112366523950714882' }
    })
  })

  it('已取过用户信息：直接放行，不重复请求', async () => {
    const u = user()
    expect(await createAuthGuard(() => u)(route('/chat'))).toBe(true)
    expect(u.loadProfile).not.toHaveBeenCalled()
  })

  it('有令牌但还没取用户信息：先取再放行', async () => {
    const u = user({ profile: null })
    expect(await createAuthGuard(() => u)(route('/chat'))).toBe(true)
    expect(u.loadProfile).toHaveBeenCalledOnce()
  })

  it('令牌失效（取用户信息失败）：清掉登录态，去登录页', async () => {
    const u = user({ profile: null, loadProfile: vi.fn().mockRejectedValue(new Error('令牌无效')) })
    expect(await createAuthGuard(() => u)(route('/chat/1'))).toEqual({ name: 'login', query: { redirect: '/chat/1' } })
    expect(u.reset).toHaveBeenCalledOnce()
  })
})

describe('路径参数校验', () => {
  it.each([
    ['2112366523950714882', true],
    ['9001', true],
    ['abc', false],
    ['12abc', false],
    ['-1', false],
    ['21123665239507148820', false]
  ])('%s → %s', (value, expected) => {
    expect(ID_PATTERN.test(value)).toBe(expected)
  })

  it.each(['/chat/abc', '/chat/12x'])('地址参数不合法时回到新对话：%s', async (path) => {
    const notify = vi.fn()
    const guard = createAuthGuard(() => user(), notify)
    const result = await guard({ fullPath: path, meta: {}, params: { id: path.split('/')[2] } })
    expect(result).toBe('/chat')
    expect(notify).toHaveBeenCalledWith('warning', expect.stringContaining('地址不正确'))
  })

  it('合法 ID 正常放行', async () => {
    expect(await createAuthGuard(() => user())({ fullPath: '/chat/9001', meta: {}, params: { id: '9001' } })).toBe(true)
  })
})

describe('safeRedirect', () => {
  it.each([
    ['/chat/1', '/chat/1'],
    ['/course/2007?lesson=4024', '/course/2007?lesson=4024'],
    ['//evil.example.com', '/chat'],
    ['https://evil.example.com', '/chat'],
    ['/login?redirect=/chat', '/chat'],
    [undefined, '/chat'],
    [['/chat/1'], '/chat']
  ])('%j → %s', (input, expected) => {
    expect(safeRedirect(input)).toBe(expected)
  })
})
```

- [ ] **Step 3: 运行确认失败**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm test 2>&1 | grep -E "Failed to resolve|Test Files"
```

Expected: `Failed to resolve import "@/utils/request"`、`Failed to resolve import "@/router/guard"`；`Test Files  2 failed (2)`。

- [ ] **Step 4: 实现令牌、登录失效出口、请求封装与守卫**

`zhixue-student-web/src/utils/auth.js`：

```js
const TOKEN_KEY = 'zx_student_token'

/** 输入框草稿（sessionStorage）：登录失效跳去登录、回来后问题仍在输入框里 */
export const CHAT_DRAFT_KEY = 'zx_chat_draft'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

export function removeToken() {
  localStorage.removeItem(TOKEN_KEY)
}
```

`zhixue-student-web/src/utils/session.js`：

```js
/**
 * 登录失效的统一出口。请求层只负责「发现」失效，怎么处理（清登录态、带上原地址去登录页）
 * 由 main.js 注册——这样请求层不依赖路由，也能单独测试。
 */
let handler = () => {}

export function setSessionExpiredHandler(fn) {
  handler = fn
}

export function sessionExpired() {
  handler()
}
```

`zhixue-student-web/src/utils/request.js`：

```js
import axios from 'axios'
import { getToken } from './auth'
import { sessionExpired } from './session'

/** 接口错误。code 为 body.code（业务错误）或 HTTP 状态码；0 表示网络不通或超时 */
export class ApiError extends Error {
  constructor(code, message) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

/**
 * 创建请求实例。约定与后端一致：
 * - 业务错误返回 HTTP 200，错误码在 body.code，以它为准；
 * - 网关鉴权失败返回 HTTP 401。两种 401 都视为登录失效，调用 onUnauthorized；
 *   请求配置 skipAuthRedirect: true 时不调用，由调用方自己处理（例如路由守卫要带上原地址）。
 * 成功时直接返回 body.data。这里不弹提示，由页面决定怎么告诉学生。
 */
export function createRequest({ baseURL, getToken: readToken, onUnauthorized, timeout = 15000 }) {
  const instance = axios.create({ baseURL, timeout })

  instance.interceptors.request.use((config) => {
    const token = readToken()
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  })

  instance.interceptors.response.use(
    (response) => {
      const body = response.data
      const wrapped = body !== null && typeof body === 'object' && 'code' in body
      if (wrapped && body.code !== 200) {
        if (body.code === 401 && !response.config.skipAuthRedirect) {
          onUnauthorized()
        }
        return Promise.reject(new ApiError(body.code, body.msg || '请求失败'))
      }
      return wrapped ? body.data : body
    },
    (error) => {
      const status = error.response?.status
      if (status === 401) {
        if (!error.config?.skipAuthRedirect) {
          onUnauthorized()
        }
        return Promise.reject(new ApiError(401, error.response.data?.msg || '登录已失效，请重新登录'))
      }
      if (error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT') {
        return Promise.reject(new ApiError(0, '请求超时，请稍后重试'))
      }
      if (!error.response) {
        return Promise.reject(new ApiError(0, '网络连接失败，请检查网络'))
      }
      return Promise.reject(new ApiError(status, error.response.data?.msg || `请求失败（${status}）`))
    }
  )
  return instance
}

export default createRequest({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  getToken,
  onUnauthorized: sessionExpired
})
```

`zhixue-student-web/src/router/guard.js`：

```js
/** 19 位以内纯数字：雪花 ID 在 URL 里的合法形式。不合法时按「不存在」处理，不去请求后端 */
export const ID_PATTERN = /^\d{1,19}$/

/**
 * 登录后要回到的地址只能是站内路径：拒绝 //evil.com、https://… 这类外部地址（开放重定向），
 * 也不回到登录页本身。
 */
export function safeRedirect(target) {
  const ok = typeof target === 'string' && target.startsWith('/') && !target.startsWith('//') && !target.startsWith('/login')
  return ok ? target : '/chat'
}

/**
 * 路由守卫：未登录去登录页并记住原地址；有令牌但还没取用户信息时先取，取不到（令牌失效）则清掉登录态。
 * @param {() => { isLoggedIn: boolean, profile: object|null, loadProfile: Function, reset: Function }} getUser
 */
export function createAuthGuard(getUser, notify) {
  return async (to) => {
    if (to.meta?.public) {
      return true
    }
    // 手输或误改地址（/chat/abc、?lesson=abc）在后端会被当成参数错误（400），这里先按「不存在」处理
    if (to.params?.id !== undefined && !ID_PATTERN.test(String(to.params.id))) {
      notify?.('warning', '页面地址不正确，已回到新对话')
      return '/chat'
    }
    const user = getUser()
    const toLogin = { name: 'login', query: { redirect: to.fullPath } }
    if (!user.isLoggedIn) {
      return toLogin
    }
    if (!user.profile) {
      try {
        await user.loadProfile()
      } catch {
        user.reset()
        return toLogin
      }
    }
    return true
  }
}
```

- [ ] **Step 5: 运行确认通过**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm test 2>&1 | grep -E "Test Files|Tests"
```

Expected: `Test Files  2 passed (2)`、`Tests  28 passed (28)`。

- [ ] **Step 6: 页面、外壳与登录后的落地页**

`ChatView.vue` 先是一个落地页，任务 5 整体替换；`SideNav.vue` 在任务 3 接入「最近」，`main.js` 在任务 3 增加退出登录时清空「最近」。

`zhixue-student-web/src/styles/index.scss`：

```scss
// 全局样式入口。tokens.scss、element.scss 原样复制自管理端，保持两端视觉一致；不要在这两个文件里改学生端专属样式
@use './tokens';
@use './element';

*,
*::before,
*::after {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body,
#app {
  width: 100%;
  height: 100%;
}

body {
  font-family: var(--el-font-family);
  font-size: var(--el-font-size-base);
  line-height: 1.6;
  color: var(--el-text-color-regular);
  background: var(--zx-surface);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-rendering: optimizeLegibility;
}

a {
  color: inherit;
  text-decoration: none;
}

h1,
h2,
h3,
h4 {
  color: var(--el-text-color-primary);
  font-weight: 600;
  letter-spacing: -0.01em;
}

:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 2px;
}

::selection {
  background: var(--el-color-primary-light-8);
  color: var(--el-text-color-primary);
}

.tabular-nums {
  font-variant-numeric: tabular-nums;
}

::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

::-webkit-scrollbar-thumb {
  background: var(--el-border-color-dark);
  border-radius: 8px;
  border: 2px solid transparent;
  background-clip: content-box;
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
    scroll-behavior: auto !important;
  }
}
```

`zhixue-student-web/src/main.js`：

```js
import { createApp } from 'vue'
import { ElMessage } from 'element-plus'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'

// 主题与全局样式必须在 Element Plus 样式之后引入
import '@fontsource-variable/geist'
import '@/styles/index.scss'

import App from './App.vue'
import router from './router'
import { useUserStore } from './stores/user'
import { setSessionExpiredHandler } from './utils/session'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 任何请求发现登录失效：清掉本地登录态，带上当前地址去登录页，登录后回到原处
setSessionExpiredHandler(() => {
  useUserStore(pinia).reset()
  const current = router.currentRoute.value
  if (current.name && current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
```

`zhixue-student-web/src/App.vue`：

```vue
<template>
  <router-view />
</template>
```

`zhixue-student-web/src/utils/media.js`：

```js
import { onBeforeUnmount, ref } from 'vue'

/** 响应式的媒体查询结果，组件卸载时自动解除监听 */
export function useMediaQuery(query) {
  const mql = window.matchMedia(query)
  const matches = ref(mql.matches)
  const update = (event) => {
    matches.value = event.matches
  }
  mql.addEventListener('change', update)
  onBeforeUnmount(() => mql.removeEventListener('change', update))
  return matches
}
```

`zhixue-student-web/src/api/auth.js`：

```js
import request from '@/utils/request'

export const getCaptcha = () => request({ url: '/auth/captcha', method: 'get' })

export const login = (form) => request({ url: '/auth/login', method: 'post', data: { ...form, loginType: 'password' } })

// 由路由守卫与登录页自己处理 401（要带上原地址），不触发全局跳转
export const getUserInfo = () => request({ url: '/auth/user/info', method: 'get', skipAuthRedirect: true })

export const logout = () => request({ url: '/auth/logout', method: 'post', skipAuthRedirect: true })
```

`zhixue-student-web/src/stores/user.js`：

```js
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getUserInfo, login as loginApi, logout as logoutApi } from '@/api/auth'
import { getToken, removeToken, setToken } from '@/utils/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  /** GET /auth/user/info：{ userId, username, nickname, avatar, roles, permissions } */
  const profile = ref(null)

  const isLoggedIn = computed(() => !!token.value)
  const displayName = computed(() => profile.value?.nickname || profile.value?.username || '同学')

  async function login({ username, password, code, uuid }) {
    const data = await loginApi({ username, password, code, uuid })
    if (!data?.token) {
      throw new Error('登录响应缺少令牌')
    }
    token.value = data.token
    setToken(data.token)
    profile.value = null
  }

  async function loadProfile() {
    profile.value = await getUserInfo()
    return profile.value
  }

  async function logout() {
    try {
      await logoutApi()
    } catch {
      // 服务端登出失败也要清掉本地登录态
    } finally {
      reset()
    }
  }

  function reset() {
    token.value = ''
    profile.value = null
    removeToken()
  }

  return { token, profile, isLoggedIn, displayName, login, loadProfile, logout, reset }
})
```

`zhixue-student-web/src/router/index.js`：

```js
import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppShell from '@/layout/AppShell.vue'
import ChatView from '@/views/chat/ChatView.vue'
import { useUserStore } from '@/stores/user'
import { createAuthGuard } from './guard'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/login/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: AppShell,
    children: [
      { path: '', redirect: { name: 'chat' } },
      { path: 'chat', name: 'chat', component: ChatView }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'chat' } }
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(createAuthGuard(() => useUserStore(), (type, message) => ElMessage({ type, message })))

export default router
```

`zhixue-student-web/src/views/login/LoginView.vue`：

```vue
<template>
  <div class="login-page">
    <main class="login-card">
      <div class="brand">
        <BrandMark :size="36" />
        <span>智学云</span>
      </div>

      <header class="head">
        <h1>登录学习</h1>
        <p>登录后可以向 AI 课程助教提问，并获得相关的视频课推荐</p>
      </header>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        autocomplete="on"
        @submit.prevent="submit"
      >
        <el-form-item label="账号" prop="username">
          <el-input v-model="form.username" name="username" autocomplete="username" placeholder="请输入账号" :prefix-icon="User" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            name="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item label="验证码" prop="code">
          <div class="captcha-row">
            <el-input v-model="form.code" maxlength="8" autocomplete="off" placeholder="右侧字符" :prefix-icon="Key" />
            <button type="button" class="captcha" title="看不清？点击换一张" aria-label="刷新验证码" @click="refreshCaptcha">
              <img v-if="captcha" :src="captcha" alt="验证码" />
              <span v-else>获取验证码</span>
            </button>
          </div>
        </el-form-item>

        <p v-if="error" class="form-error" role="alert">{{ error }}</p>

        <el-button type="primary" native-type="submit" class="submit" :loading="loading">
          {{ loading ? '登录中' : '登录' }}
        </el-button>
      </el-form>
    </main>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Key, Lock, User } from '@element-plus/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import { getCaptcha } from '@/api/auth'
import { safeRedirect } from '@/router/guard'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const user = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const error = ref('')
const captcha = ref('')
const form = reactive({ username: '', password: '', code: '', uuid: '' })
const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

async function refreshCaptcha() {
  try {
    const data = await getCaptcha()
    captcha.value = data.img?.startsWith('data:') ? data.img : `data:image/png;base64,${data.img}`
    form.uuid = data.uuid
    form.code = ''
  } catch {
    error.value = '获取验证码失败，请点击图片重试'
  }
}

async function submit() {
  if (loading.value) {
    return
  }
  error.value = ''
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }
  loading.value = true
  try {
    await user.login(form)
    await user.loadProfile()
    router.replace(safeRedirect(route.query.redirect))
  } catch (e) {
    error.value = e.message || '登录失败，请重试'
    await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (user.isLoggedIn) {
    router.replace(safeRedirect(route.query.redirect))
    return
  }
  refreshCaptcha()
})
</script>

<style lang="scss" scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100dvh;
  padding: 32px 16px;
  background:
    radial-gradient(50% 40% at 50% 0%, color-mix(in srgb, var(--el-color-primary) 10%, transparent) 0%, transparent 100%),
    var(--el-fill-color-lighter);
}

.login-card {
  width: 100%;
  max-width: 400px;
  padding: 32px 32px 28px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 16px;
  background: var(--zx-surface);
  box-shadow: var(--el-box-shadow-light);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.head {
  margin: 22px 0 22px;

  h1 {
    font-size: 22px;
  }

  p {
    margin-top: 6px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.captcha-row {
  display: flex;
  gap: 10px;
  width: 100%;

  .el-input {
    flex: 1;
    min-width: 0;
  }
}

.captcha {
  width: 120px;
  height: 40px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-lighter);
  color: var(--el-text-color-secondary);
  font-size: 12px;
  cursor: pointer;

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.form-error {
  margin: -4px 0 14px;
  font-size: 13px;
  color: var(--el-color-danger);
}

.submit {
  width: 100%;
  height: 42px;
  font-size: 15px;
}
</style>
```

`zhixue-student-web/src/layout/AppShell.vue`：

```vue
<template>
  <div class="app-shell" :class="{ narrow: isNarrow }">
    <aside v-if="!isNarrow" class="sidebar">
      <SideNav />
    </aside>
    <el-drawer v-else v-model="drawerOpen" direction="ltr" size="280px" :with-header="false" class="nav-drawer">
      <SideNav @navigate="drawerOpen = false" />
    </el-drawer>

    <main class="main">
      <header v-if="isNarrow" class="topbar">
        <button type="button" class="icon-btn" aria-label="打开导航" @click="drawerOpen = true">
          <el-icon :size="20"><Expand /></el-icon>
        </button>
        <span class="topbar-title">智学云</span>
      </header>
      <div class="page">
        <router-view />
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Expand } from '@element-plus/icons-vue'
import SideNav from './SideNav.vue'
import { useMediaQuery } from '@/utils/media'

// 与设计文档一致：宽度 < 960px 时侧边栏收起为抽屉
const isNarrow = useMediaQuery('(max-width: 959px)')
const drawerOpen = ref(false)
const route = useRoute()

watch(() => route.fullPath, () => {
  drawerOpen.value = false
})
</script>

<style lang="scss" scoped>
.app-shell {
  display: grid;
  grid-template-columns: 264px minmax(0, 1fr);
  height: 100dvh;
  background: var(--zx-surface);

  &.narrow {
    grid-template-columns: minmax(0, 1fr);
  }
}

.sidebar {
  min-height: 0;
  border-right: 1px solid var(--el-border-color-lighter);
}

.main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 52px;
  flex-shrink: 0;
  padding: 0 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.topbar-title {
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.icon-btn {
  width: 36px;
  height: 36px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-primary);
  cursor: pointer;

  &:hover {
    background: var(--el-fill-color);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
</style>

<style lang="scss">
// 抽屉渲染在组件外，用全局样式去掉默认内边距
.nav-drawer .el-drawer__body {
  padding: 0;
}
</style>
```

`zhixue-student-web/src/layout/SideNav.vue`：

```vue
<template>
  <nav class="side-nav" aria-label="主导航">
    <div class="brand">
      <BrandMark :size="28" />
      <span>智学云</span>
    </div>

    <button type="button" class="new-chat" @click="newChat">
      <el-icon :size="16"><Plus /></el-icon>
      <span>新对话</span>
    </button>

    <!-- 二期上线课程库、我的课程、商城、订单、收藏后再显示入口，避免点进去是空页面 -->
    <div class="nav-scroll">
    </div>

    <div class="user-footer">
      <el-dropdown trigger="click" placement="top-start" @command="onCommand">
        <button type="button" class="user-btn" aria-label="账号菜单">
          <span class="avatar" aria-hidden="true">{{ initial }}</span>
          <span class="user-name">{{ user.displayName }}</span>
          <el-icon class="user-more"><MoreFilled /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { MoreFilled, Plus, SwitchButton } from '@element-plus/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import { useUserStore } from '@/stores/user'
import { CHAT_DRAFT_KEY } from '@/utils/auth'

const emit = defineEmits(['navigate'])
const router = useRouter()
const user = useUserStore()
const initial = computed(() => user.displayName.slice(0, 1))

function newChat() {
  emit('navigate')
  router.push({ name: 'chat' })
}

async function onCommand(command) {
  if (command !== 'logout') {
    return
  }
  await user.logout()
  sessionStorage.removeItem(CHAT_DRAFT_KEY)
  router.replace({ name: 'login' })
}
</script>

<style lang="scss" scoped>
.side-nav {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 14px 10px 10px;
  background: var(--el-fill-color-lighter);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 8px 14px;
  font-size: 16px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.new-chat {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--zx-surface);
  color: var(--el-text-color-primary);
  font: inherit;
  font-weight: 550;
  cursor: pointer;
  transition: border-color 0.16s var(--zx-ease);

  &:hover {
    border-color: var(--el-color-primary-light-5);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.nav-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin: 16px -4px 8px;
  padding: 0 4px;
}

.user-footer {
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);

  :deep(.el-dropdown) {
    width: 100%;
  }
}

.user-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  cursor: pointer;

  &:hover {
    background: var(--el-fill-color);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.user-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-more {
  color: var(--el-text-color-secondary);
}
</style>
```

`zhixue-student-web/src/views/chat/ChatView.vue`：

```vue
<template>
  <!-- 任务 5 把本页整体替换为完整的对话页；这里先提供登录后的落地页 -->
  <div class="chat-view">
    <h1>有什么想学的？</h1>
    <p>问编程问题，AI 课程助教会结合课程资料回答，并推荐相关的视频课</p>
  </div>
</template>

<style lang="scss" scoped>
.chat-view {
  height: 100%;
  overflow-y: auto;
  padding: 14vh 24px 24px;
  text-align: center;

  h1 {
    font-size: 28px;
    letter-spacing: -0.02em;
  }

  p {
    margin-top: 8px;
    color: var(--el-text-color-secondary);
  }
}
</style>
```

- [ ] **Step 7: 构建**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm run build 2>&1 | grep -ciE "warn|deprecat|error"; npm run build 2>&1 | tail -1
```

Expected: 第一行 `0`（没有告警）；最后一行 `✓ built in …`。

- [ ] **Step 8: 界面验收框架与登录分段**

`scripts/local/lib/ui.sh`：

```bash
# 本地界面验收共用函数（被 student-web-check.sh 等脚本 source）。依赖 playwright-cli、python3、docker。
#
# 用法：
#   pwf <<'JS'                      # 执行一段 Playwright 代码（函数参数为 page），输出返回值
#   async page => { return await page.title() }
#   JS
#   check "说明" "$实际" "$期望"       # 相等输出 [ok]，否则 [fail] 并把 FAIL 置 1
#
# 注意：调用这些函数的脚本要以文件方式执行（bash x.sh），不要用 `bash <<'SH'` 从标准输入喂脚本——
# playwright-cli 与 docker exec -i 会读走标准输入，吞掉后续脚本。

FAIL=${FAIL:-0}

check() {
  if [[ "$2" == "$3" ]]; then
    echo "[ok] $1"
  else
    echo "[fail] $1：期望 [$3]，实际 [$2]"
    FAIL=1
  fi
}

# 从 playwright-cli 输出中取返回值；失败时输出「ERROR: …」，让 check 显示原因
pw_result() {
  python3 -c '
import json, sys
text = sys.stdin.read()
lines = text.splitlines()
for i, line in enumerate(lines):
    if line.startswith("### Result") and i + 1 < len(lines):
        raw = lines[i + 1].strip()
        try:
            value = json.loads(raw)
        except ValueError:
            value = raw
        print(value if isinstance(value, str) else json.dumps(value, ensure_ascii=False))
        sys.exit(0)
    if line.startswith("### Error"):
        print("ERROR: " + " ".join(l.strip() for l in lines[i + 1:i + 4])[:300])
        sys.exit(0)
print("ERROR: 无结果 " + text[:200].replace("\n", " "))'
}

pwf() {
  local f
  f=$(mktemp /tmp/zx_pw_XXXXXX)
  cat > "$f"
  playwright-cli run-code --filename "$f" 2>&1 | pw_result
  rm -f "$f"
}

# json_get '<json>' 'a.0.b'：取字段；字符串原样输出，其他类型输出 JSON；取不到为空
json_get() {
  python3 -c '
import json, sys
try:
    v = json.loads(sys.argv[1])
    for k in sys.argv[2].split("."):
        v = v[int(k)] if isinstance(v, list) else v[k]
    print(v if isinstance(v, str) else json.dumps(v, ensure_ascii=False))
except Exception:
    print("")' "$1" "$2"
}

redis_get() {
  docker exec zhixue-local-redis redis-cli -a "$ZHIXUE_REDIS_PASSWORD" --no-auth-warning GET "$1" | tr -d '\r'
}

# 学生令牌：本地微信沙箱登录（local.env 中 ZHIXUE_AUTH_WECHAT_MODE=sandbox），映射到 student 账号
student_token() {
  curl -s -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
    -d "{\"loginType\":\"wechat\",\"wechatCode\":\"${1:-ui-check}\"}" \
    | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["token"])'
}

# 管理员令牌：验证码从 Redis 读取
admin_token() {
  local uuid code
  uuid=$(curl -s "$GW/auth/captcha" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["uuid"])')
  code=$(redis_get "zhixue:auth:captcha:$uuid")
  curl -s -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
    -d "{\"username\":\"admin\",\"password\":\"123456\",\"code\":\"$code\",\"uuid\":\"$uuid\",\"loginType\":\"password\"}" \
    | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["token"])'
}

# 退掉学生（1003）对某门课的已支付订单，让课程恢复为未购；没有则什么都不做
refund_course() {
  local course_id="$1" at oid
  at=$(admin_token)
  oid=$(curl -s "$GW/order/list?pageNum=1&pageSize=50&userId=1003" -H "Authorization: Bearer $at" \
    | python3 -c 'import json,sys
r = json.load(sys.stdin)["data"]["records"]
print(next((str(x["id"]) for x in r if str(x["productId"]) == sys.argv[1] and x["status"] == 1), ""))' "$course_id")
  if [[ -n "$oid" ]]; then
    curl -s -o /dev/null -X PUT "$GW/order/$oid/refund" -H "Authorization: Bearer $at" \
      -H 'Content-Type: application/json' -d '{"reason":"本地界面验收后复原"}'
    echo "[info] 已退款订单 ${oid}，课程 $course_id 恢复为未购"
  fi
}

# 学生（1003）用接口直接买下一门课（下单 → 模拟收银台 → 确认），界面购买流程由 course 分段验收
purchase_course() {
  local token order_no
  token=$(student_token "${2:-ui-check}")
  order_no=$(curl -s -X POST "$GW/order/create" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"productId\":\"$1\"}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["orderNo"])')
  curl -s -o /dev/null -X POST "$GW/pay" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"orderNo\":\"$order_no\",\"payChannel\":\"mock\"}"
  curl -s -X POST "$GW/pay/mock/confirm" -H "Authorization: Bearer $token" -H 'Content-Type: application/json' \
    -d "{\"orderNo\":\"$order_no\"}" | python3 -c 'import json,sys;print(json.load(sys.stdin)["code"])'
}

# 学生网页版开发服务器：没在运行就在后台启动，设置 VITE_PID（由调用方在退出时结束）
ensure_student_web() {
  local web="http://localhost:3001"
  curl -sf -m 3 "$web/" >/dev/null && return 0
  (cd "${WEB_DIR:-$ROOT_DIR/zhixue-student-web}" && BROWSER=none npx vite --port 3001 --strictPort > /tmp/zhixue-student-web-vite.log 2>&1) &
  VITE_PID=$!
  for _ in $(seq 1 30); do curl -sf -m 2 "$web/" >/dev/null && return 0; sleep 1; done
  echo "[fail] 学生网页版开发服务器没有启动，见 /tmp/zhixue-student-web-vite.log"
  return 1
}
```

`scripts/local/student-web-check.sh`：

```bash
#!/usr/bin/env bash
# 学生端网页版界面验收（设计文档 7.3）：真实浏览器 + 真实网关 + 本机 Ollama。
#
# 用法：
#   bash scripts/local/student-web-check.sh               # 跑全部已有分段
#   bash scripts/local/student-web-check.sh login course  # 只跑指定分段
# 分段在 scripts/local/ui/student-web-<名称>.sh，按 login → course → shell → chat 的顺序执行；
# 每个分段自成一体，结束时复原它改动的数据（购买会退款、会话会删除、停掉的 Ollama 会恢复）。
#
# 前置：服务已启动；zhixue-student-web 下已 npm ci；local.env 中 ZHIXUE_AUTH_WECHAT_MODE=sandbox；
#       已安装 playwright-cli（npm i -g @playwright/cli）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
source "$SCRIPT_DIR/common.sh"
load_env
# common.sh 开启了 set -e：验收要跑完所有检查再汇总，这里关掉
set +e +u +o pipefail
source "$SCRIPT_DIR/lib/ui.sh"

WEB="http://localhost:3001"
GW="http://127.0.0.1:${ZHIXUE_GATEWAY_PORT:-19001}"
WEB_DIR="${ZHIXUE_STUDENT_WEB_DIR:-$ROOT_DIR/zhixue-student-web}"
OUT="${ZHIXUE_UI_OUT:-/tmp/zhixue-student-web-check}"
mkdir -p "$OUT"
FAIL=0
# 分段登记需要在退出时复原的数据
CLEANUP_CONVERSATIONS=()
CLEANUP_REFUND_COURSES=()
OLLAMA_STOPPED=0
VITE_PID=""

cleanup() {
  if [[ "$OLLAMA_STOPPED" == 1 ]]; then
    brew services start ollama >/dev/null 2>&1
  fi
  if [[ ${#CLEANUP_CONVERSATIONS[@]} -gt 0 ]]; then
    local ids
    ids=$(IFS=,; echo "${CLEANUP_CONVERSATIONS[*]}")
    docker exec zhixue-local-mysql mysql --default-character-set=utf8mb4 -uroot -p"$ZHIXUE_DB_PASSWORD" -N -e \
      "DELETE FROM zhixue_ai.ai_message WHERE conversation_id IN ($ids); DELETE FROM zhixue_ai.ai_conversation WHERE id IN ($ids);" \
      2>&1 | grep -v "Using a password"
    echo "[info] 已清理本次创建的 ${#CLEANUP_CONVERSATIONS[@]} 个会话"
  fi
  local course
  for course in "${CLEANUP_REFUND_COURSES[@]}"; do
    refund_course "$course"
  done
  playwright-cli close >/dev/null 2>&1
  [[ -n "$VITE_PID" ]] && kill "$VITE_PID" >/dev/null 2>&1
}
trap cleanup EXIT

command -v playwright-cli >/dev/null || { echo "[fail] 需要 playwright-cli（npm i -g @playwright/cli）"; exit 1; }
[[ -d "$WEB_DIR/node_modules" ]] || { echo "[fail] 先在 $WEB_DIR 下执行 npm ci"; exit 1; }
ensure_student_web || exit 1
# playwright-cli 会在当前目录写会话日志：在输出目录里运行，不污染仓库
cd "$OUT" || exit 1
playwright-cli open "$WEB/login" >/dev/null 2>&1 || playwright-cli goto "$WEB/login" >/dev/null 2>&1
playwright-cli resize 1440 900 >/dev/null 2>&1

# 已登录状态：直接写入令牌（登录表单本身由 login 分段验收）
login_as_student() {
  playwright-cli goto "$WEB/login" >/dev/null 2>&1
  playwright-cli localstorage-set zx_student_token "$(student_token ui-check)" >/dev/null 2>&1
}

# 课程 2007 必须是未购状态，购买流程才验收得了
require_not_purchased() {
  local owned
  owned=$(curl -s "$GW/course/detail/2007" -H "Authorization: Bearer $(student_token ui-check)" \
    | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["canStudy"])')
  if [[ "$owned" != "False" ]]; then
    echo "[fail] 学生已购买课程 2007，购买流程无法验收：先执行 source scripts/local/lib/ui.sh && refund_course 2007"
    FAIL=1
    return 1
  fi
}

ALL_SECTIONS=(login course shell chat)
REQUESTED=("$@")
[[ ${#REQUESTED[@]} -eq 0 ]] && REQUESTED=("${ALL_SECTIONS[@]}")
for name in "${ALL_SECTIONS[@]}"; do
  [[ " ${REQUESTED[*]} " == *" $name "* ]] || continue
  file="$SCRIPT_DIR/ui/student-web-$name.sh"
  if [[ ! -f "$file" ]]; then
    echo "[skip] ${name}：分段尚未实现（${file}）"
    continue
  fi
  echo "===================== $name ====================="
  source "$file"
  "section_$name"
done

echo
echo "截图（1440 与 390 两种宽度请人工核对）：$OUT"
ls "$OUT"/*.png 2>/dev/null
if [[ "$FAIL" == 0 ]]; then echo "[ok] 学生端网页版界面验收全部通过"; else echo "[fail] 学生端网页版界面验收有失败项"; fi
exit "$FAIL"
```

`scripts/local/ui/student-web-login.sh`：

```bash
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

  # 这里只验证「带着失效令牌访问受保护页面 → 记住地址去登录页」；带参数的课程地址
  # 在任务 2 的课程分段里验证（本任务还没有课程路由，/course/2007 会被兜底规则改写成 /chat）
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
```

确认契约检查会扫描本工程（后端计划任务 10 Step 4 加入这一行；还没有就现在加在 `scan_dir "app-mp" …` 之后，后端任务 10 届时跳过该步）：

```bash
cd /Users/jax/Projects/zhixue-cloud
grep -q 'scan_dir "student-web"' scripts/local/contract-check.sh || \
  sed -i '' '/^scan_dir "app-mp"/a\
scan_dir "student-web" "$ROOT_DIR/zhixue-student-web/src"
' scripts/local/contract-check.sh
grep -n 'scan_dir "' scripts/local/contract-check.sh
chmod +x scripts/local/student-web-check.sh
bash scripts/local/student-web-check.sh login
bash scripts/local/contract-check.sh | tail -3
```

Expected: `scan_dir` 有 admin-web、app-mp、student-web 三行；登录分段 6 项 `[ok]`，最后 `[ok] 学生端网页版界面验收全部通过`；契约检查 `[ok] 全部前端 API 路径均被网关路由覆盖。`

- [ ] **Step 9: README**

`README.md` 做四处修改：

1. 「### 前端 (Frontend)」下，`- **管理后台**：…` 之后加一行：

```markdown
- **学生端网页版**：Vue 3, Vite, Element Plus, Pinia（AI 课程助教对话 + 视频课推荐）
```

2. 模块结构中 `├── zhixue-admin-web       # 后端管理前端 (Vue3)` 之后加一行：

```text
├── zhixue-student-web     # 学生端网页版 (Vue3，3001 端口)
```

3. 「5. 启动前端」代码块中 `npm run dev` 之后追加：

```bash

    # 学生端网页版（http://localhost:3001，演示账号 student / student123）
    cd zhixue-student-web
    npm install
    npm run dev
```

4. 「### 本地回归」代码块末尾追加一行：

```bash
bash scripts/local/student-web-check.sh   # 学生端网页版界面验收（真实浏览器，需 playwright-cli）
```

- [ ] **Step 10: 提交**

```bash
cd /Users/jax/Projects/zhixue-cloud
git add zhixue-student-web scripts/local/lib/ui.sh scripts/local/student-web-check.sh scripts/local/ui scripts/local/contract-check.sh README.md
git status --short | grep -E "node_modules|dist/" && echo "!! 不应提交构建产物" || echo ok
git commit -m "feat(student-web): 学生端网页版工程骨架与登录；界面验收框架"
```

---

### Task 2: 课程详情：试看、购买与播放

**Files:**
- Create: `zhixue-student-web/src/utils/course.js`
- Create: `zhixue-student-web/src/api/course.js`
- Create: `zhixue-student-web/src/api/order.js`
- Create: `zhixue-student-web/src/views/course/LessonPlayer.vue`
- Create: `zhixue-student-web/src/views/course/CourseDetail.vue`
- Modify: `zhixue-student-web/src/router/index.js`（加 `course` 路由）
- Test: `zhixue-student-web/tests/unit/utils/course.test.js`
- Create: `scripts/local/ui/student-web-course.sh`

**Interfaces:**
- Consumes: `request`、`ApiError`（任务 1）；现有接口 `GET /course/detail/{id}`（`canStudy` 为能否观看全部课时）、`GET /course/{id}/chapters`（服务端已判定 `locked/preview` 并清掉无权课时的 `videoUrl`）、`POST /order/create {productId}`、`POST /pay {orderNo, payChannel:'mock'}`、`POST /pay/mock/confirm {orderNo}`
- Produces:
  - `buildLessonRows(chapters)` → `[{chapterId, chapterNo, chapterTitle, lessons:[{id, no:'2.2', title, duration, durationText, playable, preview, videoUrl}]}]`（ID 为字符串）
  - `lessonNo(chapterNo, lessonIndex)`、`formatDuration(seconds)`、`formatPrice(price)`、`courseBadge(card)` → `{text, tone, preview?}`
  - 路由 `course`：`/course/:id`，查询参数 `lesson` 存在时加载后直接打开该课时（可播放则播放，否则提示购买）
  - 「问这门课」跳到 `{ name: 'chat', query: { courseId, courseTitle } }`（任务 5 的对话页据此限定课程）

- [ ] **Step 1: 写失败测试**

`zhixue-student-web/tests/unit/utils/course.test.js`：

```js
import { describe, expect, it } from 'vitest'
import { buildLessonRows, courseBadge, formatDuration, formatPrice } from '@/utils/course'

// 与 GET /course/2007/chapters 的实际返回同构：种子课 ID 是数字，新建课程的雪花 ID 是字符串
const CHAPTERS = [
  {
    id: 3017, title: '锁与并发容器', orderNum: 2,
    lessons: [
      { id: 4024, title: 'ConcurrentHashMap 原理', duration: 1080, orderNum: 2, locked: true, preview: false, videoUrl: null },
      { id: 4023, title: 'synchronized 与 ReentrantLock', duration: 1260, orderNum: 1, locked: true, preview: false, videoUrl: null }
    ]
  },
  {
    id: '2112366524013629441', title: '线程基础', orderNum: 1,
    lessons: [
      { id: '2112366524101709826', title: '线程的创建与生命周期', duration: 900, orderNum: 1, locked: false, preview: true, videoUrl: 'https://v.example.com/1.mp4' }
    ]
  }
]

describe('buildLessonRows', () => {
  it('按章节顺序与章内顺序编号，ID 一律为字符串', () => {
    const rows = buildLessonRows(CHAPTERS)
    expect(rows.map((c) => [c.chapterNo, c.chapterTitle, c.chapterId])).toEqual([
      [1, '线程基础', '2112366524013629441'],
      [2, '锁与并发容器', '3017']
    ])
    expect(rows.flatMap((c) => c.lessons.map((l) => [l.no, l.id, l.playable]))).toEqual([
      ['1.1', '2112366524101709826', true],
      ['2.1', '4023', false],
      ['2.2', '4024', false]
    ])
    expect(rows[0].lessons[0]).toMatchObject({ preview: true, videoUrl: 'https://v.example.com/1.mp4', durationText: '15 分钟' })
  })

  it('没有章节或章节没有课时都不报错', () => {
    expect(buildLessonRows(null)).toEqual([])
    expect(buildLessonRows([{ id: 1, title: '空章', orderNum: 1 }])[0].lessons).toEqual([])
  })
})

describe('显示格式', () => {
  it('时长按分钟取整，不足一分钟算一分钟', () => {
    expect(formatDuration(1080)).toBe('18 分钟')
    expect(formatDuration(20)).toBe('1 分钟')
    expect(formatDuration(null)).toBe('')
  })

  it('价格去掉多余的 .00，0 或缺失为免费', () => {
    expect(formatPrice(99)).toBe('¥99')
    expect(formatPrice('59.50')).toBe('¥59.50')
    expect(formatPrice(0)).toBe('免费')
    expect(formatPrice(null)).toBe('免费')
  })

  it('卡片状态：免费课不显示「已购」；已购不再显示价格', () => {
    expect(courseBadge({ price: 0, purchased: true })).toEqual({ text: '免费', tone: 'success' })
    expect(courseBadge({ price: 99, purchased: true })).toEqual({ text: '已购', tone: 'success' })
    expect(courseBadge({ price: 99, purchased: false, previewable: true })).toEqual({ text: '¥99', tone: 'price', preview: true })
  })
})
```

- [ ] **Step 2: 运行确认失败**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm test 2>&1 | grep -E "Failed to resolve|Test Files"
```

Expected: `Failed to resolve import "@/utils/course"`；`Test Files  1 failed | 2 passed (3)`。

- [ ] **Step 3: 实现格式化**

`zhixue-student-web/src/utils/course.js`：

```js
const ORDER_LAST = Number.MAX_SAFE_INTEGER

/** 按 orderNum 排序；相同时按 ID（数字或雪花 ID 字符串）排，保证顺序稳定 */
function byOrder(a, b) {
  const diff = (a.orderNum ?? ORDER_LAST) - (b.orderNum ?? ORDER_LAST)
  return diff || String(a.id).localeCompare(String(b.id), undefined, { numeric: true })
}

/** 课时编号「章.课」，与推荐卡片里的 chapterIndex.lessonIndex 同一规则 */
export function lessonNo(chapterNo, lessonIndex) {
  return `${chapterNo}.${lessonIndex}`
}

/**
 * 把 GET /course/{id}/chapters 的结果整理成目录：章节按顺序、课时按章内顺序编号，ID 一律转成字符串。
 * playable 以服务端的 locked 为准（服务端已按「已购 / 免费 / 试看前 N 节」判定，并清掉了无权课时的视频地址）。
 */
export function buildLessonRows(chapters) {
  return [...(chapters || [])].sort(byOrder).map((chapter, ci) => ({
    chapterId: String(chapter.id),
    chapterNo: ci + 1,
    chapterTitle: chapter.title,
    lessons: [...(chapter.lessons || [])].sort(byOrder).map((lesson, li) => ({
      id: String(lesson.id),
      no: lessonNo(ci + 1, li + 1),
      title: lesson.title,
      duration: lesson.duration,
      durationText: formatDuration(lesson.duration),
      playable: !lesson.locked,
      preview: !!lesson.preview,
      videoUrl: lesson.videoUrl || null
    }))
  }))
}

export function formatDuration(seconds) {
  if (!seconds) {
    return ''
  }
  return `${Math.max(1, Math.round(seconds / 60))} 分钟`
}

export function formatPrice(price) {
  const value = Number(price)
  if (!price || !Number.isFinite(value) || value <= 0) {
    return '免费'
  }
  return `¥${value.toFixed(2).replace(/\.00$/, '')}`
}

/** 推荐卡片右上角的状态：免费 / 已购 / 价格（+ 可试看） */
export function courseBadge(card) {
  if (!(Number(card.price) > 0)) {
    return { text: '免费', tone: 'success' }
  }
  if (card.purchased) {
    return { text: '已购', tone: 'success' }
  }
  return { text: formatPrice(card.price), tone: 'price', preview: !!card.previewable }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm test 2>&1 | grep -E "Test Files|Tests"
```

Expected: `Test Files  3 passed (3)`、`Tests  33 passed (33)`。

- [ ] **Step 5: 接口、详情页、播放器与路由**

`zhixue-student-web/src/api/course.js`：

```js
import request from '@/utils/request'

/** 课程详情：canStudy 为当前学生能否观看全部课时（已购 / 免费 / 本课教师） */
export const getCourseDetail = (id) => request({ url: `/course/detail/${id}`, method: 'get' })

/** 章节与课时：服务端已按当前学生判定 locked / preview，并清掉无权课时的 videoUrl */
export const getCourseChapters = (id) => request({ url: `/course/${id}/chapters`, method: 'get' })
```

`zhixue-student-web/src/api/order.js`：

```js
import request from '@/utils/request'

/** 下单只传课程 ID：下单人取自登录态，金额由服务端核价；已有未过期的待支付订单时返回该订单 */
export const createOrder = (productId) => request({ url: '/order/create', method: 'post', data: { productId: String(productId) } })

/** 发起支付（模拟收银台）：服务端校验订单可支付，返回以服务端为准的 amount */
export const payOrder = (orderNo) => request({ url: '/pay', method: 'post', data: { orderNo, payChannel: 'mock' } })

/** 模拟收银台确认付款（本地联调，不产生真实扣款） */
export const confirmMockPay = (orderNo) => request({ url: '/pay/mock/confirm', method: 'post', data: { orderNo } })
```

`zhixue-student-web/src/views/course/LessonPlayer.vue`：

```vue
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
```

`zhixue-student-web/src/views/course/CourseDetail.vue`：

```vue
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
```

`zhixue-student-web/src/router/index.js`（整体替换）：

```js
import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppShell from '@/layout/AppShell.vue'
import ChatView from '@/views/chat/ChatView.vue'
import { useUserStore } from '@/stores/user'
import { createAuthGuard } from './guard'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/login/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: AppShell,
    children: [
      { path: '', redirect: { name: 'chat' } },
      { path: 'chat', name: 'chat', component: ChatView },
      { path: 'course/:id', name: 'course', component: () => import('@/views/course/CourseDetail.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'chat' } }
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(createAuthGuard(() => useUserStore(), (type, message) => ElMessage({ type, message })))

export default router
```

- [ ] **Step 6: 构建并运行课程分段**

`scripts/local/ui/student-web-course.sh`：

```bash
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
```

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm run build 2>&1 | grep -ciE "warn|deprecat|error"
cd /Users/jax/Projects/zhixue-cloud && bash scripts/local/student-web-check.sh login course
```

Expected: `0`；course 分段 6 项 `[ok]`，其中购买那一项之后出现 `[info] 已退款订单 …，课程 2007 恢复为未购`；最后 `[ok] 学生端网页版界面验收全部通过`。

人工看一眼 `/tmp/zhixue-student-web-check/course-1440.png`：封面加载失败时显示带课程名的色块，目录里试看课时有「试看」标记、锁定课时灰色带锁。

- [ ] **Step 7: 提交**

```bash
cd /Users/jax/Projects/zhixue-cloud
git add zhixue-student-web scripts/local/ui/student-web-course.sh
git commit -m "feat(student-web): 课程详情——试看、模拟收银台购买、课时播放、问这门课"
```

---

## 检查点 W-A（任务 1–2 之后）：现有接口覆盖的部分

- [ ] `npm test`：33 passed；`npm run build` 无告警。
- [ ] `bash scripts/local/student-web-check.sh login course` 全部 `[ok]`，结束后课程 2007 仍为未购。
- [ ] `bash scripts/local/contract-check.sh` 通过。
- [ ] 后端回归不受影响（本计划到此没有改后端）：`ZHIXUE_SKIP_MEDIA=1 bash scripts/local/smoke.sh | tail -1` 为 `[ok]`。

---

### Task 3: 外壳与「最近」

**前置：** 后端计划任务 8 已部署（`GET/PUT/DELETE /ai/conversations…` 可用）：

```bash
cd /Users/jax/Projects/zhixue-cloud && cat > /tmp/zx_conv_probe.sh <<'SH'
source scripts/local/common.sh && load_env
GW="http://127.0.0.1:${ZHIXUE_GATEWAY_PORT:-19001}"
source scripts/local/lib/ui.sh
curl -s "$GW/ai/conversations?pageNum=1&pageSize=1" -H "Authorization: Bearer $(student_token)"; echo
SH
bash /tmp/zx_conv_probe.sh; rm -f /tmp/zx_conv_probe.sh
```

Expected: `{"code":200,…"data":{"records":[…],"total":…}}`。返回 404 说明后端任务 8 还没部署，停下。

**Files:**
- Create: `zhixue-student-web/src/api/conversations.js`
- Create: `zhixue-student-web/src/stores/conversations.js`
- Create: `zhixue-student-web/src/layout/RecentList.vue`
- Modify: `zhixue-student-web/src/layout/SideNav.vue`、`zhixue-student-web/src/main.js`、`zhixue-student-web/src/router/index.js`（整体替换）
- Test: `zhixue-student-web/tests/unit/stores/conversations.test.js`
- Create: `scripts/local/ui/student-web-shell.sh`

**Interfaces:**
- Consumes: 后端任务 8：`GET /ai/conversations?pageNum&pageSize` → `PageResult{records:[{id, title, courseId, lastMessageTime}], total}`（pageSize 上限 50）；`GET /ai/conversations/{id}` → 同一个 `ConversationView`（他人的会话 404）；`PUT /ai/conversations/{id} {title}`（1–64 字）；`DELETE /ai/conversations/{id}`；`GET /ai/conversations/{id}/messages`（任务 5 使用）
- Produces:
  - `listConversations(pageNum, pageSize)`、`getConversation(id)`（单个会话，取课程范围）、`getConversationMessages(id)`、`renameConversation(id, title)`、`deleteConversation(id)`
  - `useConversationsStore()`：`items[{id, title, courseId, lastMessageTime}]`（ID 为字符串）、`total`、`loading`、`error`、`hasMore`、`load()`、`loadMore()`、`ensureLoaded()`、`find(id)`、`upsert(conversation)`（放到最前）、`bump(id)`（有新消息移到最前）、`rename(id, title)`、`remove(id)`、`reset()`；`PAGE_SIZE = 50`
  - 路由 `conversation`：`/chat/:id`，与 `chat` 共用 `ChatView` 组件

- [ ] **Step 1: 写失败测试**

`zhixue-student-web/tests/unit/stores/conversations.test.js`：

```js
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/conversations', () => ({
  listConversations: vi.fn(),
  renameConversation: vi.fn().mockResolvedValue(null),
  deleteConversation: vi.fn().mockResolvedValue(null)
}))

const api = await import('@/api/conversations')
const { PAGE_SIZE, useConversationsStore } = await import('@/stores/conversations')

// 种子数据里的小 ID 是数字，雪花 ID 由后端转成字符串返回（超出 JS 安全整数）
const PAGE_1 = {
  records: [
    { id: '9000000000000000002', title: '线程池参数怎么设', courseId: 2007, lastMessageTime: '2026-10-20T21:00:00' },
    { id: 9001, title: 'HashMap 为什么线程不安全', courseId: null, lastMessageTime: '2026-10-20T20:00:00' }
  ],
  total: 3
}
const PAGE_2 = {
  records: [
    { id: 9001, title: 'HashMap 为什么线程不安全', courseId: null, lastMessageTime: '2026-10-20T20:00:00' },
    { id: 9000, title: 'String 为什么不可变', courseId: null, lastMessageTime: '2026-10-19T20:00:00' }
  ],
  total: 3
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

describe('useConversationsStore', () => {
  it('加载第一页：ID 一律转成字符串；并发调用只请求一次', async () => {
    api.listConversations.mockResolvedValue(PAGE_1)
    const store = useConversationsStore()
    await Promise.all([store.load(), store.ensureLoaded()])
    expect(api.listConversations).toHaveBeenCalledOnce()
    expect(api.listConversations).toHaveBeenCalledWith(1, PAGE_SIZE)
    expect(store.items.map((c) => [c.id, c.courseId])).toEqual([['9000000000000000002', '2007'], ['9001', null]])
    expect(store.hasMore).toBe(true)
  })

  it('加载更多：追加下一页并去掉重复', async () => {
    api.listConversations.mockResolvedValueOnce(PAGE_1).mockResolvedValueOnce(PAGE_2)
    const store = useConversationsStore()
    await store.load()
    await store.loadMore()
    expect(api.listConversations).toHaveBeenLastCalledWith(2, PAGE_SIZE)
    expect(store.items.map((c) => c.id)).toEqual(['9000000000000000002', '9001', '9000'])
    expect(store.hasMore).toBe(false)
  })

  it('新建会话放到最前；已有会话有新消息时移到最前', async () => {
    api.listConversations.mockResolvedValue(PAGE_1)
    const store = useConversationsStore()
    await store.load()
    store.upsert({ id: '9000000000000000009', title: 'volatile 和 synchronized 的区别', courseId: null })
    expect(store.items[0]).toMatchObject({ id: '9000000000000000009', title: 'volatile 和 synchronized 的区别' })
    expect(store.total).toBe(4)
    store.bump('9001')
    expect(store.items.map((c) => c.id)).toEqual(['9001', '9000000000000000009', '9000000000000000002'])
    store.bump('不存在的会话')
    expect(store.items).toHaveLength(3)
  })

  it('重命名去掉首尾空白；删除后从列表移除', async () => {
    api.listConversations.mockResolvedValue(PAGE_1)
    const store = useConversationsStore()
    await store.load()
    await store.rename('9001', '  并发笔记  ')
    expect(api.renameConversation).toHaveBeenCalledWith('9001', '并发笔记')
    expect(store.find('9001').title).toBe('并发笔记')
    await store.remove('9001')
    expect(api.deleteConversation).toHaveBeenCalledWith('9001')
    expect(store.items.map((c) => c.id)).toEqual(['9000000000000000002'])
    expect(store.total).toBe(2)
  })

  it('加载失败记录原因，可以重试', async () => {
    api.listConversations.mockRejectedValueOnce(new Error('网络连接失败，请检查网络')).mockResolvedValueOnce(PAGE_1)
    const store = useConversationsStore()
    await expect(store.load()).rejects.toThrow('网络连接失败')
    expect(store.error).toBe('网络连接失败，请检查网络')
    await store.load()
    expect(store.error).toBe('')
    expect(store.items).toHaveLength(2)
  })
})
```

- [ ] **Step 2: 运行确认失败**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm test 2>&1 | grep -E "Failed to resolve|Test Files"
```

Expected: `Failed to resolve import "@/api/conversations"`（或 `@/stores/conversations`）；`Test Files  1 failed | 3 passed (4)`。

- [ ] **Step 3: 实现接口与 store**

`zhixue-student-web/src/api/conversations.js`：

```js
import request from '@/utils/request'

export const listConversations = (pageNum = 1, pageSize = 50) =>
  request({ url: '/ai/conversations', method: 'get', params: { pageNum, pageSize } })

export const getConversation = (id) => request({ url: `/ai/conversations/${id}`, method: 'get' })

export const getConversationMessages = (id) => request({ url: `/ai/conversations/${id}/messages`, method: 'get' })

export const renameConversation = (id, title) => request({ url: `/ai/conversations/${id}`, method: 'put', data: { title } })

export const deleteConversation = (id) => request({ url: `/ai/conversations/${id}`, method: 'delete' })
```

`zhixue-student-web/src/stores/conversations.js`：

```js
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { deleteConversation, listConversations, renameConversation } from '@/api/conversations'

/** 后端单页上限为 50 */
export const PAGE_SIZE = 50

const normalize = (c) => ({
  id: String(c.id),
  title: c.title || '新对话',
  courseId: c.courseId == null ? null : String(c.courseId),
  lastMessageTime: c.lastMessageTime || null
})

/** 侧边栏「最近」：按最后消息时间倒序，ID 一律为字符串 */
export const useConversationsStore = defineStore('conversations', () => {
  const items = ref([])
  const total = ref(0)
  const page = ref(0)
  const loading = ref(false)
  const error = ref('')
  let pending = null

  const hasMore = computed(() => items.value.length < total.value)

  function fetchPage(n) {
    loading.value = true
    error.value = ''
    pending = listConversations(n, PAGE_SIZE)
      .then((data) => {
        const incoming = (data?.records || []).map(normalize)
        if (n === 1) {
          items.value = incoming
        } else {
          const known = new Set(items.value.map((c) => c.id))
          items.value = [...items.value, ...incoming.filter((c) => !known.has(c.id))]
        }
        total.value = Number(data?.total || 0)
        page.value = n
      })
      .catch((e) => {
        error.value = e?.message || '加载失败'
        throw e
      })
      .finally(() => {
        loading.value = false
        pending = null
      })
    return pending
  }

  /** 重新加载第一页 */
  const load = () => pending || fetchPage(1)
  const loadMore = () => pending || (hasMore.value ? fetchPage(page.value + 1) : Promise.resolve())
  /** 还没加载过才加载；正在加载则等它完成 */
  const ensureLoaded = () => pending || (page.value ? Promise.resolve() : fetchPage(1))
  const find = (id) => items.value.find((c) => c.id === String(id)) || null

  /** 新建会话（meta 事件）：放到最前 */
  function upsert(conversation) {
    const existing = find(conversation.id)
    const next = normalize({ ...existing, ...conversation, lastMessageTime: new Date().toISOString() })
    items.value = [next, ...items.value.filter((c) => c.id !== next.id)]
    if (!existing) {
      total.value += 1
    }
  }

  /** 已有会话有新消息：移到最前；不在列表里（超出已加载的页）则忽略 */
  function bump(id) {
    const existing = find(id)
    if (!existing) {
      return
    }
    items.value = [{ ...existing, lastMessageTime: new Date().toISOString() }, ...items.value.filter((c) => c.id !== existing.id)]
  }

  async function rename(id, title) {
    const trimmed = (title || '').trim()
    await renameConversation(String(id), trimmed)
    const item = find(id)
    if (item) {
      item.title = trimmed
    }
  }

  async function remove(id) {
    await deleteConversation(String(id))
    const before = items.value.length
    items.value = items.value.filter((c) => c.id !== String(id))
    if (items.value.length < before) {
      total.value = Math.max(0, total.value - 1)
    }
  }

  /** 退出登录时清空 */
  function reset() {
    items.value = []
    total.value = 0
    page.value = 0
    error.value = ''
    pending = null
  }

  return { items, total, loading, error, hasMore, load, loadMore, ensureLoaded, find, upsert, bump, rename, remove, reset }
})
```

- [ ] **Step 4: 运行确认通过**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm test 2>&1 | grep -E "Test Files|Tests"
```

Expected: `Test Files  4 passed (4)`、`Tests  38 passed (38)`。

- [ ] **Step 5: 「最近」列表，接入侧边栏、退出登录与路由**

`RecentList.vue` 链接到 `conversation` 路由，所以路由必须同时加上（否则列表一有数据就渲染报错）。

`zhixue-student-web/src/layout/RecentList.vue`：

```vue
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
```

`zhixue-student-web/src/layout/SideNav.vue`（整体替换）：

```vue
<template>
  <nav class="side-nav" aria-label="主导航">
    <div class="brand">
      <BrandMark :size="28" />
      <span>智学云</span>
    </div>

    <button type="button" class="new-chat" @click="newChat">
      <el-icon :size="16"><Plus /></el-icon>
      <span>新对话</span>
    </button>

    <!-- 二期上线课程库、我的课程、商城、订单、收藏后再显示入口，避免点进去是空页面 -->
    <div class="nav-scroll">
      <RecentList @navigate="emit('navigate')" />
    </div>

    <div class="user-footer">
      <el-dropdown trigger="click" placement="top-start" @command="onCommand">
        <button type="button" class="user-btn" aria-label="账号菜单">
          <span class="avatar" aria-hidden="true">{{ initial }}</span>
          <span class="user-name">{{ user.displayName }}</span>
          <el-icon class="user-more"><MoreFilled /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout" :icon="SwitchButton">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </nav>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { MoreFilled, Plus, SwitchButton } from '@element-plus/icons-vue'
import BrandMark from '@/components/BrandMark.vue'
import RecentList from './RecentList.vue'
import { useConversationsStore } from '@/stores/conversations'
import { useUserStore } from '@/stores/user'
import { CHAT_DRAFT_KEY } from '@/utils/auth'

const emit = defineEmits(['navigate'])
const router = useRouter()
const user = useUserStore()
const conversations = useConversationsStore()
const initial = computed(() => user.displayName.slice(0, 1))

function newChat() {
  emit('navigate')
  router.push({ name: 'chat' })
}

async function onCommand(command) {
  if (command !== 'logout') {
    return
  }
  await user.logout()
  conversations.reset()
  sessionStorage.removeItem(CHAT_DRAFT_KEY)
  router.replace({ name: 'login' })
}
</script>

<style lang="scss" scoped>
.side-nav {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 14px 10px 10px;
  background: var(--el-fill-color-lighter);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 2px 8px 14px;
  font-size: 16px;
  font-weight: 650;
  color: var(--el-text-color-primary);
}

.new-chat {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  height: 38px;
  padding: 0 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--zx-surface);
  color: var(--el-text-color-primary);
  font: inherit;
  font-weight: 550;
  cursor: pointer;
  transition: border-color 0.16s var(--zx-ease);

  &:hover {
    border-color: var(--el-color-primary-light-5);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.nav-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  margin: 16px -4px 8px;
  padding: 0 4px;
}

.user-footer {
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);

  :deep(.el-dropdown) {
    width: 100%;
  }
}

.user-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 8px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  cursor: pointer;

  &:hover {
    background: var(--el-fill-color);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 600;
}

.user-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-more {
  color: var(--el-text-color-secondary);
}
</style>
```

`zhixue-student-web/src/main.js`（整体替换）：

```js
import { createApp } from 'vue'
import { ElMessage } from 'element-plus'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import 'element-plus/dist/index.css'

// 主题与全局样式必须在 Element Plus 样式之后引入
import '@fontsource-variable/geist'
import '@/styles/index.scss'

import App from './App.vue'
import router from './router'
import { useConversationsStore } from './stores/conversations'
import { useUserStore } from './stores/user'
import { setSessionExpiredHandler } from './utils/session'

const app = createApp(App)
const pinia = createPinia()
app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// 任何请求发现登录失效：清掉本地登录态，带上当前地址去登录页，登录后回到原处
setSessionExpiredHandler(() => {
  useUserStore(pinia).reset()
  useConversationsStore(pinia).reset()
  const current = router.currentRoute.value
  if (current.name && current.name !== 'login') {
    router.replace({ name: 'login', query: { redirect: current.fullPath } })
  }
})

app.mount('#app')
```

`zhixue-student-web/src/router/index.js`（整体替换）：

```js
import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import AppShell from '@/layout/AppShell.vue'
import ChatView from '@/views/chat/ChatView.vue'
import { useUserStore } from '@/stores/user'
import { createAuthGuard } from './guard'

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/login/LoginView.vue'), meta: { public: true } },
  {
    path: '/',
    component: AppShell,
    children: [
      { path: '', redirect: { name: 'chat' } },
      { path: 'chat', name: 'chat', component: ChatView },
      // 两条路由用同一个组件实例：新会话收到 meta 后把地址换成 /chat/:id，不能因此重建页面打断流式输出
      { path: 'chat/:id', name: 'conversation', component: ChatView },
      { path: 'course/:id', name: 'course', component: () => import('@/views/course/CourseDetail.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: { name: 'chat' } }
]

const router = createRouter({ history: createWebHistory(), routes })
router.beforeEach(createAuthGuard(() => useUserStore(), (type, message) => ElMessage({ type, message })))

export default router
```

- [ ] **Step 6: 构建并运行外壳分段**

`scripts/local/ui/student-web-shell.sh`：

```bash
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
```

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm run build 2>&1 | grep -ciE "warn|deprecat|error"
cd /Users/jax/Projects/zhixue-cloud && bash scripts/local/student-web-check.sh login course shell
bash scripts/local/contract-check.sh | tail -3
```

Expected: `0`；三个分段全部 `[ok]`（shell 分段 3 项，「最近」显示「加载成功」——没有会话时显示「还没有对话」）；契约检查通过（新增的 `/ai/conversations` 路径被 `/ai` 路由覆盖）。

人工核对 `/tmp/zhixue-student-web-check/shell-1440.png`、`shell-390-drawer.png`、`shell-390.png`。

- [ ] **Step 7: 提交**

```bash
cd /Users/jax/Projects/zhixue-cloud
git add zhixue-student-web scripts/local/ui/student-web-shell.sh
git commit -m "feat(student-web): 侧边栏「最近」——加载更多、重命名、删除；窄屏抽屉"
```

---

### Task 4: 流式对话核心逻辑（SSE 解析、流式客户端、会话状态机）

本任务没有界面，也不依赖后端部署：以后端任务 9 的事件格式为契约，流式客户端用本地 `http` 服务测试真实的 `fetch` + `ReadableStream` 读取路径（含中止、断线、连不上）。

**Files:**
- Create: `zhixue-student-web/src/api/sse.js`
- Create: `zhixue-student-web/src/api/chat.js`
- Create: `zhixue-student-web/src/views/chat/chatSession.js`
- Test: `zhixue-student-web/tests/unit/api/sse.test.js`
- Test: `zhixue-student-web/tests/unit/api/chat.test.js`
- Test: `zhixue-student-web/tests/unit/views/chatSession.test.js`

**Interfaces:**
- Consumes: 后端任务 9：`POST /ai/conversations/messages`，请求体 `{conversationId?, question, courseId?}`（ID 为字符串）；流前错误为 JSON（业务错误 HTTP 200 + `body.code` 400/404/409/500，网关 401）；否则 `text/event-stream`：`meta {conversationId, messageId, title}` → `delta {text}`* → `done {messageId, sources:[{title}], notice, recommendations:[RecommendationCard]}` 或 `error {code, msg}`
- Produces:
  - `readSse(stream)`：异步产出 `{event, data}`；`parseBlock(block)`
  - `askStream(body, { onMeta, onDelta, signal, token, baseUrl })` → 永远 resolve：`{kind:'done', data}`、`{kind:'error', code, msg}`、`{kind:'rejected', code, msg}`、`{kind:'aborted'}`、`{kind:'interrupted'}`
  - `createChatSession(deps)` → `{ state, send(text?), retry(question), stop(), open(conversationId, scope?), reset(), setScope(scope), clearScope() }`；`deps` 为 `askStream, loadMessages, onConversationCreated, onActivity, onUnauthorized, onConversationGone, notify(type, message)`
  - `state`：`conversationId, courseScope{id,title}, scopeLocked, messages, draft, inputError, generating, loading, cooldown`；消息 `{key, role:'user', content, unanswered}` 或 `{key, role:'assistant', id, content, status:'streaming'|'done'|'stopped'|'failed'|'interrupted', sources, notice, recommendations, error{code,msg}, question}`
  - `toMessages(list)`、`MAX_QUESTION = 500`、`COOLDOWN_MS = 3000`

- [ ] **Step 1: 写失败测试**

`zhixue-student-web/tests/unit/api/sse.test.js`：

```js
import { describe, expect, it } from 'vitest'
import { readSse } from '@/api/sse'

const encoder = new TextEncoder()

function streamOf(chunks) {
  return new ReadableStream({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(typeof chunk === 'string' ? encoder.encode(chunk) : chunk))
      controller.close()
    }
  })
}

async function collect(stream) {
  const events = []
  for await (const event of readSse(stream)) {
    events.push(event)
  }
  return events
}

describe('readSse', () => {
  it('Spring 的写法（冒号后无空格），事件被拆在多个网络块里', async () => {
    const events = await collect(streamOf([
      'event:me',
      'ta\ndata:{"conversationId":"21123665239507148',
      '82"}\n',
      '\nevent:delta\ndata:{"text":"Hash"}\n\n'
    ]))
    expect(events).toEqual([
      { event: 'meta', data: '{"conversationId":"2112366523950714882"}' },
      { event: 'delta', data: '{"text":"Hash"}' }
    ])
  })

  it('标准写法、CRLF 换行、注释行与多行 data', async () => {
    const events = await collect(streamOf(['event: delta\r\n', ': keepalive\r\n', 'data: 第一行\r\ndata: 第二行\r\n\r\n']))
    expect(events).toEqual([{ event: 'delta', data: '第一行\n第二行' }])
  })

  it('CRLF 的空行被拆在两个块之间', async () => {
    const events = await collect(streamOf(['event:done\r\ndata:{}\r\n\r', '\nevent:x\r\ndata:1\r\n\r\n']))
    expect(events).toEqual([{ event: 'done', data: '{}' }, { event: 'x', data: '1' }])
  })

  it('多字节字符被拆在两个块里', async () => {
    const bytes = encoder.encode('event:delta\ndata:{"text":"线程安全"}\n\n')
    const cut = bytes.indexOf(0xe7) + 1 // 切在「线」字的第一个字节之后
    const events = await collect(streamOf([bytes.slice(0, cut), bytes.slice(cut)]))
    expect(events).toEqual([{ event: 'delta', data: '{"text":"线程安全"}' }])
  })

  it('没有 event 行的块按 message 处理；没有 data 的块被忽略', async () => {
    const events = await collect(streamOf(['event:ping\n\n', 'data:hello\n\n']))
    expect(events).toEqual([{ event: 'message', data: 'hello' }])
  })

  it('流结束时没有空行收尾的最后一个事件也会产出', async () => {
    expect(await collect(streamOf(['event:done\ndata:{}']))).toEqual([{ event: 'done', data: '{}' }])
  })
})
```

`zhixue-student-web/tests/unit/api/chat.test.js`：

```js
import http from 'node:http'
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest'
import { askStream } from '@/api/chat'

const CONV = '2112366523950714882'
const MSG = '2112366524101709826'
const CARD = {
  courseId: '2007', title: 'Java 并发编程实战', coverUrl: '', price: 99, purchased: false, previewable: true,
  lessons: [{ lessonId: '4024', chapterId: '3017', title: 'ConcurrentHashMap 原理', duration: 1080, chapterIndex: 2, lessonIndex: 2, playable: false }]
}

/** 真实 HTTP 服务：覆盖 fetch + ReadableStream 的真实读取路径，而不是只测解析函数 */
let server
let baseUrl
let handler
let lastRequest

beforeAll(async () => {
  server = http.createServer((req, res) => {
    let raw = ''
    req.on('data', (chunk) => { raw += chunk })
    req.on('end', () => {
      lastRequest = { method: req.method, url: req.url, auth: req.headers.authorization, accept: req.headers.accept, body: raw ? JSON.parse(raw) : null }
      handler(req, res)
    })
  })
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
  baseUrl = `http://127.0.0.1:${server.address().port}`
})

afterAll(() => new Promise((resolve) => {
  // fetch 会复用长连接，不主动断开的话 close 要等到连接空闲超时
  server.closeAllConnections()
  server.close(resolve)
}))

/** 以 Spring 的格式（冒号后无空格）写一个事件 */
function sse(res) {
  res.writeHead(200, { 'Content-Type': 'text/event-stream;charset=UTF-8', 'Cache-Control': 'no-cache' })
  return (event, data) => res.write(`event:${event}\ndata:${JSON.stringify(data)}\n\n`)
}

function json(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' })
  res.end(JSON.stringify(body))
}

const ask = (body, options = {}) => askStream(body, { baseUrl, token: 't-1', ...options })

describe('askStream', () => {
  it('依次回调 meta 与 delta，以 done 的内容结束；请求带令牌与正确的 Accept', async () => {
    handler = (req, res) => {
      const send = sse(res)
      send('meta', { conversationId: CONV, messageId: MSG, title: 'HashMap 为什么线程不安全' })
      send('delta', { text: 'Hash' })
      send('delta', { text: 'Map' })
      send('done', { messageId: MSG, sources: [{ title: 'Java 集合与泛型 · HashMap 深入' }], notice: null, recommendations: [CARD] })
      res.end()
    }
    const onMeta = vi.fn()
    const deltas = []

    const result = await ask({ question: 'HashMap 为什么线程不安全', courseId: '2007' }, { onMeta, onDelta: (t) => deltas.push(t) })

    expect(lastRequest).toEqual({
      method: 'POST', url: '/ai/conversations/messages', auth: 'Bearer t-1', accept: 'text/event-stream, application/json',
      body: { question: 'HashMap 为什么线程不安全', courseId: '2007' }
    })
    expect(onMeta).toHaveBeenCalledWith({ conversationId: CONV, messageId: MSG, title: 'HashMap 为什么线程不安全' })
    expect(deltas).toEqual(['Hash', 'Map'])
    expect(result).toEqual({ kind: 'done', data: { messageId: MSG, sources: [{ title: 'Java 集合与泛型 · HashMap 深入' }], notice: null, recommendations: [CARD] } })
  })

  it('流开始前的业务错误是 HTTP 200 + JSON，以 body.code 为准', async () => {
    handler = (req, res) => json(res, 200, { code: 409, msg: '上一个回答还在生成' })
    expect(await ask({ question: 'x' })).toEqual({ kind: 'rejected', code: 409, msg: '上一个回答还在生成' })
  })

  it('网关鉴权失败是 HTTP 401', async () => {
    handler = (req, res) => json(res, 401, { code: 401, msg: '令牌无效或已过期' })
    expect(await ask({ question: 'x' })).toEqual({ kind: 'rejected', code: 401, msg: '令牌无效或已过期' })
  })

  it('error 事件结束：返回错误码与说明，此前的文字照常回调', async () => {
    handler = (req, res) => {
      const send = sse(res)
      send('meta', { conversationId: CONV, messageId: MSG, title: 't' })
      send('delta', { text: '半句' })
      send('error', { code: 504, msg: '生成超时，已中断' })
      res.end()
    }
    const deltas = []
    expect(await ask({ question: 'x' }, { onDelta: (t) => deltas.push(t) })).toEqual({ kind: 'error', code: 504, msg: '生成超时，已中断' })
    expect(deltas).toEqual(['半句'])
  })

  it.each([
    ['服务端正常关闭但没有 done/error', (res) => res.end()],
    ['连接被重置', (res) => res.destroy()]
  ])('%s：视为连接中断', async (_, close) => {
    handler = (req, res) => {
      sse(res)('delta', { text: '半句' })
      setTimeout(() => close(res), 20)
    }
    expect(await ask({ question: 'x' })).toEqual({ kind: 'interrupted' })
  })

  it('调用方中止：返回 aborted，服务端感知到连接关闭', async () => {
    let serverSawClose = false
    handler = (req, res) => {
      res.on('close', () => { serverSawClose = true })
      sse(res)('meta', { conversationId: CONV, messageId: MSG, title: 't' })
    }
    const controller = new AbortController()

    const result = await ask({ question: 'x' }, { signal: controller.signal, onMeta: () => controller.abort() })

    expect(result).toEqual({ kind: 'aborted' })
    await vi.waitFor(() => expect(serverSawClose).toBe(true))
  })

  it('连不上服务：视为连接中断', async () => {
    expect(await askStream({ question: 'x' }, { baseUrl: 'http://127.0.0.1:1', token: 't' })).toEqual({ kind: 'interrupted' })
  })
})
```

`zhixue-student-web/tests/unit/views/chatSession.test.js`：

```js
import { afterEach, describe, expect, it, vi } from 'vitest'
import { COOLDOWN_MS, MAX_QUESTION, SERVER_RELEASE_MS, createChatSession } from '@/views/chat/chatSession'

const CONV = '2112366523950714882'
const MSG = '2112366524101709826'
const CARD = {
  courseId: '2007', title: 'Java 并发编程实战', coverUrl: '', price: 99, purchased: false, previewable: true,
  lessons: [{ lessonId: '4024', chapterId: '3017', title: 'ConcurrentHashMap 原理', duration: 1080, chapterIndex: 2, lessonIndex: 2, playable: false }]
}

/** 可控的假 askStream：由测试决定何时回调 meta/delta、以什么结果结束；中止时与真实客户端一样返回 aborted */
function fakeAsk() {
  const calls = []
  const ask = vi.fn((body, options) => new Promise((resolve) => {
    options.signal?.addEventListener('abort', () => resolve({ kind: 'aborted' }))
    calls.push({ body, ...options, resolve })
  }))
  return { ask, calls, last: () => calls[calls.length - 1] }
}

function setup(overrides = {}) {
  const fake = fakeAsk()
  const deps = {
    askStream: fake.ask,
    loadMessages: vi.fn().mockResolvedValue([]),
    onConversationCreated: vi.fn(),
    onActivity: vi.fn(),
    onUnauthorized: vi.fn(),
    onConversationGone: vi.fn(),
    notify: vi.fn(),
    ...overrides
  }
  const session = createChatSession(deps)
  return { session, state: session.state, deps, fake }
}

afterEach(() => {
  vi.useRealTimers()
})

describe('新会话与追问', () => {
  it('首问带课程范围；meta 建立会话并锁定范围；delta 逐段追加；done 带出资料与推荐', async () => {
    const { session, state, deps, fake } = setup()
    session.setScope({ id: 2007, title: 'Java 并发编程实战' })
    state.draft = '  HashMap 为什么线程不安全  '

    const sending = session.send()
    expect(state.generating).toBe(true)
    expect(state.draft).toBe('')
    expect(fake.last().body).toEqual({ question: 'HashMap 为什么线程不安全', courseId: '2007' })

    fake.last().onMeta({ conversationId: CONV, messageId: MSG, title: 'HashMap 为什么线程不安全' })
    fake.last().onDelta('Hash')
    fake.last().onDelta('Map')
    expect(state.messages[1].content).toBe('HashMap')
    expect(state.conversationId).toBe(CONV)
    expect(state.scopeLocked).toBe(true)
    expect(deps.onConversationCreated).toHaveBeenCalledWith({ conversationId: CONV, messageId: MSG, title: 'HashMap 为什么线程不安全' })

    fake.last().resolve({ kind: 'done', data: { messageId: MSG, sources: [{ title: 'Java 集合与泛型 · HashMap 深入' }], notice: null, recommendations: [CARD] } })
    await sending
    expect(state.generating).toBe(false)
    expect(state.messages.map((m) => [m.role, m.content])).toEqual([['user', 'HashMap 为什么线程不安全'], ['assistant', 'HashMap']])
    expect(state.messages[1]).toMatchObject({ id: MSG, status: 'done', sources: [{ title: 'Java 集合与泛型 · HashMap 深入' }], recommendations: [CARD] })
  })

  it('追问带上会话 ID、不再带课程范围，并通知「最近」把它移到最前', async () => {
    const { session, state, deps, fake } = setup()
    session.setScope({ id: '2007', title: '' })
    const first = session.send('HashMap 为什么线程不安全')
    fake.last().onMeta({ conversationId: CONV, messageId: 'm1', title: 't' })
    fake.last().resolve({ kind: 'done', data: {} })
    await first

    const second = session.send('那 ConcurrentHashMap 呢')
    expect(fake.last().body).toEqual({ question: '那 ConcurrentHashMap 呢', conversationId: CONV })
    fake.last().onMeta({ conversationId: CONV, messageId: 'm2', title: 't' })
    fake.last().resolve({ kind: 'done', data: {} })
    await second

    expect(deps.onActivity).toHaveBeenCalledWith(CONV)
    expect(deps.onConversationCreated).toHaveBeenCalledOnce()
    expect(state.messages).toHaveLength(4)
  })

  it('生成中再次发送被忽略', () => {
    const { session, fake } = setup()
    session.send('第一个问题')
    session.send('第二个问题')
    expect(fake.ask).toHaveBeenCalledOnce()
  })

  it('会话建立后，限定课程的范围不能再清除', async () => {
    const { session, state, fake } = setup()
    session.setScope({ id: '2007', title: 'Java 并发编程实战' })
    const sending = session.send('线程池参数怎么设')
    fake.last().onMeta({ conversationId: CONV, messageId: MSG, title: 't' })
    session.clearScope()
    session.setScope(null)
    expect(state.courseScope).toEqual({ id: '2007', title: 'Java 并发编程实战' })
    fake.last().resolve({ kind: 'done', data: {} })
    await sending
  })
})

describe('输入校验（与后端一致：去掉首尾空白后 1–500 字）', () => {
  it.each([
    ['   ', '请输入问题'],
    ['问'.repeat(MAX_QUESTION + 1), `问题不能超过 ${MAX_QUESTION} 字`]
  ])('第 %# 种不合法输入', (text, error) => {
    const { session, state, fake } = setup()
    state.draft = text
    session.send()
    expect(state.inputError).toBe(error)
    expect(fake.ask).not.toHaveBeenCalled()
    expect(state.messages).toEqual([])
  })

  it('正好 500 字可以发送', () => {
    const { session, fake } = setup()
    session.send('问'.repeat(MAX_QUESTION))
    expect(fake.ask).toHaveBeenCalledOnce()
  })
})

describe('流开始后的结束方式', () => {
  it('点停止：保留已生成的文字，标记已停止，不出推荐', async () => {
    const { session, state, fake } = setup()
    const sending = session.send('讲讲线程池')
    fake.last().onDelta('线程池有 7 个参数')
    session.stop()
    await sending
    expect(fake.last().signal.aborted).toBe(true)
    expect(state.messages[1]).toMatchObject({ status: 'stopped', content: '线程池有 7 个参数', recommendations: [] })
    expect(state.generating).toBe(false)
  })

  it('error 事件：保留已生成的文字并给出原因', async () => {
    const { session, state, fake } = setup()
    const sending = session.send('讲讲线程池')
    fake.last().onDelta('半句')
    fake.last().resolve({ kind: 'error', code: 504, msg: '生成超时，已中断' })
    await sending
    expect(state.messages[1]).toMatchObject({ status: 'failed', content: '半句', error: { code: 504, msg: '生成超时，已中断' } })
  })

  it('连接中断', async () => {
    const { session, state, fake } = setup()
    const sending = session.send('讲讲线程池')
    fake.last().resolve({ kind: 'interrupted' })
    await sending
    expect(state.messages[1]).toMatchObject({ status: 'interrupted', error: { code: 0, msg: '连接中断' } })
  })
})

describe('流开始前被拒绝：撤回这次提问，问题回到输入框', () => {
  async function rejectWith(result, prepare) {
    const ctx = setup()
    await prepare?.(ctx)
    ctx.state.draft = 'HashMap 为什么线程不安全'
    const sending = ctx.session.send()
    ctx.fake.last().resolve({ kind: 'rejected', ...result })
    await sending
    return ctx
  }

  it('409：提示，并短暂禁止发送', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
    const { session, state, deps, fake } = await rejectWith({ code: 409, msg: '上一个回答还在生成' })
    expect(state.messages).toEqual([])
    expect(state.draft).toBe('HashMap 为什么线程不安全')
    expect(deps.notify).toHaveBeenCalledWith('warning', '上一个回答还在生成')
    expect(state.cooldown).toBe(true)
    session.send()
    expect(fake.ask).toHaveBeenCalledOnce()
    vi.advanceTimersByTime(COOLDOWN_MS)
    expect(state.cooldown).toBe(false)
  })

  it('401：交给登录失效处理，问题留在输入框（登录回来还在）', async () => {
    const { state, deps } = await rejectWith({ code: 401, msg: '令牌无效' })
    expect(deps.onUnauthorized).toHaveBeenCalledOnce()
    expect(state.draft).toBe('HashMap 为什么线程不安全')
  })

  it('404：会话已不存在，回到新对话', async () => {
    const { state, deps } = await rejectWith({ code: 404, msg: '会话不存在或已删除' }, ({ session }) => session.open(CONV))
    expect(deps.notify).toHaveBeenCalledWith('warning', '会话不存在或已删除')
    expect(deps.onConversationGone).toHaveBeenCalledOnce()
    expect(state.conversationId).toBe(null)
    expect(state.draft).toBe('HashMap 为什么线程不安全')
  })

  it('400：在输入框下方提示', async () => {
    const { state } = await rejectWith({ code: 400, msg: '问题不能超过 500 字' })
    expect(state.inputError).toBe('问题不能超过 500 字')
  })

  it('500：提示发送失败', async () => {
    const { deps } = await rejectWith({ code: 500, msg: '发送失败，请重试' })
    expect(deps.notify).toHaveBeenCalledWith('error', '发送失败，请重试')
  })

  it('重试与推荐问题不动输入框里正在写的草稿', async () => {
    const { session, state, fake } = setup()
    state.draft = '还没写完的草稿'
    const sending = session.retry('HashMap 为什么线程不安全')
    expect(fake.last().body.question).toBe('HashMap 为什么线程不安全')
    fake.last().resolve({ kind: 'rejected', code: 409, msg: '上一个回答还在生成' })
    await sending
    expect(state.draft).toBe('还没写完的草稿')
  })
})

describe('停止后的 409：自动重发一次', () => {
  /** 先发一题并点停止，让状态机记下「刚停止的提问」 */
  async function stopThenReject(ctx, opts = {}) {
    const sending = ctx.session.send('HashMap 为什么线程不安全')
    ctx.fake.last().onDelta('半句')
    ctx.session.stop()
    ctx.fake.last().resolve({ kind: 'aborted' })
    await sending
    vi.advanceTimersByTime(opts.elapse ?? 0)
    const retry = ctx.session.send('HashMap 为什么线程不安全')
    ctx.fake.last().resolve({ kind: 'rejected', code: 409, msg: '上一个回答还在生成' })
    await retry
    return ctx
  }

  it('冷却结束后自动重发，成功后正常显示', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const ctx = setup()
    await stopThenReject(ctx)
    expect(ctx.fake.ask).toHaveBeenCalledTimes(2)
    expect(ctx.state.cooldown).toBe(true)
    expect(ctx.state.draft).toBe('')

    vi.advanceTimersByTime(COOLDOWN_MS)
    expect(ctx.fake.ask).toHaveBeenCalledTimes(3)
    expect(ctx.fake.last().body.question).toBe('HashMap 为什么线程不安全')
    ctx.fake.last().resolve({ kind: 'done', data: {} })
    await Promise.resolve()
    await Promise.resolve()
    // 第一条是已停止的回答，自动重发产生第 3、4 条
    expect(ctx.state.messages.map((m) => m.role === 'assistant' && m.status)).toEqual([false, 'stopped', false, 'done'])
  })

  it('只自动重发一次：重发又被 409 就只冷却，不再无限重试', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const ctx = setup()
    await stopThenReject(ctx)
    vi.advanceTimersByTime(COOLDOWN_MS)
    expect(ctx.fake.ask).toHaveBeenCalledTimes(3)
    ctx.fake.last().resolve({ kind: 'rejected', code: 409, msg: '上一个回答还在生成' })
    await Promise.resolve()
    await Promise.resolve()
    vi.advanceTimersByTime(COOLDOWN_MS * 3)
    expect(ctx.fake.ask).toHaveBeenCalledTimes(3)
    expect(ctx.state.cooldown).toBe(false)
  })

  it('不是刚停止（已超过 30 秒）就只冷却', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] })
    const ctx = setup()
    await stopThenReject(ctx, { elapse: SERVER_RELEASE_MS + 1 })
    vi.advanceTimersByTime(COOLDOWN_MS)
    expect(ctx.fake.ask).toHaveBeenCalledTimes(2)
  })

  it('没点停止的 409（例如另一个标签页在生成）不自动重发', async () => {
    const { session, state, fake } = setup()
    const sending = session.send('HashMap 为什么线程不安全')
    fake.last().resolve({ kind: 'rejected', code: 409, msg: '上一个回答还在生成' })
    await sending
    expect(state.cooldown).toBe(true)
    expect(fake.ask).toHaveBeenCalledOnce()
  })
})

describe('打开历史会话', () => {
  const HISTORY = [
    { id: '1', role: 'user', content: 'Q1', status: 1 },
    { id: '2', role: 'assistant', content: 'A1', status: 1, sources: [{ title: 'S' }], recommendations: [CARD] },
    { id: '3', role: 'user', content: 'Q2', status: 1 },
    { id: '4', role: 'assistant', content: '半截', status: 2, sources: [], recommendations: [] },
    { id: '5', role: 'user', content: 'Q3', status: 1 },
    { id: '6', role: 'assistant', content: '', status: 3, sources: [], recommendations: [] },
    { id: '7', role: 'user', content: 'Q4', status: 1 }
  ]

  it('按服务端状态还原消息；没有回答的问题标为未完成；范围锁定', async () => {
    const { session, state, deps } = setup({ loadMessages: vi.fn().mockResolvedValue(HISTORY) })
    await session.open(CONV, { id: 2007, title: '' })
    expect(deps.loadMessages).toHaveBeenCalledWith(CONV)
    expect(state.messages.map((m) => (m.role === 'user' ? (m.unanswered ? 'unanswered' : 'user') : m.status))).toEqual([
      'user', 'done', 'user', 'stopped', 'user', 'failed', 'unanswered'
    ])
    expect(state.messages[1].recommendations).toEqual([CARD])
    expect(state.messages[5]).toMatchObject({ question: 'Q3', error: { code: 503 } })
    expect(state).toMatchObject({ conversationId: CONV, courseScope: { id: '2007', title: '' }, scopeLocked: true, loading: false })
  })

  it('切换会话会中止正在生成的回答，旧回答的结果不影响新会话', async () => {
    const { session, state, fake } = setup()
    const sending = session.send('第一个问题')
    await session.open(CONV)
    await sending
    expect(fake.last().signal.aborted).toBe(true)
    expect(state.generating).toBe(false)
    expect(state.messages).toEqual([])
  })

  it('会话不存在（404）：提示并回到新对话', async () => {
    const error = Object.assign(new Error('会话不存在或已删除'), { code: 404 })
    const { session, state, deps } = setup({ loadMessages: vi.fn().mockRejectedValue(error) })
    await session.open(CONV)
    expect(state.conversationId).toBe(null)
    expect(deps.onConversationGone).toHaveBeenCalledOnce()
  })
})
```

- [ ] **Step 2: 运行确认失败**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm test 2>&1 | grep -E "Failed to resolve|Test Files"
```

Expected: 三个 `Failed to resolve import`（`@/api/sse`、`@/api/chat`、`@/views/chat/chatSession`）；`Test Files  3 failed | 4 passed (7)`。

- [ ] **Step 3: 实现**

`zhixue-student-web/src/api/sse.js`：

```js
/**
 * 把 SSE 字节流解析成事件。
 *
 * 兼容 Spring 输出的 `event:x` / `data:y`（冒号后没有空格）与标准写法；兼容 \r\n；
 * 事件或多字节字符被拆在多个网络块里；多行 data（按 \n 拼接）；注释行（以冒号开头）。
 * 流结束时没有空行收尾的最后一个事件也会产出。
 *
 * @param {ReadableStream<Uint8Array>} stream
 * @returns {AsyncGenerator<{ event: string, data: string }>}
 */
export async function* readSse(stream) {
  const reader = stream.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      let boundary = findBoundary(buffer)
      while (boundary) {
        const event = parseBlock(buffer.slice(0, boundary.index))
        buffer = buffer.slice(boundary.index + boundary.length)
        if (event) {
          yield event
        }
        boundary = findBoundary(buffer)
      }
    }
    const tail = parseBlock(buffer + decoder.decode())
    if (tail) {
      yield tail
    }
  } finally {
    // 调用方提前结束（如收到 done 后 return）时释放连接
    reader.cancel().catch(() => {})
  }
}

function findBoundary(text) {
  const match = /\r\n\r\n|\n\n|\r\r/.exec(text)
  return match ? { index: match.index, length: match[0].length } : null
}

/** 解析一个事件块；没有 data 行的块（如心跳注释）返回 null */
export function parseBlock(block) {
  let event = 'message'
  const data = []
  for (const line of block.split(/\r\n|\n|\r/)) {
    if (!line || line.startsWith(':')) {
      continue
    }
    const colon = line.indexOf(':')
    const field = colon === -1 ? line : line.slice(0, colon)
    let value = colon === -1 ? '' : line.slice(colon + 1)
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }
    if (field === 'event') {
      event = value
    } else if (field === 'data') {
      data.push(value)
    }
  }
  return data.length ? { event, data: data.join('\n') } : null
}
```

`zhixue-student-web/src/api/chat.js`：

```js
import { readSse } from './sse'

const BASE = import.meta.env.VITE_APP_BASE_API || ''
// 写成 url 字面量，scripts/local/contract-check.sh 才能核对网关是否有这条路由
const ASK = { url: '/ai/conversations/messages', method: 'POST' }
const MODEL_UNAVAILABLE = 'AI 服务暂不可用，请稍后再试'

/**
 * 提问并读取流式回答（POST /ai/conversations/messages）。
 *
 * 不用浏览器自带的 EventSource：它只能发 GET、带不了 Authorization 头，而问题可能长达 500 字。
 * 永远 resolve、不 reject，用 kind 区分结束方式：
 * - done：正常结束；data 为 done 事件内容（messageId、sources、notice、recommendations）
 * - error：流开始后失败（error 事件）；code 为 503 / 504，msg 可直接展示
 * - rejected：流开始前被拒绝。业务错误是 HTTP 200 + body.code（400 / 404 / 409 / 500），
 *   以 body.code 为准；网关鉴权失败是 HTTP 401
 * - aborted：调用方通过 signal 中止（停止按钮、切换会话、离开页面）
 * - interrupted：连接失败，或流在 done / error 之前就结束了
 *
 * @param {{ conversationId?: string, question: string, courseId?: string }} body
 * @param {{ onMeta?: (meta: object) => void, onDelta?: (text: string) => void, signal?: AbortSignal,
 *           token?: string, baseUrl?: string }} options
 */
export async function askStream(body, { onMeta, onDelta, signal, token, baseUrl = BASE } = {}) {
  let response
  try {
    response = await fetch(`${baseUrl}${ASK.url}`, {
      method: ASK.method,
      headers: {
        'Content-Type': 'application/json',
        // 必须同时接受 JSON：流开始前的错误按 JSON 返回，只写 text/event-stream 会被服务端判为不可接受
        Accept: 'text/event-stream, application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(body),
      signal
    })
  } catch {
    return ended(signal)
  }

  if (!(response.headers.get('content-type') || '').includes('text/event-stream')) {
    return rejected(response)
  }

  try {
    for await (const { event, data } of readSse(response.body)) {
      const payload = parseJson(data)
      if (event === 'meta') {
        onMeta?.(payload || {})
      } else if (event === 'delta') {
        onDelta?.(payload?.text ?? '')
      } else if (event === 'done') {
        return { kind: 'done', data: payload || {} }
      } else if (event === 'error') {
        return { kind: 'error', code: payload?.code ?? 503, msg: payload?.msg || MODEL_UNAVAILABLE }
      }
    }
  } catch {
    // 读取中途出错：中止或断线，下面统一判断
  }
  return ended(signal)
}

function ended(signal) {
  return signal?.aborted ? { kind: 'aborted' } : { kind: 'interrupted' }
}

async function rejected(response) {
  const body = await response.json().catch(() => null)
  if (response.status === 401 || body?.code === 401) {
    return { kind: 'rejected', code: 401, msg: body?.msg || '登录已失效，请重新登录' }
  }
  const code = body && body.code !== 200 ? body.code : response.status >= 400 ? response.status : 500
  return { kind: 'rejected', code, msg: body?.msg || `请求失败（${response.status}）` }
}

function parseJson(text) {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}
```

`zhixue-student-web/src/views/chat/chatSession.js`：

```js
import { reactive } from 'vue'

/** 与后端一致：去掉首尾空白后最多 500 字（按 UTF-16 长度计，与 Java String.length 相同） */
export const MAX_QUESTION = 500
/** 收到 409（另一个回答还在生成）后暂停发送的时长 */
export const COOLDOWN_MS = 3000
/**
 * 停止后服务端要等它自己发现连接已断开才释放「生成中」占位（最长等待首字的时间），
 * 超过这个时长后再收到 409 就不自动重试了，交给学生自己判断。
 */
export const SERVER_RELEASE_MS = 30000

const STATUS_BY_CODE = { 1: 'done', 2: 'stopped', 3: 'failed' }
let keySeq = 0
const nextKey = (prefix) => `${prefix}-local-${++keySeq}`

function assistantMessage(fields) {
  return {
    key: nextKey('a'),
    role: 'assistant',
    id: null,
    content: '',
    status: 'streaming',
    sources: [],
    notice: null,
    recommendations: [],
    error: null,
    question: '',
    ...fields
  }
}

/**
 * 把历史消息（GET /ai/conversations/{id}/messages）转成界面消息。
 * 学生的问题后面没有 AI 回答（例如 AI 服务重启），标记为 unanswered，界面显示「此问题未完成回答」。
 */
export function toMessages(list) {
  const messages = []
  let lastQuestion = ''
  ;(list || []).forEach((m, i) => {
    if (m.role === 'user') {
      lastQuestion = m.content
      const next = list[i + 1]
      messages.push({ key: `u-${m.id}`, role: 'user', content: m.content, unanswered: !next || next.role !== 'assistant' })
      return
    }
    const status = STATUS_BY_CODE[m.status] || 'done'
    messages.push(assistantMessage({
      key: `a-${m.id}`,
      id: String(m.id),
      content: m.content || '',
      status,
      sources: m.sources || [],
      recommendations: m.recommendations || [],
      error: status === 'failed' ? { code: 503, msg: '这次回答没有完成' } : null,
      question: lastQuestion
    }))
  })
  return messages
}

/**
 * 当前会话的消息与生成状态。外部依赖全部注入，不依赖组件与路由，便于单测：
 * - askStream(body, { onMeta, onDelta, signal })：见 api/chat.js
 * - loadMessages(conversationId)：历史消息
 * - onConversationCreated(meta) / onActivity(conversationId)：通知「最近」列表
 * - onUnauthorized() / onConversationGone() / notify(type, message)
 */
export function createChatSession(deps) {
  const state = reactive({
    conversationId: null,
    courseScope: null,
    scopeLocked: false,
    messages: [],
    draft: '',
    inputError: '',
    generating: false,
    loading: false,
    cooldown: false
  })
  let controller = null
  let openTicket = 0
  /** 刚停止的提问与时间：此时收到 409 说明服务端还没放开占位，冷却后自动重发一次 */
  let stopped = null
  let cooldownTimer = null

  /** 中止进行中的回答，并让它的后续回调不再改动当前状态 */
  function detach() {
    controller?.abort()
    controller = null
    state.generating = false
    stopped = null
    endCooldown()
  }

  function endCooldown() {
    clearTimeout(cooldownTimer)
    cooldownTimer = null
    state.cooldown = false
  }

  function setScope(scope) {
    if (state.conversationId) {
      return
    }
    state.courseScope = scope ? { id: String(scope.id), title: scope.title || '' } : null
    state.scopeLocked = false
  }

  function clearScope() {
    if (!state.scopeLocked) {
      state.courseScope = null
    }
  }

  function reset() {
    detach()
    openTicket++
    Object.assign(state, { conversationId: null, courseScope: null, scopeLocked: false, messages: [], inputError: '', loading: false })
  }

  async function open(conversationId, scope = null) {
    detach()
    const ticket = ++openTicket
    Object.assign(state, {
      conversationId: String(conversationId),
      courseScope: scope ? { id: String(scope.id), title: scope.title || '' } : null,
      scopeLocked: !!scope,
      messages: [],
      inputError: '',
      loading: true
    })
    try {
      const list = await deps.loadMessages(state.conversationId)
      if (ticket === openTicket) {
        state.messages = toMessages(list)
      }
    } catch (error) {
      if (ticket !== openTicket) {
        return
      }
      if (error?.code === 404) {
        deps.notify('warning', '会话不存在或已删除')
        reset()
        deps.onConversationGone?.()
      } else {
        deps.notify('error', error?.message || '加载对话失败')
      }
    } finally {
      if (ticket === openTicket) {
        state.loading = false
      }
    }
  }

  /**
   * text 不传时发送输入框里的内容；传了（推荐问题、重试）则不动输入框。
   * auto 为 true 时是停止后的自动重试：失败的问题不再放回输入框（学生已经看见它发出去过一次）。
   */
  async function send(text, auto = false) {
    if (state.generating || state.cooldown || state.loading) {
      return
    }
    const fromDraft = text === undefined
    const question = (fromDraft ? state.draft : text || '').trim()
    state.inputError = ''
    if (!question) {
      state.inputError = '请输入问题'
      return
    }
    if (question.length > MAX_QUESTION) {
      state.inputError = `问题不能超过 ${MAX_QUESTION} 字`
      return
    }

    const isNew = !state.conversationId
    const body = { question }
    if (isNew) {
      if (state.courseScope) {
        body.courseId = state.courseScope.id
      }
    } else {
      body.conversationId = state.conversationId
    }
    state.messages.push({ key: nextKey('u'), role: 'user', content: question, unanswered: false })
    state.messages.push(assistantMessage({ question }))
    const userMessage = state.messages[state.messages.length - 2]
    const answer = state.messages[state.messages.length - 1]
    if (fromDraft) {
      state.draft = ''
    }
    state.generating = true
    const mine = (controller = new AbortController())

    const result = await deps.askStream(body, {
      signal: mine.signal,
      onMeta(meta) {
        if (controller !== mine) {
          return
        }
        answer.id = String(meta.messageId)
        if (isNew) {
          state.conversationId = String(meta.conversationId)
          state.scopeLocked = !!state.courseScope
          deps.onConversationCreated?.(meta)
        } else {
          deps.onActivity?.(state.conversationId)
        }
      },
      onDelta(piece) {
        answer.content += piece
      }
    })

    if (controller === mine) {
      controller = null
      state.generating = false
    }
    settle(result, { answer, userMessage, question, fromDraft, auto })
  }

  function settle(result, context) {
    const { answer } = context
    if (result.kind === 'done') {
      Object.assign(answer, {
        status: 'done',
        id: result.data?.messageId ? String(result.data.messageId) : answer.id,
        sources: result.data?.sources || [],
        notice: result.data?.notice || null,
        recommendations: result.data?.recommendations || []
      })
    } else if (result.kind === 'error') {
      Object.assign(answer, { status: 'failed', error: { code: result.code, msg: result.msg } })
    } else if (result.kind === 'aborted') {
      answer.status = 'stopped'
    } else if (result.kind === 'interrupted') {
      Object.assign(answer, { status: 'interrupted', error: { code: 0, msg: '连接中断' } })
    } else if (result.kind === 'rejected') {
      rejected(result, context)
    }
  }

  /** 流开始前被拒绝：服务端没有保存这次提问，撤回界面上的两条消息，问题回到输入框 */
  function rejected({ code, msg }, { answer, userMessage, question, fromDraft, auto }) {
    state.messages = state.messages.filter((m) => m !== answer && m !== userMessage)
    if (fromDraft && !auto && !state.draft) {
      state.draft = question
    }
    if (code === 400) {
      state.inputError = msg || '问题格式不正确'
    } else if (code === 401) {
      deps.onUnauthorized?.()
    } else if (code === 404) {
      deps.notify('warning', '会话不存在或已删除')
      reset()
      deps.onConversationGone?.()
    } else if (code === 409) {
      conflict(question, msg)
    } else {
      deps.notify('error', '发送失败，请重试')
    }
  }

  /**
   * 409：另一个回答还在生成。刚点过停止时必然是服务端还没发现连接断了（它要等下一次写出才发现），
   * 冷却后自动重发一次，学生不用自己再点；其他情况只冷却，不自动重发。
   */
  function conflict(question, msg) {
    deps.notify('warning', msg || '上一个回答还在生成')
    const auto = stopped && stopped.question === question && Date.now() - stopped.at < SERVER_RELEASE_MS
    stopped = null
    state.cooldown = true
    clearTimeout(cooldownTimer)
    cooldownTimer = setTimeout(() => {
      cooldownTimer = null
      state.cooldown = false
      if (auto) {
        send(question, true)
      }
    }, COOLDOWN_MS)
  }

  function stop() {
    const last = state.messages[state.messages.length - 1]
    if (last?.role === 'assistant' && last.question) {
      stopped = { question: last.question, at: Date.now() }
    }
    controller?.abort()
  }

  return { state, send, retry: (question) => send(question), stop, open, reset, setScope, clearScope }
}
```

- [ ] **Step 4: 运行确认通过**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm test 2>&1 | grep -E "Test Files|Tests|Duration"
```

Expected: `Test Files  7 passed (7)`、`Tests  75 passed (75)`，总耗时在 1 秒左右（若 `chat.test.js` 慢到 3 秒以上，说明测试服务器没有在 `afterAll` 里断开长连接）。

- [ ] **Step 5: 构建并提交**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm run build 2>&1 | grep -ciE "warn|deprecat|error"
cd /Users/jax/Projects/zhixue-cloud
git add zhixue-student-web
git commit -m "feat(student-web): 流式对话核心——SSE 解析、流式客户端五种结束方式、会话状态机"
```

Expected: `0`。

---

### Task 5: 对话页

**前置：** 后端计划检查点 C 已通过（流式提问接口、推荐、会话接口均已部署）。

**Files:**
- Create: `zhixue-student-web/src/utils/markdown.js`
- Create: `zhixue-student-web/src/views/chat/AnswerMarkdown.vue`
- Create: `zhixue-student-web/src/views/chat/RecommendationCards.vue`
- Create: `zhixue-student-web/src/views/chat/MessageList.vue`
- Create: `zhixue-student-web/src/views/chat/Composer.vue`
- Modify: `zhixue-student-web/src/views/chat/ChatView.vue`（整体替换任务 1 的落地页）
- Test: `zhixue-student-web/tests/unit/utils/markdown.test.js`
- Create: `scripts/local/ui/student-web-chat.sh`
- Modify: `docs/superpowers/specs/2026-10-20-student-web-ai-chat-design.md`（3.4、11 节）

**Interfaces:**
- Consumes: `createChatSession`、`askStream`（任务 4）；`useConversationsStore`、`getConversationMessages`（任务 3）；`getCourseDetail`、`courseBadge`、`formatDuration`、路由 `course` 的 `lesson` 参数（任务 2）
- Produces:
  - `renderMarkdown(text)` → 可安全用于 `v-html` 的 HTML
  - `Composer` 事件：`update:modelValue`、`send`（Enter，输入法组字时不触发）、`stop`（停止按钮或 Esc）、`clear-scope`；`defineExpose({ focus })`
  - `MessageList` 事件：`retry(question)`、`open-course(card)`、`open-lesson(card, lesson)`；回答节点带 `data-status`（验收脚本据此判断）
  - `RecommendationCards` props：`cards`（`RecommendationCard[]`）

- [ ] **Step 1: 写失败测试**

`zhixue-student-web/tests/unit/utils/markdown.test.js`：

````js
import { describe, expect, it } from 'vitest'
import { renderMarkdown } from '@/utils/markdown'

describe('renderMarkdown 的安全规则', () => {
  it('原始 HTML 一律转义，不会变成标签', () => {
    const html = renderMarkdown('<script>alert(1)</script>\n\n<img src=x onerror=alert(1)>')
    expect(html).not.toContain('<script')
    expect(html).not.toContain('<img')
    expect(html).toContain('&lt;script&gt;')
  })

  it('只允许 http/https 链接，且新窗口打开、不传来源', () => {
    const html = renderMarkdown('[官网](https://example.com) [坏链接](javascript:alert(1)) [文件](file:///etc/passwd)')
    expect(html).toContain('<a href="https://example.com" target="_blank" rel="noopener noreferrer nofollow">官网</a>')
    expect(html).not.toMatch(/href="javascript/i)
    expect(html).not.toMatch(/href="file/i)
  })

  it('裸网址自动成为安全链接', () => {
    expect(renderMarkdown('见 https://docs.oracle.com/javase/8/')).toContain('target="_blank" rel="noopener noreferrer nofollow"')
  })

  it('图片不加载，只显示为链接文字', () => {
    const html = renderMarkdown('![架构图](https://t.example.com/p.png)')
    expect(html).not.toContain('<img')
    expect(html).toContain('<a href="https://t.example.com/p.png" target="_blank" rel="noopener noreferrer nofollow">[图片] 架构图</a>')
  })

  it('非 http(s) 的图片地址不成为任何元素', () => {
    const html = renderMarkdown('![x](data:image/png;base64,AAAA)')
    expect(html).not.toContain('<img')
    expect(html).not.toContain('href="data:')
  })

  it('Java 代码块高亮，未知语言原样转义', () => {
    const java = renderMarkdown('```java\npublic class A { int a = 1; }\n```')
    expect(java).toContain('class="hljs language-java"')
    expect(java).toContain('hljs-keyword')
    const unknown = renderMarkdown('```brainfuck\n<b>x</b>\n```')
    expect(unknown).toContain('&lt;b&gt;x&lt;/b&gt;')
    expect(unknown).not.toContain('<b>')
  })

  it('单个换行保留为换行（模型常用单换行分点）', () => {
    expect(renderMarkdown('第一行\n第二行')).toContain('第一行<br>\n第二行')
  })

  it('空内容渲染为空串', () => {
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown(null)).toBe('')
  })
})
````

- [ ] **Step 2: 运行确认失败**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm test 2>&1 | grep -E "Failed to resolve|Test Files"
```

Expected: `Failed to resolve import "@/utils/markdown"`；`Test Files  1 failed | 7 passed (8)`。

- [ ] **Step 3: 实现安全渲染**

`zhixue-student-web/src/utils/markdown.js`：

```js
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import sql from 'highlight.js/lib/languages/sql'
import xml from 'highlight.js/lib/languages/xml'

hljs.registerLanguage('bash', bash)
hljs.registerLanguage('java', java)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('json', json)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('xml', xml)

const SAFE_URL = /^https?:\/\//i
const LINK_ATTRS = 'target="_blank" rel="noopener noreferrer nofollow"'

/**
 * 回答渲染器。模型输出不可信，三条规则保证结果可以直接用于 v-html：
 * 1. html: false —— 原始 HTML 一律按文本转义；
 * 2. 链接只允许 http/https，新窗口打开并带 noopener noreferrer nofollow；
 * 3. 图片不加载（防追踪像素），只渲染为链接文字。
 */
const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true,
  langPrefix: 'hljs language-',
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
      } catch {
        // 高亮失败时交给 markdown-it 转义原文
      }
    }
    return ''
  }
})

// markdown-it 对链接与图片地址都会调用 validateLink；不通过的保持为普通文本
md.validateLink = (url) => SAFE_URL.test(url.trim())

md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  tokens[idx].attrSet('target', '_blank')
  tokens[idx].attrSet('rel', 'noopener noreferrer nofollow')
  return self.renderToken(tokens, idx, options)
}

md.renderer.rules.image = (tokens, idx) => {
  const token = tokens[idx]
  const src = token.attrGet('src') || ''
  const alt = md.utils.escapeHtml(token.content || '图片')
  return `<a href="${md.utils.escapeHtml(src)}" ${LINK_ATTRS}>[图片] ${alt}</a>`
}

export function renderMarkdown(text) {
  return md.render(text || '')
}
```

- [ ] **Step 4: 运行确认通过**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm test 2>&1 | grep -E "Test Files|Tests"
```

Expected: `Test Files  8 passed (8)`、`Tests  83 passed (83)`。

- [ ] **Step 5: 对话页组件**

`zhixue-student-web/src/views/chat/AnswerMarkdown.vue`：

```vue
<template>
  <!-- renderMarkdown 禁用了原始 HTML、只放行 http(s) 链接、不加载图片，输出可以安全地用于 v-html -->
  <div class="answer-md" v-html="html" />
</template>

<script setup>
import { computed } from 'vue'
import 'highlight.js/styles/github.css'
import { renderMarkdown } from '@/utils/markdown'

const props = defineProps({
  content: { type: String, default: '' }
})

const html = computed(() => renderMarkdown(props.content))
</script>

<style lang="scss" scoped>
.answer-md {
  color: var(--el-text-color-primary);
  font-size: 15px;
  line-height: 1.75;
  word-break: break-word;

  :deep(p),
  :deep(ul),
  :deep(ol),
  :deep(pre),
  :deep(blockquote),
  :deep(table) {
    margin: 0 0 0.75em;
  }

  :deep(> :last-child) {
    margin-bottom: 0;
  }

  :deep(ul),
  :deep(ol) {
    padding-left: 1.4em;
  }

  :deep(li) {
    margin: 0.2em 0;
  }

  :deep(h1),
  :deep(h2),
  :deep(h3),
  :deep(h4) {
    margin: 1em 0 0.5em;
    font-size: 16px;
  }

  :deep(code) {
    padding: 0.1em 0.35em;
    border-radius: 4px;
    background: var(--el-fill-color);
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    font-size: 13px;
  }

  :deep(pre) {
    overflow-x: auto;
    padding: 12px 14px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 8px;
    background: var(--el-fill-color-lighter);
  }

  :deep(pre code) {
    padding: 0;
    background: none;
    line-height: 1.6;
  }

  :deep(a) {
    color: var(--el-color-primary);
    text-decoration: underline;
    text-underline-offset: 2px;
  }

  :deep(blockquote) {
    padding-left: 12px;
    border-left: 3px solid var(--el-border-color);
    color: var(--el-text-color-secondary);
  }

  :deep(table) {
    border-collapse: collapse;
  }

  :deep(th),
  :deep(td) {
    padding: 4px 10px;
    border: 1px solid var(--el-border-color-light);
  }
}
</style>
```

`zhixue-student-web/src/views/chat/RecommendationCards.vue`：

```vue
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
```

`zhixue-student-web/src/views/chat/MessageList.vue`：

```vue
<template>
  <ol class="message-list" aria-live="polite">
    <li v-for="message in messages" :key="message.key" class="row" :class="message.role">
      <template v-if="message.role === 'user'">
        <div class="bubble">{{ message.content }}</div>
        <div v-if="message.unanswered" class="unanswered">
          此问题未完成回答
          <el-button link type="primary" :disabled="busy" @click="emit('retry', message.content)">重试</el-button>
        </div>
      </template>

      <article v-else class="answer" :data-status="message.status" :aria-busy="message.status === 'streaming'">
        <div class="avatar" aria-hidden="true">AI</div>
        <div class="body">
          <p v-if="message.status === 'streaming' && !message.content" class="thinking">
            正在思考<span class="dots" aria-hidden="true" />
          </p>
          <AnswerMarkdown v-if="message.content" :content="message.content" />
          <span v-if="message.status === 'streaming' && message.content" class="cursor" aria-hidden="true" />

          <p v-if="message.status === 'stopped'" class="state stopped">已停止</p>

          <div v-if="message.status === 'failed' || message.status === 'interrupted'" class="failure" role="alert">
            <el-icon><WarningFilled /></el-icon>
            <span>{{ message.error?.msg || 'AI 服务暂不可用，请稍后再试' }}</span>
            <el-button
              v-if="message.question"
              link
              type="primary"
              :icon="RefreshRight"
              :disabled="busy"
              @click="emit('retry', message.question)"
            >
              重试
            </el-button>
          </div>

          <template v-if="message.status === 'done'">
            <p v-if="message.sources.length" class="sources">
              参考资料：{{ message.sources.map((source) => source.title).join('、') }}
            </p>
            <p v-if="message.notice" class="notice">{{ message.notice }}</p>
            <RecommendationCards
              v-if="message.recommendations.length"
              :cards="message.recommendations"
              @open-course="(card) => emit('open-course', card)"
              @open-lesson="(card, lesson) => emit('open-lesson', card, lesson)"
            />
          </template>
        </div>
      </article>
    </li>
  </ol>
</template>

<script setup>
import { RefreshRight, WarningFilled } from '@element-plus/icons-vue'
import AnswerMarkdown from './AnswerMarkdown.vue'
import RecommendationCards from './RecommendationCards.vue'

defineProps({
  /** chatSession 的 state.messages */
  messages: { type: Array, required: true },
  /** 正在生成或冷却中：重试按钮暂不可用 */
  busy: { type: Boolean, default: false }
})
const emit = defineEmits(['retry', 'open-course', 'open-lesson'])
</script>

<style lang="scss" scoped>
.message-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.row.user {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.bubble {
  max-width: 82%;
  padding: 10px 14px;
  border-radius: 16px 16px 4px 16px;
  background: var(--el-fill-color);
  color: var(--el-text-color-primary);
  font-size: 15px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.unanswered {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.answer {
  display: flex;
  gap: 12px;
}

.avatar {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: 2px;
  border-radius: 8px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.body {
  flex: 1;
  min-width: 0;
}

.thinking {
  color: var(--el-text-color-secondary);
}

.dots::after {
  content: '…';
  animation: blink 1.2s steps(1) infinite;
}

.cursor {
  display: inline-block;
  width: 7px;
  height: 1.1em;
  margin-left: 2px;
  vertical-align: text-bottom;
  border-radius: 1px;
  background: var(--el-color-primary);
  animation: blink 1s steps(1) infinite;
}

@keyframes blink {
  50% {
    opacity: 0;
  }
}

.state {
  margin-top: 8px;
  font-size: 12px;
}

.stopped {
  display: inline-block;
  padding: 1px 8px;
  border-radius: 999px;
  background: var(--el-fill-color);
  color: var(--el-text-color-secondary);
}

.failure {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 12px;
  border: 1px solid var(--el-color-danger-light-7);
  border-radius: 10px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  font-size: 14px;
}

.sources,
.notice {
  margin-top: 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.notice {
  color: var(--el-text-color-placeholder);
}
</style>
```

`zhixue-student-web/src/views/chat/Composer.vue`：

```vue
<template>
  <div class="composer">
    <div v-if="scope" class="scope">
      <span class="scope-tag">
        正在问：{{ scope.title || '指定课程' }}
        <button
          v-if="!scopeLocked"
          type="button"
          class="scope-clear"
          aria-label="改为问全部课程"
          @click="emit('clear-scope')"
        >
          <el-icon><Close /></el-icon>
        </button>
      </span>
      <span v-if="scopeLocked" class="scope-hint">想换范围请开新对话</span>
    </div>

    <div class="box" :class="{ invalid: !!error }">
      <textarea
        ref="inputRef"
        :value="modelValue"
        class="input"
        rows="1"
        :placeholder="generating ? '回答生成中，按 Esc 停止' : '问点什么…（Enter 发送，Shift + Enter 换行）'"
        aria-label="输入问题"
        :aria-invalid="!!error"
        :aria-describedby="error ? 'composer-error' : undefined"
        @input="onInput"
        @keydown="onKeydown"
      />
      <span v-if="nearLimit" class="count tabular-nums" :class="{ over: overLimit }">{{ length }}/{{ max }}</span>
      <el-button
        v-if="generating"
        class="action"
        :icon="VideoPause"
        aria-label="停止生成"
        @click="emit('stop')"
      >
        停止
      </el-button>
      <el-button
        v-else
        type="primary"
        class="action"
        :icon="Promotion"
        :disabled="disabled || !modelValue.trim()"
        aria-label="发送"
        @click="emit('send')"
      >
        发送
      </el-button>
    </div>
    <p v-if="error" id="composer-error" class="error" role="alert">{{ error }}</p>
    <p v-else class="tip">AI 回答可能有误，请结合课程内容判断</p>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { Close, Promotion, VideoPause } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: { type: String, default: '' },
  generating: { type: Boolean, default: false },
  /** 冷却中（409 后）或会话加载中 */
  disabled: { type: Boolean, default: false },
  /** { id, title } 限定课程；null 表示全部课程 */
  scope: { type: Object, default: null },
  scopeLocked: { type: Boolean, default: false },
  error: { type: String, default: '' },
  max: { type: Number, default: 500 }
})
const emit = defineEmits(['update:modelValue', 'send', 'stop', 'clear-scope'])

const MAX_HEIGHT = 200
const inputRef = ref(null)
const length = computed(() => props.modelValue.trim().length)
const nearLimit = computed(() => length.value > props.max - 100)
const overLimit = computed(() => length.value > props.max)

function onInput(event) {
  emit('update:modelValue', event.target.value)
}

function onKeydown(event) {
  if (event.key === 'Escape' && props.generating) {
    event.preventDefault()
    emit('stop')
    return
  }
  // 输入法组字时的 Enter 是确认候选词，不是发送
  if (event.key !== 'Enter' || event.shiftKey || event.isComposing || event.keyCode === 229) {
    return
  }
  event.preventDefault()
  if (!props.generating && !props.disabled) {
    emit('send')
  }
}

/** 高度随内容增长，最多 200px 后出现滚动条 */
function autosize() {
  const el = inputRef.value
  if (!el) {
    return
  }
  el.style.height = 'auto'
  el.style.height = `${Math.min(el.scrollHeight, MAX_HEIGHT)}px`
}

watch(() => props.modelValue, () => nextTick(autosize))

defineExpose({ focus: () => inputRef.value?.focus() })
</script>

<style lang="scss" scoped>
.composer {
  width: 100%;
}

.scope {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  font-size: 13px;
}

.scope-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px 3px 10px;
  border-radius: 999px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary-dark-2);
}

.scope-clear {
  display: inline-flex;
  padding: 2px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: inherit;
  cursor: pointer;

  &:hover {
    background: var(--el-color-primary-light-8);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }
}

.scope-hint {
  color: var(--el-text-color-secondary);
}

.box {
  display: flex;
  align-items: flex-end;
  gap: 10px;
  padding: 10px 10px 10px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 14px;
  background: var(--zx-surface);
  box-shadow: var(--el-box-shadow-lighter);
  transition: border-color 0.16s var(--zx-ease), box-shadow 0.16s var(--zx-ease);

  &:focus-within {
    border-color: var(--el-color-primary-light-5);
    box-shadow: var(--zx-ring);
  }

  &.invalid {
    border-color: var(--el-color-danger);
  }
}

.input {
  flex: 1;
  min-width: 0;
  max-height: 200px;
  padding: 6px 0;
  border: 0;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  font-size: 15px;
  line-height: 1.6;

  &::placeholder {
    color: var(--el-text-color-placeholder);
  }
}

.count {
  align-self: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);

  &.over {
    color: var(--el-color-danger);
  }
}

.action {
  flex-shrink: 0;
  height: 36px;
  border-radius: 10px;
}

.error,
.tip {
  margin-top: 6px;
  padding-left: 4px;
  font-size: 12px;
}

.error {
  color: var(--el-color-danger);
}

.tip {
  color: var(--el-text-color-placeholder);
}
</style>
```

`zhixue-student-web/src/views/chat/ChatView.vue`（整体替换）：

```vue
<template>
  <div class="chat-view">
    <div ref="scrollRef" class="scroll" @scroll="onScroll">
      <div class="column">
        <p v-if="state.loading" class="loading">加载中…</p>

        <section v-else-if="!state.messages.length" class="empty">
          <h1>有什么想学的？</h1>
          <p>问编程问题，AI 课程助教会结合课程资料回答，并推荐相关的视频课</p>
          <div class="suggestions">
            <button
              v-for="question in SUGGESTIONS"
              :key="question"
              type="button"
              class="suggestion"
              :disabled="busy"
              @click="session.send(question)"
            >
              {{ question }}
            </button>
          </div>
        </section>

        <MessageList
          v-else
          :messages="state.messages"
          :busy="busy"
          @retry="(question) => session.retry(question)"
          @open-course="openCourse"
          @open-lesson="openLesson"
        />
      </div>
    </div>

    <footer class="footer">
      <div class="column">
        <Composer
          ref="composerRef"
          v-model="state.draft"
          :generating="state.generating"
          :disabled="state.cooldown || state.loading"
          :scope="state.courseScope"
          :scope-locked="state.scopeLocked"
          :error="state.inputError"
          :max="MAX_QUESTION"
          @send="session.send()"
          @stop="session.stop()"
          @clear-scope="clearScope"
        />
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import Composer from './Composer.vue'
import MessageList from './MessageList.vue'
import { MAX_QUESTION, createChatSession } from './chatSession'
import { askStream } from '@/api/chat'
import { getConversation, getConversationMessages } from '@/api/conversations'
import { getCourseDetail } from '@/api/course'
import { useConversationsStore } from '@/stores/conversations'
import { useUserStore } from '@/stores/user'
import { CHAT_DRAFT_KEY } from '@/utils/auth'
import { sessionExpired } from '@/utils/session'

const SUGGESTIONS = ['HashMap 为什么线程不安全', '线程池参数怎么设', 'String 为什么是不可变的']
/** 距底部多少像素以内算「在底部」：在底部时新内容自动跟随，学生往上翻看时不打扰 */
const STICK_THRESHOLD = 80

const route = useRoute()
const router = useRouter()
const user = useUserStore()
const conversations = useConversationsStore()

const scrollRef = ref(null)
const composerRef = ref(null)
let stickToBottom = true

const session = createChatSession({
  askStream: (body, options) => askStream(body, { ...options, token: user.token }),
  loadMessages: getConversationMessages,
  onConversationCreated(meta) {
    const id = String(meta.conversationId)
    conversations.upsert({ id, title: meta.title, courseId: state.courseScope?.id ?? null })
    // 只换地址不重建页面：两条路由共用这个组件实例，watch 发现 ID 与当前会话相同就不会重新加载
    router.replace({ name: 'conversation', params: { id } })
  },
  onActivity: (id) => conversations.bump(id),
  onUnauthorized() {
    // 问题已放回输入框；登录回来后从这里恢复
    sessionStorage.setItem(CHAT_DRAFT_KEY, state.draft)
    sessionExpired()
  },
  onConversationGone() {
    conversations.load().catch(() => {})
    router.replace({ name: 'chat' })
  },
  notify: (type, message) => ElMessage({ type, message })
})
const state = session.state
const busy = computed(() => state.generating || state.cooldown)

/** 历史会话只存了课程 ID：取一次课程标题用于「正在问：…」标签 */
async function fillScopeTitle(conversationId, courseId) {
  try {
    const detail = await getCourseDetail(courseId)
    if (state.conversationId === conversationId && state.courseScope) {
      state.courseScope.title = detail.title
    }
  } catch {
    // 取不到标题时显示「指定课程」
  }
}

/** 会话的课程范围：「最近」只加载了前 50 条，不在列表里（直接输入地址或很久以前）时再问一次接口 */
async function courseOf(conversationId) {
  await conversations.ensureLoaded().catch(() => {})
  const known = conversations.find(conversationId)
  if (known) {
    return known.courseId
  }
  try {
    const conversation = await getConversation(conversationId)
    return conversation?.courseId ?? null
  } catch {
    return null
  }
}

async function openConversation(id) {
  const courseId = await courseOf(id)
  await session.open(id, courseId ? { id: courseId, title: '' } : null)
  if (courseId) {
    fillScopeTitle(id, courseId)
  }
  scrollToBottom(true)
}

function startNew() {
  session.reset()
  if (route.query.courseId) {
    session.setScope({ id: route.query.courseId, title: route.query.courseTitle || '' })
  }
  nextTick(() => composerRef.value?.focus())
}

watch(
  () => [route.name, route.params.id, route.query.courseId],
  ([name, id]) => {
    if (name === 'conversation') {
      if (String(id) !== state.conversationId) {
        openConversation(String(id))
      }
    } else if (name === 'chat') {
      startNew()
    }
  },
  { immediate: true }
)

function clearScope() {
  session.clearScope()
  if (route.query.courseId) {
    router.replace({ name: 'chat' })
  }
}

function openCourse(card) {
  router.push({ name: 'course', params: { id: card.courseId } })
}

/** 课程详情页按 lesson 参数打开该课时：可播放直接播放，否则提示购买 */
function openLesson(card, lesson) {
  router.push({ name: 'course', params: { id: card.courseId }, query: { lesson: lesson.lessonId } })
}

function onScroll() {
  const el = scrollRef.value
  stickToBottom = el.scrollHeight - el.scrollTop - el.clientHeight < STICK_THRESHOLD
}

function scrollToBottom(force = false) {
  nextTick(() => {
    const el = scrollRef.value
    if (el && (force || stickToBottom)) {
      el.scrollTop = el.scrollHeight
    }
  })
}

watch(
  () => {
    const last = state.messages[state.messages.length - 1]
    return [state.messages.length, last?.content.length, last?.status, last?.recommendations?.length]
  },
  ([length], [previousLength] = []) => scrollToBottom(length > (previousLength ?? 0))
)

onMounted(() => {
  const draft = sessionStorage.getItem(CHAT_DRAFT_KEY)
  if (draft) {
    sessionStorage.removeItem(CHAT_DRAFT_KEY)
    state.draft = draft
  }
  // 首次进入时 watch 先于挂载执行，那时输入框还不存在，这里补一次聚焦
  if (!state.conversationId) {
    composerRef.value?.focus()
  }
})

// 离开对话页（例如点推荐卡片去课程详情）：进行中的回答按「已停止」保存，回来时从服务端重新加载
onBeforeUnmount(() => session.stop())
</script>

<style lang="scss" scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 28px 24px 16px;
}

.column {
  width: 100%;
  max-width: 760px;
  margin: 0 auto;
}

.loading {
  padding: 60px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}

.empty {
  padding: 14vh 0 24px;
  text-align: center;

  h1 {
    font-size: 28px;
    letter-spacing: -0.02em;
  }

  p {
    margin-top: 8px;
    color: var(--el-text-color-secondary);
  }
}

.suggestions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px;
  margin-top: 28px;
}

.suggestion {
  padding: 8px 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 999px;
  background: var(--zx-surface);
  color: var(--el-text-color-regular);
  font: inherit;
  cursor: pointer;
  transition: border-color 0.16s var(--zx-ease), color 0.16s var(--zx-ease);

  &:hover:not(:disabled) {
    border-color: var(--el-color-primary-light-5);
    color: var(--el-color-primary);
  }

  &:focus-visible {
    outline: none;
    box-shadow: var(--zx-ring);
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }
}

.footer {
  flex-shrink: 0;
  padding: 8px 24px 16px;
  background: linear-gradient(to bottom, transparent, var(--zx-surface) 24px);
}

@media (max-width: 959px) {
  .scroll {
    padding: 16px 14px 12px;
  }

  .footer {
    padding: 6px 12px 12px;
  }

  .empty {
    padding-top: 8vh;

    h1 {
      font-size: 22px;
    }
  }
}
</style>
```

- [ ] **Step 6: 构建与契约检查**

```bash
cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm run build 2>&1 | grep -ciE "warn|deprecat|error"
grep -rn "v-html" src | grep -v "views/chat/AnswerMarkdown.vue" || echo "v-html 只在 AnswerMarkdown"
cd /Users/jax/Projects/zhixue-cloud && bash scripts/local/contract-check.sh | tail -3
```

Expected: `0`；`v-html 只在 AnswerMarkdown`；契约检查通过（含 `/ai/conversations/messages`）。

- [ ] **Step 7: 对话分段与全量界面验收**

`scripts/local/ui/student-web-chat.sh`：

```bash
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
  check "回答承接上文（围绕 ConcurrentHashMap）" \
    "$(python3 -c 'import json,sys;print("ConcurrentHashMap" in json.loads(sys.argv[1])["text"])' "$r" 2>/dev/null)" "True"

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
```

```bash
cd /Users/jax/Projects/zhixue-cloud && bash scripts/local/student-web-check.sh 2>&1 | tee /tmp/zx_student_web_check.out | grep -E "^\[(ok|fail|skip|info)\]|=====" 
```

Expected: login、course、shell、chat 四个分段全部 `[ok]`，没有 `[skip]`；`[info] 已清理本次创建的 … 个会话`、`[info] 已退款订单 …`；最后 `[ok] 学生端网页版界面验收全部通过`。会临时停掉 Ollama 约 1 分钟（第 8 项），结束时一定恢复：

```bash
curl -s http://127.0.0.1:11434/api/version
```

人工核对截图（`/tmp/zhixue-student-web-check/`）：`chat-answer-1440.png`（代码块高亮、参考资料、推荐卡片：课程名、价格或已购、可试看、最多 2 个课时）、`chat-failure-1440.png`（红色失败气泡与重试）、`shell-390*.png`（窄屏）。把截图发给用户确认后再提交。

- [ ] **Step 8: 设计文档同步**

`docs/superpowers/specs/2026-10-20-student-web-ai-chat-design.md`：

1. 3.4 节表格 `src/api/chat.js` 一行的「内容」改为：

```markdown
流式客户端：`fetch` + `ReadableStream` 逐行解析 SSE（`api/sse.js`），通过 `onMeta / onDelta` 回调输出过程、以 `AbortSignal` 中止；结束时返回 `done / error / rejected / aborted / interrupted` 五种结果之一（流前错误以 `body.code` 为准）
```

2. 3.3 第 4 条中「课时前的编号（如 2.2）由前端按章节顺序与课时顺序生成」改为「课时前的编号（如 2.2）由课程服务的大纲接口给出（`chapterIndex.lessonIndex`），与课程详情目录的编号规则一致」。
3. 11 节修订记录追加一行：

```markdown
| 2026-10-20 | 学生端网页版实施计划 | 3.4 流式客户端改为返回结束方式（便于覆盖 6.1 全部情形）；3.3 课时编号由大纲接口给出；从推荐卡片点课时进入课程详情 `?lesson=` 再播放或提示购买，离开对话页时进行中的回答按「已停止」保存 |
```

- [ ] **Step 9: 提交**

```bash
cd /Users/jax/Projects/zhixue-cloud
git add zhixue-student-web scripts/local/ui/student-web-chat.sh docs/superpowers/specs/2026-10-20-student-web-ai-chat-design.md
git commit -m "feat(student-web): 对话页——流式回答、安全 Markdown、推荐卡片、停止/重试/失败气泡、限定课程提问"
```

---

## 检查点 W-B：一期完成的定义（设计文档 7.4）

- [ ] 学生端：`npm test` 83 passed；`npm run build` 无告警。
- [ ] 后端：`mvn test` 全绿（后端计划检查点 C 的命令）。
- [ ] 回归脚本全部以 `[ok]` 结束：

```bash
cd /Users/jax/Projects/zhixue-cloud
ZHIXUE_SKIP_MEDIA=1 bash scripts/local/smoke.sh | tail -1
bash scripts/local/journeys.sh | tail -1
bash scripts/local/ai-reco-check.sh | tail -1
bash scripts/local/contract-check.sh | tail -1
bash scripts/local/student-web-check.sh | tail -1
```

- [ ] 1440 与 390 两种宽度的截图经用户人工核对。
- [ ] `ai-reco-check.sh` 输出的时延达标：首字 ≤ 1.5s、回答结束到推荐 ≤ 0.5s。
- [ ] 推送前做密钥扫描；推送需用户确认。

---

## 自查记录

**1. 设计覆盖**

| 设计条目 | 落在 |
|---|---|
| 3.1 工程、端口 3001、同源代理、令牌复制、锁定 markdown-it 与 highlight.js（只注册 java/sql/xml/json/bash/javascript） | 任务 1、5 |
| 3.2 布局；一期隐藏二期入口 | 任务 1、3 |
| 3.3-1 新对话空状态与推荐问题 | 任务 5 |
| 3.3-2 流式、停止后保留并标「已停止」、不出推荐 | 任务 4、5 |
| 3.3-3 / 6.2 Markdown 与内容安全 | 任务 5（8 个单元测试） |
| 3.3-4 推荐卡片：课程 → 详情；课时 → 可播放直接播放、否则购买提示 | 任务 2、5 |
| 3.3-5 限定课程提问、范围创建时确定、之后不可改 | 任务 4（状态机）、5（标签） |
| 3.3-6 「最近」排序、重命名、删除 | 任务 3 |
| 3.3-7 < 960px 抽屉 | 任务 1、3 |
| 3.3-8 账号密码 + 验证码登录；注册二期 | 任务 1 |
| 3.3-9 课程详情（封面、价格、简介、目录、购买、问这门课、详情内播放器） | 任务 2 |
| 4.1 / 4.2 接口与事件、ID 字符串、流前错误以 body.code 为准 | 任务 3、4 |
| 4.5 历史卡片按最新状态 | 任务 5（离开即停止、返回重新加载），后端负责解析 |
| 6.1 异常矩阵（前端列） | 任务 4 状态机测试 + 任务 5 界面：401 保留草稿、400 输入框提示、404 回到新对话并刷新「最近」、409 提示并冷却 3 秒、500 发送失败问题回到输入框、503/504 失败气泡与重试、notice 灰字、断线「连接中断」、未完成回答「此问题未完成回答」 |
| 7.3 界面验收 1–10 | `student-web-check.sh`：1 → login；3 → course、chat；9 → shell；2、4–8、10 → chat |
| 7.4 完成定义 | 检查点 W-B |

**2. 本计划的代码在写入前已验证**

- 全部代码先在临时工程里写成，按任务边界拆成 5 个阶段**逐一**构建与测试：每个任务结束时都能构建、无告警，单测依次为 28 / 33 / 38 / 75 / 83 项全部通过。
- 登录、课程两个分段已在本机真实网关上跑通（含真实下单、模拟支付、管理员退款复原）；外壳分段除「最近」外通过（会话接口要等后端任务 8）。
- 对话页在会话接口上线前，用请求拦截模拟 SSE 验证过：流式渲染、代码高亮、`<script>` 被转义、参考资料、推荐卡片、失败气泡保留已出文字、409 撤回提问并冷却、重命名与删除菜单、历史会话的课程范围标签、进入新对话时输入框自动聚焦。
- 契约检查扫描临时工程：13 条前端路径全部被网关路由覆盖。

**3. 写计划时修正的问题**

- 登录页的「刷新验证码」按钮名称也含「验证码」，验收脚本按标签找输入框会命中两个元素，改为按占位文字定位。
- 课程 2007 的目录接口返回的种子课 ID 是数字、新建课是字符串：`buildLessonRows` 一律转字符串，测试两种都覆盖。
- 测试里的 `url: '/ok'` 会被契约检查当成真实请求而报错：测试移到 `tests/unit/`。
- `askStream` 用 `fetch` 拼地址，契约检查扫不到：改为 `url` 字面量常量。
- 本地测试服务器在 `afterAll` 不断开长连接时，测试要多等 3 秒：显式 `closeAllConnections()`。
- Vite 5 默认用 Sass 旧版接口，构建刷屏弃用告警：改用 `modern-compiler`。
- `playwright-cli` 会在当前目录写会话日志：验收脚本切到输出目录再运行，不污染仓库。
- 紧跟中文的 `$变量` 在 `set -u` 下会被 bash 当成变量名的一部分：脚本统一写 `${变量}`。
- 与后端计划交叉核对发现的三处联调问题，已在本计划与后端计划中一起修好：
  1. **停止后马上再问会 409**：后端要等下一次写出才发现连接断开，最长 30 秒。现在停止后若收到 409，冷却 3 秒自动重发一次（`SERVER_RELEASE_MS` 之外不重发），4 个单元测试覆盖。
  2. **打开不在「最近」前 50 条里的会话看不到课程范围**：`/messages` 不带会话的 `courseId`，改用后端新加的 `GET /conversations/{id}` 取。
  3. **地址里的 ID 不合法时后端返回 400**（`/chat/abc`）：路由守卫先用 `ID_PATTERN` 校验，提示并回到新对话，不去请求后端。

**4. 已知局限（一期接受）**

- 离开对话页（例如点推荐卡片去课程详情）会中止进行中的回答并保存为「已停止」；卡片只在回答完成后出现，正常使用不会碰到。
- 停止后收到 409 会自动重发一次（冷却 3 秒后）；服务端释放占位最长要 30 秒，超过这个时间再收到 409 就只冷却、不再自动重发。
- Element Plus 全量引入（gzip 约 250KB）。
- 界面验收依赖本机 Homebrew 管理的 Ollama（`brew services stop/start ollama`）。
