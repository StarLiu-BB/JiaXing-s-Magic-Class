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
修改 `api/request.js` 中的 `BASE_URL`：
```javascript
const BASE_URL = 'http://192.168.211.175:9000'
```

### 3. 添加 TabBar 图标
在 `miniprogram/images/tabbar/` 目录下添加以下图标：
- `home.png` / `home-active.png` - 首页图标
- `category.png` / `category-active.png` - 分类图标
- `study.png` / `study-active.png` - 学习图标
- `my.png` / `my-active.png` - 我的图标

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

