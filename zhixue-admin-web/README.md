# 智学平台管理系统

Vue 3 + Vite + Element Plus 管理后台项目

## 技术栈

- Vue 3.4 + Composition API
- Vite 5
- Element Plus
- Vue Router 4
- Pinia
- Axios
- SCSS
- ECharts

## 环境要求

- Node.js >= 16.0.0
- npm >= 7.0.0

## 安装依赖

```bash
npm install
```

## 开发

```bash
npm run dev
```

## 构建

```bash
npm run build
```

## 预览构建结果

```bash
npm run preview
```

## 环境变量配置

请复制 `env.example` 文件创建环境变量文件：

```bash
# Windows PowerShell
Copy-Item env.example .env.development
Copy-Item env.example .env.production

# Linux/Mac
cp env.example .env.development
cp env.example .env.production
```

### 开发环境 (.env.development)

```env
VITE_APP_BASE_API=/api
VITE_APP_TITLE=智学平台管理系统
```

### 生产环境 (.env.production)

```env
VITE_APP_BASE_API=/api
VITE_APP_TITLE=智学平台管理系统
```

## 项目结构

```
zhixue-admin-web/
├── src/
│   ├── api/          # API 接口
│   ├── components/   # 公共组件
│   ├── router/       # 路由配置
│   ├── stores/       # Pinia 状态管理
│   ├── utils/        # 工具函数
│   ├── views/        # 页面组件
│   ├── App.vue       # 根组件
│   └── main.js       # 入口文件
├── index.html        # HTML 模板
├── vite.config.js    # Vite 配置
└── package.json      # 项目配置
```

## 代理配置

开发环境代理配置在 `vite.config.js` 中：

- 代理地址：`http://192.168.211.175:9000`
- 代理路径：`/api`

## 注意事项

1. 请确保后端服务正常运行在 `192.168.211.175:9000`
2. 环境变量文件需要手动创建（复制 `env.example` 为 `.env.development` 和 `.env.production`）
3. 首次运行前请执行 `npm install` 安装依赖
4. 项目使用 Element Plus 组件库，已配置自动导入，无需手动导入组件

