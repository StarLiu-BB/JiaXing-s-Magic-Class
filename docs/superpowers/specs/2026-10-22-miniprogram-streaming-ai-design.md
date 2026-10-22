# 小程序流式 AI 答疑

用户批准与网页版能力逐步对齐：流式文字、停止、失败重试、同一会话追问、最近会话、历史恢复、推荐课程与课时跳转。保持小程序纯文本展示，不渲染模型 HTML，不加载模型生成图片。

## 契约
复用 POST /ai/conversations/messages；新会话发送 question/courseId，追问只发送 conversationId/question。meta 给出字符串会话/消息 ID，delta.text 追加，done.sources/notice/recommendations 展示，error.code/msg 原文显示。流前 HTTP 200 仍可能是 JSON 错误，按响应头和业务 code 分流。

传输模块使用 wx.request(enableChunked:true,responseType:arraybuffer) 与 RequestTask 回调。返回 {promise,abort}，不修改普通 JSON request.js。不假定 TextDecoder 存在，使用增量 UTF-8 解码；跨字节、跨事件、CRLF、多行data和尾事件覆盖测试。收到 done/error 后不再修改页面。未知结果断线不自动重发，避免重复创建会话。

## 交互
按最近消息时间展示会话列表，可新建、打开、重命名和删除。课程范围在新会话首问发送前可清除，创建后锁定。同一时间只一个生成；409 撤回未保存提问并保留输入，短暂禁用发送，不自动发起未知POST。

停止调用 abort，保留文字标已停止、不出推荐。离开页面或切换会话中止请求，并以页面请求代次过滤迟到回调。setData 每80ms批量刷新，终态立即flush。401 保留输入、清登录态并导航到我的页登录；404 提示并刷新会话列表。

推荐只显示后端提供的卡片，最多3门、每门2课时。点课进入详情；点课时带 lessonId，播放器重新向后端获取访问权限，不能信任卡片中的 playable。历史失败/停止按1/2/3状态恢复，问题无后续回答标未完成。

## 验证与限制
Node测试覆盖codec、模拟RequestTask、取消/JSON拒绝/错误/断线，页面生命周期与迟到回调。已在开发者工具基础库3.17.5实测真实wx.request分块接收、两轮完成和停止、多轮历史恢复与推荐课时导航；TextDecoder在该模拟器中存在，但实现仍使用独立增量UTF-8解码。真机尚未验证，不将模拟器证据当成真机验证。旧非流式 askCourse 保留兼容。统一最低基础库为当前根项目的3.12.1。

传输安全边界：仅HTTP 2xx且Content-Type为SSE时分发事件；总响应上限1MiB，单个未结束事件上限128Ki字符，超限中断并关闭请求。已消费SSE不保留原文，JSON回退只捕获受限正文。终态、畸形数据、异步回调异常均保证结算Promise和关闭连接。

401草稿只保存发送前固定的账号ID、问题、会话与课程范围，有效期30分钟，不含令牌；身份尚未加载不消费，确认同账号后恢复且不自动发送。切换账号不恢复原账号草稿。历史请求、删除回包和页面隐藏均通过请求代次隔离，不能覆盖后来的会话。
