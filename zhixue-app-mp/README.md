# 智学云微信小程序

## 项目结构

```
zhixue-app-mp/
├── miniprogram/
│   ├── api/                    # API接口
│   │   ├── request.js          # 请求封装
│   │   └── auth.js             # 认证API
│   ├── utils/                  # 工具函数
│   │   └── auth.js             # 认证工具
│   ├── pages/                  # 页面
│   │   ├── index/              # 首页
│   │   ├── category/           # 分类页
│   │   ├── study/              # 学习页
│   │   └── my/                 # 我的页
│   ├── app.js                  # 小程序入口
│   ├── app.json                # 全局配置
│   ├── app.wxss                # 全局样式
│   ├── sitemap.json           # 站点地图
│   └── project.config.json     # 项目配置
└── README.md
```

## 功能特性

### 1. 全局配置 (app.json)
- ✅ TabBar配置（首页、分类、学习、我的）
- ✅ 分包配置（课程、订单、AI等）
- ✅ 预加载规则
- ✅ 网络超时配置

### 2. 全局逻辑 (app.js)
- ✅ globalData（用户信息、token、登录状态）
- ✅ 登录逻辑（微信登录、获取用户信息）
- ✅ 退出登录
- ✅ 自动检查更新

### 3. 请求封装 (api/request.js)
- ✅ wx.request 统一封装
- ✅ 请求/响应拦截器
- ✅ Token 自动携带
- ✅ 统一错误处理（401跳转登录、403无权限等）
- ✅ 请求队列（防止重复请求）
- ✅ 加载提示
- ✅ Token刷新机制（预留接口）

### 小程序 AI 答疑
- 使用现有 `POST /ai/conversations/messages` 流式接口，支持停止、重试、同一会话追问。
- 最近会话支持分页、打开、重命名、删除；历史按服务端的完成/停止/失败状态恢复。
- 回答完成后显示参考资料和课程推荐；推荐课时由播放器重新校验观看权限，锁定课时提示购买。
- 新服务端返回 `saved=false` 或显式 `saved=null` 时，分别提示未保存/保存待确认；旧服务端缺字段时不误报。
- `api/ai-stream.js` 独立使用 RequestTask 分块回调，`utils/sse-codec.js` 增量解码 UTF-8，不要求真机存在 TextDecoder。
- 模型内容以纯文本显示，不解析 HTML，不加载模型提供的图片。401 草稿按账号隔离、30分钟过期；断线不自动重发。
- 开发者工具基础库 3.17.5 已验真实流式、多轮、停止和历史；真机分块网络与切后台行为仍需验收。

### 4. 认证工具 (utils/auth.js)
- ✅ Token 存储到 Storage
- ✅ 用户信息存储
- ✅ 登录状态检查
- ✅ 清除认证信息
- ✅ 登录跳转提示

## 使用说明

### 1. 配置 AppID
在微信开发者工具中配置你的小程序 AppID。

### 2. 配置后端地址
开发环境默认读取本机网关地址，也可以通过 Storage 覆盖：
```javascript
const BASE_URL = wx.getStorageSync('BASE_URL') || 'http://127.0.0.1:19001'
```

如需改成其他环境，可在微信开发者工具控制台执行：
```javascript
wx.setStorageSync('BASE_URL', 'http://你的网关地址:19001')
wx.setStorageSync('WS_URL', 'ws://你的互动服务地址:9999/ws/danmaku')
```

### 3. 图标与图片兜底
本地图标已接入，源自 Element Plus Icons 2.3.2（MIT，许可见 `miniprogram/images/LICENSE`）。保留原生 TabBar，导航普通/选中态资源位于 `images/tabbar/`；分类 ID 映射集中在 `utils/category-icons.js`，未知分类使用默认图标；操作图标在 `images/actions/`。

`safe-image` 全局组件保留原生图片显示模式和外部样式类，空地址或加载失败时回退到指定本地图片，兜底失败不会循环请求。头像使用 `avatar-default.png`，课程封面使用 `course-default.png`。

在仓库根目录重新生成资源或执行回归：
```bash
npm ci --prefix zhixue-app-mp/tools
node zhixue-app-mp/tools/generate-icons.cjs
node --test zhixue-app-mp/tests/*.test.cjs
```
生成器从 SVG 输出高分辨率 PNG，工具依赖位于 `tools/`，不进入小程序代码包。

### 4. 使用请求封装
```javascript
const { get, post } = require('../../api/request')

// GET请求
const res = await get('/course/list', { pageNum: 1, pageSize: 10 })

// POST请求
const res = await post('/order/create', { courseId: 123 })
```

### 5. 使用认证工具
```javascript
const { getToken, setToken, isLogin } = require('../../utils/auth')

// 检查登录状态
if (isLogin()) {
  const token = getToken()
  // 使用token
}
```

### 6. 全局登录
```javascript
const app = getApp()

// 登录
await app.login()

// 检查登录状态
if (app.globalData.isLogin) {
  const userInfo = app.globalData.userInfo
}
```

## API 接口说明

### 认证接口 (api/auth.js)
- `wxLogin(code)` - 微信登录
- `getUserInfo()` - 获取用户信息
- `logout()` - 退出登录
- `refreshToken()` - 刷新Token

## 注意事项

1. **Token 存储**：Token 存储在微信小程序的 Storage 中，会自动同步到 `app.globalData`
2. **登录过期**：当接口返回 401 时，会自动清除 Token 并提示重新登录
3. **网络错误**：请求失败时会自动显示错误提示
4. **分包加载**：课程、订单、AI 等功能使用分包加载，提升首屏加载速度

## 开发规范

1. 所有 API 请求统一使用 `api/request.js` 封装
2. Token 相关操作统一使用 `utils/auth.js`
3. 页面样式优先使用 `app.wxss` 中的全局样式类
4. 遵循小程序开发规范，注意性能优化
