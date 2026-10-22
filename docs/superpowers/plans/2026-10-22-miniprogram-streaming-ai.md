# 小程序流式 AI Implementation Plan

> **For agentic workers:** 使用 incremental-implementation 在本会话执行，TDD 后逐步接入页面。

**Goal:** 使小程序流式答疑、多轮会话、历史与推荐使用现有后端契约。
**Architecture:** codec 与 wx.request 传输分离，页面持有请求代次和终态；JSON会话API沿用request封装。
**Tech Stack:** CommonJS、WXML/WXSS、Node test、RequestTask；不增加小程序运行时依赖。

## Global Constraints
- IDs为字符串；question≤500；纯文本呈现；不信任卡片的播放权限。
- 保留旧askCourse与已有后端；不改数据库。
- 自动化超时不算验收通过；未知结果断线不重发POST。

## S1：流式传输
- [x] `tests/ai-stream.test.cjs` 用模拟 RequestTask 实测 UTF-8 跨字节、拆事件、CRLF、JSON401/409、取消、done/error/断流、迟到回调。
- [x] 红色命令 `node --test zhixue-app-mp/tests/ai-stream.test.cjs`。
- [x] 新增 `utils/sse-codec.js` 的 createSseCodec(onEvent)，push(ArrayBuffer)/finish()；残留UTF-8字节不能逐块丢弃。
- [x] 新增 `api/ai-stream.js` 的 startStream(body,{onMeta,onDelta,request,baseUrl,token}) → {promise,abort}；生产调用由页面提供请求源、基址和令牌。
- [x] 相同命令变绿。

## S2：会话与页面
- [x] 新增 `api/conversations.js` 的 list/get/messages/rename/delete，复用已有request。
- [x] 页面 onSend 添加占位回答，onMeta锁定范围，onDelta合并、80ms更新，终态flush；onHide/onUnload/new/open中止并隔离旧请求。
- [x] 接入停止、重试、课程范围、最近列表、打开/重命名/删除与推荐卡片。
- [x] 推荐课时导航修复：播放器必须识别 lessonId 并以当前后端目录权限决定播放；不更改付费规则。
- [x] Node全量测试、模拟器编译/console/截图；真实流式和停止的端到端验证必须有可用automator或用户配合真机。

## 检查点 S-A
- [x] codec和传输行为测试通过，无新增依赖。
- [x] 流式字数增长、停止部分保留、历史多轮恢复、推荐精准导航在实际运行时核验。
- [x] 清理本轮会话、更新规格状态；不称本地模型性能目标已达标。

## S-A 验收记录（2026-10-22）

- 全部小程序 Node 回归 36 项通过：流式传输 16、AI页面 11、推荐精准导航 2、图标与图片兜底 7；失败测试覆盖异步 success 尾事件异常、401与SSE混用、畸形done、响应大小限制、历史取消/删除竞态、身份未加载草稿。
- `git diff --check` 通过；本次流式文件的 IDE 错误/警告为0。网关前缀契约检查120条路径通过（静态检查不等于所有接口运行验收）。
- 微信开发者工具2.02.2608080、实际基础库3.17.5。自动化已恢复；登录失败根因为本机Docker及应用容器停止。只启动已有容器，未调用会初始化数据库的up.sh。
- 真实首问「线程池核心参数怎么设置？」完成：508字、推荐2007、参考资料2条。
- 真实追问「那拒绝策略有哪些，分别适用于什么情况？」同一会话完成：410字，视图字数观测为0、16、41、78、124、140、189、209、234、275、301、331、373、401、410，证明真实分块显示。
- 第三轮停止：本地保留7字、状态stopped、推荐0。重新从接口加载历史为六条消息，两次done、一次stopped；服务端停止记录25字（关闭连接传播有延迟，符合现有后端语义）。
- 推荐试看4021：播放器请求/选中4021、locked=false、有视频；锁定4025：locked=true、视频为空、未选中其他课时，并显示购买提示，没有支付。
- 会话重命名与删除走真实页面方法和后端接口；仅确认输入通过showModal mock提供，验证后已restore。
- 单次流式请求注入网络失败显示interrupted且loading=false；wx.request在finally恢复，点击失败气泡重试后真实完成299字并返回3张推荐卡片。
- 本轮会话2112983921351606274、2112985259846287362均以学生本人身份删除；第二次清理code=200、列表不再包含该会话。未清理其他用户会话或修改订单。
- 最终截图：`zhixue-app-mp/tests/artifacts/stream-answer-final.jpg`、`stream-history-stopped.jpg`、`stream-locked-lesson.jpg`。390px输入栏实测输入框286px、按钮72px。
- 真机分块、切后台、弱网恢复尚未验收；模拟器TextDecoder存在，但实现不依赖它。旧非流式askCourse保持兼容。学习记录未纳入本任务。

## 代码审查处理
规范轴：终态关闭、success/chunk回调异常、非2xx响应分类及无界缓冲均已修复并先红后绿。
规格轴：同账号草稿恢复、身份未就绪等待、发送时固定owner、历史请求代次及删除回包覆盖均已修复并先红后绿。定向复审确认上述Required消除。

本次只完成代码和本地验收，未提交、未推送、未上传小程序；工作区保留既有图标阶段改动。
