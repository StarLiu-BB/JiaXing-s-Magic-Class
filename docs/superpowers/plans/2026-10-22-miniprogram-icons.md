# 小程序图标修复 Implementation Plan

> **For agentic workers:** 使用 incremental-implementation 在本会话按垂直切片执行，每步先验证失败再修复。

**Goal:** 修复小程序首页分类、底部导航和页面操作图标，并使图片缺失时有可靠兜底。

**Architecture:** Element Plus Icons SVG 构建期生成本地 PNG；分类映射在前端集中维护。公共 safe-image 组件负责有界失败回退。

**Tech Stack:** 原生 WXML/WXSS/CommonJS，Node 内置测试，构建期 @resvg/resvg-js 2.6.2。

## Global Constraints
- 保留现有页面布局、原生 TabBar、点击事件与课程权限判断。
- 不使用远程字体，不增加小程序运行时 UI 依赖。
- 图片与图标必须入库，图标来源 MIT 许可随资源保留。
- 自动化超时不算验收通过；逐页截图验证须与代码测试一起报告。

## 任务 I1：导航和分类
- [x] 创建 `zhixue-app-mp/tests/icons.test.cjs`：检查四个 tab 普通/选中态资源、PNG 签名与尺寸、未知分类、固定 ID 映射。
- [x] 执行 `node --test zhixue-app-mp/tests/icons.test.cjs`，确认没有导航图标/资源时失败。
- [x] 创建 `zhixue-app-mp/tools/package.json` 和图标生成器 `generate-icons.cjs`；工具依赖只装在 tools。
- [x] `npm ci --prefix zhixue-app-mp/tools && node zhixue-app-mp/tools/generate-icons.cjs`：从已安装的 SVG 图标组件生成资源和许可，不修改安装目录。
- [x] 修改 `app.json` 四组 iconPath/selectedIconPath；新增 `utils/category-icons.js` 的 `withCategoryIcon(category)`，在首页分类数据装载中映射，不更改后端。
- [x] 重跑 I1 测试。

## 任务 I2：操作图标和图片兜底
- [x] 新增 safe-image 组件状态测试：null/空源、失败一次回退、兜底失败停止、新源重新加载。
- [x] 运行测试，确认组件缺失导致失败。
- [x] 实现 `components/safe-image`，属性 src/fallback/mode/lazyLoad，状态 resolvedSrc；`onError` 有界回退，不循环。
- [x] `app.json` 全局注册组件；替换页面图片节点为 safe-image 并保留类名和模式。头像、分类、空状态明确指定 fallback。
- [x] 替换详情收藏和播放器目录/弹幕 iconfont、我的页操作图标；保留文字和已有绑定；增加图标固定尺寸样式。没有介绍视频时详情封面改用 safe-image。
- [x] 测试所有静态资源存在、无 iconfont 残留，重跑组件测试。

## 检查点 I-A
- [x] `node --test zhixue-app-mp/tests/*.test.cjs` 全通过。
- [x] 微信开发者工具编译并截图：首页、分类、我的、课程详情、播放器；四个导航可见、类别各有语义图标、控制按钮保留原语义。
- [x] 图片 URL 失败和默认资源的行为由组件状态测试覆盖；真机验证未执行时明确记录。
- [x] 更新已有 README 的图标使用说明，审查 diff 和资源许可；不提交 node_modules。

## 验收记录
- Node 回归：7 项通过（导航双态、分类 ID 映射、全部模板资源存在、空/null 源、单次回退、换源恢复、迟到错误隔离）。工具依赖安全审计 0 漏洞，IDE 诊断 0 项。
- 模拟器截图：首页、分类、我的、详情、播放器已逐页人工核对；详情标签不换行，底部按钮避开安全区，播放器目录/弹幕不再被输入栏遮挡。
- 自动化工具仍返回 `timeout waiting for automator response`；带 wait 参数的截图也超时，不把这部分计为通过。截图不带自动化等待时可用，最终图片采用已检查的首页与播放器截图。
- 尚未真机验收收藏/弹幕切换两态及屏幕旋转。资源两态存在由 Node 测试保证，不代表真机点击测试已通过。
- 没有修改支付、课程权限、学习记录和 AI 代码。

## 后续移交
I-A 的资源和截图验收已完成；自动化/真机项受上述环境限制。随后独立生成 AI 流式规格和计划，再推进学习记录；不将尚未设计的后端数据表混入图标任务。
