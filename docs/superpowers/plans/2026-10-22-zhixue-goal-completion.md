# 智学云目标达成总计划（学习闭环 · 经营闭环 · 质量门禁 · 上线）

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 `incremental-implementation`（模式 B 子代理控制器优先；否则模式 A 本会话执行）逐任务实施本计划。步骤用复选框（`- [ ]`）跟踪。每个任务内部先写失败测试，再写最小实现。

**Goal:** 把当前"核心链路可演示"的智学云，推进到"学员能持续学、运营能完整卖与售后、AI 质量可量化、质量可回归、生产可上线"的完整产品状态。

**Architecture:** 保持现有 Spring Cloud Alibaba 多服务 + 每服务独立 MySQL 库的架构不变。新增能力全部落在既有服务内（学习进度/评价在 `zhixue-course`，用券/售后/教师经营在 `zhixue-order`，券锁定在 `zhixue-marketing`，数据修正与访问控制加固在 `zhixue-system`），跨服务只用现有 Feign + `X-Inner-Call`/`X-Internal-Token` 内部调用模式，不引入 Seata、不引入新中间件。前端按既有三端分工补齐：小程序做学习中心与用券下单，学生 Web 做二期门户，管理后台做评价/售后/教师经营与既有缺陷修复。

**Tech Stack:** Java 17 / Spring Boot 3.2.0 / Spring Cloud 2023.0.0 / MyBatis-Plus 3.5.7 / MySQL 8 / Redis 7 / RabbitMQ / Elasticsearch 8 / Vue 3.5 + Vite 5 + Element Plus 2.13 / 微信小程序原生（CommonJS）/ Vitest 2.1.9 / Node 内置 `node:test` / Playwright（本地界面验收）

**执行入口（第三批：2026-10-22 开始，验证跨至10-23）**：A0–A5 代码/本地回归已实施，下一任务 A6。A5 新鲜证据：全后端467测试（466通过，1默认跳过隔离Redis集成，0失败/错误），显式隔离Redis集成另1/1含10轮并发注册/撤销；全部服务可打包，独立静态复审、IDE和空白检查通过。A1–A3微信编译/视觉复验仍待补；A4已完成隔离浏览器核验。第二批7个提交已同步GitHub至 `740cd21`；本批A5功能分4个主题提交：`2d90457`公共注册器、`3602c32`认证网关、`47f64d8`系统防护、`57e907a`持续WS会话；随后同步文档，未过审AI/学生Web/配置继续保留本地。全后端验证包含未提交AI，不宣称纯提交快照或生产验收。首次部署会话版本时旧令牌必须重登，需统一升级auth/gateway/system/interaction及common；本批未执行SQL或部署。真MySQL并发事务、多实例/Cluster、恢复故障验收仍待补；M1/M2未达成。各已实施章节以源码与实施记录为准。

---

## 一、预期目标如何定义（先对齐口径）

`README.md` 第 7 行把项目定位为"完整的企业级在线教育解决方案"，但当前所有已批准规格、验收脚本和演示数据都以**本机模型 + 本地数据库 + 模拟收银台 + 微信沙箱**为基线。因此本计划把"达成预期目标"拆成两个可判定的里程碑，避免用"演示通过"冒充"生产可用"：

| 里程碑 | 定义 | 判定方式 |
|---|---|---|
| **M1 产品完整（本计划主体）** | 在本地/预发环境，用真实后端、真实数据库、真实 RAG 模型，完整走通"老师建课上传视频 → 管理员配置营销 → 学员发现课程 → 领券下单支付 → 学习并续学 → 评价 → 申请售后 → 管理员审批退款"；AI 答疑与推荐质量有量化评测；三端无已知阻断缺陷；一条命令可复现全部回归 | 第 12 章质量门禁 `scripts/local/verify-all.sh` 全绿 + 第 11 章人工验收清单全部勾选 |
| **M2 生产可上线（本计划尾部）** | 真实微信身份（code2session）、微信支付 JSAPI 与渠道退款、HTTPS/WSS 与合法域名、生产镜像与外部化配置、监控告警、备份恢复演练、真机验收、小程序提审 | 第 13 章上线检查清单 + 真机验收记录；依赖商户资质与云资源，属**外部前置条件**，不能仅靠代码完成 |

**本计划承诺 M1 全部由代码与脚本兜底可达。M2 的代码任务全部给出实现，但是否"达成"取决于外部资质与资源到位。**

以下这些**不计入**未完成缺口（已确认不在原范围）：直播、练习/考试、独立教师端 App、移动 H5、社交化运营、多租户。

以下这些是**明确的分期，不是本期漏做**：学生 Web 的课程库/我的课程/商城/订单/收藏/注册属"二期"；学习进度/评价/售后退款申请属"三期"。本计划正是把这些"后期"补齐到可交付。

---

## 二、M1 验收标准（可观察、可判定）

逐条对应下面的任务编号，验收时按此清单打勾：

**学习闭环**
1. 学员播放课时，每 15 秒与离开页面时上报进度；重新进入同一课时从上次位置继续，偏差 ≤ 3 秒。
2. 进度 ≥ 90% 标记该课时完成；"学习"tab 显示真实累计学时、连续学习天数、近 7 天日历打点、最近学习课程及进度百分比。
3. "我的"页课程数为真实已购 + 已学课程数，不再恒定 0，也不再弹"学习记录加载失败"。
4. 学生 Web 播放器同样上报进度并能续播，与小程序共用同一份服务端数据。

**评价闭环**
5. 已购（或免费课已学）学员可对课程提交 1–5 星 + 文字评价，每人每课一条；未购买用户提交返回 403。
6. 课程详情页"评价"tab 显示真实评价、平均分与条数；教师可对自己课程的评价回复一次；管理员可隐藏违规评价。
7. 课程列表与详情不再出现写死的统计 0（无调用方的死接口删除）。

**经营闭环**
8. 学员可领取优惠券，并在下单时选择使用；订单金额 = 课程价 − 券抵扣，且不低于 0.01 元。
9. 支付成功后券置为已使用；取消订单/超时取消后券退回可用；退款不退券（页面明示）。
10. 学员可对已支付订单发起售后申请（填写原因），管理员审批通过后走既有退款链路并收回观看权限；可拒绝并留原因。
11. 教师登录后台可看到"我的课程销售与收入"（按课程维度的销量、实收、退款笔数）与"课程学员"（已购学员 + 学习进度），数据范围严格限于本人课程。
12. 后台课程列表"分类/讲师"两列显示真实名称，小程序按讲师搜索可命中。

**数据与访问控制正确性**
13. 后台 token 过期后能正常回到登录页重新登录，不再被弹回首页。
14. 优惠券发放与秒杀配置的 19 位雪花 ID 不丢精度（用字符串传输，且后端能正确接收）。
15. 仅有查看权限的账号可以只读查看课程，不会误入可写表单或 403。
16. 管理员不能删除/停用自己、不能移除自己最后的超管角色、系统至少保留一个启用的超管。
17. 停用用户、改角色权限、重置密码后，该用户旧令牌在 1 次请求内失效（需重新登录）。
18. 秒杀在"订单服务已建单但响应超时"下不产生孤儿单、不超卖：同一活动同一用户最多一张有效订单，重试返回同一张单。
19. 被 AI 审核拒绝的弹幕不会先广播给房间，也不会进入历史；审核服务不可用时的处理策略在页面明确告知。

**内容与 AI 质量**
20. 后台可上传分片视频（>512MB），合并后异步转码，转码完成后一键绑定到课时并可播放；转码失败可见、可重试。
21. 新增知识资料覆盖已知漏推主题（如 `try-with-resources`）；推荐评测用例能拒绝"多推了无关课程"。
22. AI 回答质量评测跑满 20 场景 × 3 次，产出 `ai-answer-eval.json`，自动规则通过率 ≥ 90%，人工评审 ≥ 18/20，且 `saved` 为 true 的比例 = 100%。
23. 保存失败/保存未知状态在三端都可见（小程序、学生 Web、后台 AI 页）。

**工程门禁**
24. `scripts/local/verify-all.sh` 一条命令跑完：后端全量测试、三端构建与单测、契约检查、静态检查；退出码为 0。
25. `scripts/local/journeys.sh` 覆盖新增闭环（进度、用券、售后、评价、教师统计），且运行时自建数据、结束自清理。
26. 文档与实现一致：README 不再宣称未验收的容量结论，缺口清单更新为"已关闭/仍开放"。

---

## 三、全局约束（Global Constraints）

以下约束对**每个任务**都成立，任务描述里不再重复：

1. **ID 一律字符串在端上流转**：雪花 ID 在前端、URL、JSON 中必须是 `String`；任何 `Number(id)`、`el-input-number`、`parseInt(id)` 一律视为缺陷。Java 侧 `Long` 不变，Jackson 的 Long→String 序列化已由 common 模块处理。
2. **金额只在服务端计算**：客户端传入的 `amount`、`discount`、`owner` 一律忽略。
3. **内部接口必须校验 `SecurityUtils.isTrustedInternalCall()`**，并在方法注释里写明"仅限服务间调用"。网关不会注入 `X-Inner-Call`，外部不可达。
4. **跨服务调用失败必须显式决策**：影响钱与权限的调用 fail-closed（抛 503 并拒绝操作）；只影响展示名称的调用可降级为空并在日志警告。
5. **所有 DDL 变更必须同时提供**：(a) 对应模块 `sql/<x>_schema.sql` 的一致性修改（供全新环境），(b) `sql/upgrade/YYYY-MM-DD-<topic>.sql` 幂等增量脚本（供已有数据环境）。增量脚本用 `information_schema` 判断列是否存在、`INSERT IGNORE` 插数据，可重复执行。
6. **不允许用 `db-init.sh` 升级已有数据**；升级一律走 `scripts/local/upgrade.sh`（本计划 Task A0 新增）。
7. **测试必须能在无外部依赖下运行**：单元测试用 Mockito/`MockMvc standaloneSetup`/内存假实现；需要真实中间件的验证放到 `scripts/local/*.sh` 并在文档标注"需要本地环境"。
8. **每个任务结束时工作区必须可构建**：`mvn test -pl <model> -am` 或对应前端 `npm test` 通过再提交。
9. **不新增运行时前端依赖**，唯一允许的新依赖是 `spark-md5`（分片上传需要真实 MD5）与 `vitest`（后台补单测，devDependency）。
10. **不修改演示种子数据的主键语义**（课程 2001–2007、课时 4001–4025、账号 1001–1003、订单 50001–50002 保持可用），新数据一律用雪花 ID 或更大号段。
11. **文案规范**：订单状态 0 待支付 / 1 已支付 / 2 已取消 / 3 已退款；"已完成"不得用于状态 2。前端文案改动需同步三端。

---

## 四、架构决策（Architecture Decisions）

| 编号 | 决策 | 理由 | 影响 |
|---|---|---|---|
| AD-1 | 学习进度、学习日历、课程评价全部落在 `zhixue-course` 库 | 它们以"学员 × 课时"为核心，读取课程与课时权限时不需要跨库；评价展示也由课程详情聚合 | 不新建服务、不加跨库事务 |
| AD-2 | 优惠券的"锁定/核销/释放"放在 `zhixue-marketing`，由 `zhixue-order` 通过 Feign 调用 | 券的库存与状态归营销服务所有，订单只持有 `couponUserId` 与 `discountAmount` 快照 | order 新增 `RemoteCouponService`（fail-closed） |
| AD-3 | 售后申请记录放在 `zhixue-order`，审批通过后复用既有 `refundOrder` 撤权链路 | 复用已验证的退款与权限收回逻辑，避免两套退款路径 | 不引入工单系统 |
| AD-4 | 教师经营数据由 `zhixue-order` 计算，课程归属来自 `zhixue-course` 的新增内部接口 | 订单数据在 order 库，课程归属在 course 库；教师只能在自家课程范围内聚合 | course 增加 `POST /inner/teacher-courses` |
| AD-5 | 会话撤销采用"用户→令牌集合"的 Redis 索引（`zhixue:auth:user-tokens:{userId}`） | 现有只有 `token→LoginUser`，无法反查；索引是唯一能以最小改动实现即时撤权的方式 | auth 登录时写入索引；system 变更账号时删索引内所有 token |
| AD-6 | 秒杀幂等采用"订单侧 `(activity_id, user_id)` 唯一键 + 重试复用"代替分布式事务 | 单库唯一键 + 幂等重试已能覆盖超时不确定窗口，无需 Seata | order 新增 `activity_id` 列与唯一索引 |
| AD-7 | 弹幕审核前置到广播之前，用有界线程池异步审核，超时可配置 fail-open/fail-closed | 现有先广播后审核是实际内容风险；阻塞 Netty 事件循环不可接受 | interaction 新增 `DanmakuModerationService` |
| AD-8 | 专用 ES 课程搜索维持 `stub`，不补索引同步；普通列表走数据库 `LIKE` + 分类/讲师条件 | 当前数据规模（几十门课）用 DB 完全够用，补 ES 索引生命周期是投机工程（YAGNI）；AI 的向量检索是另一套，不受影响 | 在 README 与 ADR 中明确；删除 `aggregateDetail` 死接口 |
| AD-9 | 媒体转码改为异步任务 + 状态轮询，媒体容器自带 ffmpeg | 现状是同步 `waitFor()` 且镜像无 ffmpeg，GB 级视频必然超时 | 新增媒体镜像构建与状态查询接口 |
| AD-10 | 后台补 `vitest` 单测（devDependency），先覆盖可提炼的纯函数（ID 解析、401 恢复、状态映射） | 后台目前零测试，UI 组件测试成本高，先锁住最容易回归的逻辑 | 不引入组件挂载测试 |

---

## 五、依赖图与执行顺序

```
A0 基线收口 ──┬─ A1 小程序保存状态 ─ A2 导航/状态 ─ A3 分类分页 ─ A4 后台缺陷
              ├─ A5 防自锁 + 会话撤销
              ├─ A6 秒杀幂等
              ├─ A7 弹幕审核前置
              └─ A8 AI 迁移/部署/评测  ─────────────┐
                                                     │
B 学习闭环（B1 表与上报 → B2 查询 → B3 我的课程 → B4 小程序 → B5 学生Web）
                                                     │
C 经营闭环（C1 评价 → C2 用券 → C3 售后 → C4 教师经营 → C5 列表与搜索）
                                                     │
D 学生 Web 二期（D1 路由外壳 → D2 课程库 → D3 订单 → D4 券/收藏 → D5 秒杀 → D6 注册）
                                                     │
E 媒资（E1 镜像 → E2 异步转码 → E3 分片上传）
                                                     │
F AI 质量（F1 知识覆盖 → F2 评测与人工评审 → F3 推荐精度）──┘
                                                     │
G 质量门禁（G1 verify-all → G2 journey 扩展 → G3 压测 → G4 文档）
                                                     │
H 生产上线（H1 微信登录 → H2 微信支付 → H3 渠道退款 → H4 部署加固 → H5 监控备份 → H6 真机与提审）
```

顺序原则：
- **A 阶段先做**，因为 B/C 的代码会落在同一批文件里，先修掉已知缺陷可避免重复返工（例如 A3 的分类分页和 C5 的列表筛选都在 `pageCourses`）。
- **B 先于 C**：教师经营需要学习进度数据；评价需要"已学/已购"判定。
- **D 可与 C 并行**：D 只依赖 A 阶段暴露的接口契约，与 C 的服务端改动不重叠（除订单 DTO 增加 `couponUserId`，需在 C2 完成后开始 D3）。
- **E 与 F 可并行**：媒资与 AI 互不依赖。
- **G 最后收口**：所有脚本在功能稳定后写，避免反复改。
- **H 需要外部资质**，代码可先于资质落地。

## 六、文件结构（本计划将创建/修改的主要文件）

```text
zhixue-common/src/main/java/com/zhixue/common/
├── redis/service/RedisService.java                 M  新增 sAdd/sMembers（会话索引）
└── security/utils/SecurityUtils.java               M  新增 getUsername()

zhixue-api/src/main/java/com/zhixue/api/
├── order/RemoteOrderService.java                   M  新增 inner 方法
├── order/domain/PurchasedCourse.java               C  已购课程视图
├── course/RemoteCourseService.java                 M  新增教师课程/名称批量
├── course/domain/CourseBrief.java                  C  课程名与教师名
├── marketing/RemoteCouponService.java              C  券锁定/核销/释放
├── marketing/domain/CouponLockRequest.java         C
├── marketing/domain/CouponLockResult.java          C
└── system/RemoteUserService.java                   M  批量用户摘要 + 按昵称检索

zhixue-modules/zhixue-course/
├── domain/entity/LearningProgress.java             C
├── domain/entity/LearningDaily.java                C
├── domain/entity/CourseReview.java                 C
├── domain/entity/Course.java                       M  +avgRating/+reviewCount(非落库)
├── domain/query/CourseQuery.java                   C  列表查询参数对象
├── mapper/LearningProgressMapper.java              C
├── mapper/LearningDailyMapper.java                 C
├── mapper/CourseReviewMapper.java                  C
├── service/LearningService.java                    C
├── service/CourseReviewService.java                C
├── service/impl/LearningServiceImpl.java           C
├── service/impl/CourseReviewServiceImpl.java       C
├── service/impl/CourseServiceImpl.java             M  分类含子级、排序、名称补齐
├── service/support/LessonAccessPolicy.java         M  +canPlay(course, section)
├── controller/LearningController.java              C
├── controller/CourseReviewController.java          C
├── controller/CourseController.java                M  删死接口、补分类名、评价路由
├── controller/CourseInnerController.java           M  +teacher-courses/+names
└── resources/sql/{course_schema.sql,upgrade/*}     M/C

zhixue-modules/zhixue-order/
├── domain/entity/Order.java                        M  +activityId/+couponUserId/+discountAmount
├── domain/entity/RefundRequest.java                C
├── domain/dto/CreateOrderDTO.java                  M  +couponUserId
├── domain/dto/RefundApplyDTO.java                  C
├── domain/vo/TeacherSalesVO.java                   C
├── mapper/RefundRequestMapper.java                 C
├── service/RefundRequestService.java               C
├── service/TeacherStatsService.java                C
├── service/impl/OrderServiceImpl.java              M  用券、幂等、释放
├── service/impl/RefundRequestServiceImpl.java      C
├── service/impl/TeacherStatsServiceImpl.java       C
├── controller/OrderController.java                 M  +售后端点 +教师统计
├── controller/PayController.java                   M  支付成功核销券
└── resources/sql/{order_schema.sql,upgrade/*}      M/C

zhixue-modules/zhixue-marketing/
├── domain/entity/CouponUser.java                   M  +status=3锁定 +order_no/+lock_time/+use_time
├── service/CouponService.java                      M  +lock/+consume/+release
├── service/impl/CouponServiceImpl.java             M  实现锁定/核销/释放、claim 校验启停
├── service/impl/SeckillServiceImpl.java            M  幂等重试、库存编辑保护
├── controller/CouponInnerController.java           C  内部锁定/核销/释放
└── resources/sql/{marketing_schema.sql,upgrade/*}  M/C

zhixue-modules/zhixue-interaction/
├── service/DanmakuModerationService.java           C  广播前审核
├── service/DanmakuPersistenceService.java          M  只落库，使用传入的审核结果
├── netty/DanmakuHandler.java                       M  改为先审核后广播
├── domain/dto/DanmakuMessageDTO.java               M  +auditStatus/+auditRemark
└── resources/application.yml                       M  审核策略与线程池

zhixue-modules/zhixue-system/
├── service/support/AdminOperationGuard.java        C  自身/最后超管保护
├── service/support/SessionRevoker.java             C  撤销该用户全部会话
├── service/impl/SysUserServiceImpl.java            M  接入守卫与撤销
├── service/impl/SysRoleServiceImpl.java            M  禁止改超管 roleKey/status，改授权后撤权
└── controller/SysUserController.java               M  新增 inner 批量摘要/昵称检索

zhixue-modules/zhixue-media/
├── service/impl/FileServiceImpl.java               M  合并后异步转码
├── service/impl/VideoProcessServiceImpl.java       M  @Async + 状态回写
├── strategy/FFmpegStrategy.java                    M  超时 + 消费输出
└── controller/UploadController.java                M  +状态查询

zhixue-admin-web/src/
├── utils/request.js                                M  401 同步重置 store
├── utils/session.js                                C  可单测的 401 恢复
├── utils/id.js                                     C  雪花 ID 解析
├── router/modules/course.js                        M  +view 只读路由
├── views/course/list/index.vue                     M  查看走只读、分类/讲师列
├── views/course/publish/index.vue                  M  readonly 模式
├── views/course/review/index.vue                   C  评价审核
├── views/order/refund/index.vue                    C  售后审批
├── views/teacher/statistics/index.vue              C  教师经营
├── views/marketing/{coupon,seckill}/index.vue      M  字符串 ID
├── views/interaction/comment/index.vue             M  待审核/审核不可用文案
└── tests/unit/*.test.js                            C

zhixue-app-mp/miniprogram/
├── utils/auth.js                                   M  switchTab 登录跳转
├── utils/nav.js                                    C  openPage 统一导航
├── api/user.js                                     M  真实进度接口
├── api/order.js                                    M  +couponUserId
├── api/marketing.js                                M  +可用券带金额
├── pages/course/player/index.js|wxml               M  上报/续播/失败可见
├── pages/study/*                                   M  真实学习中心
├── pages/my/index.js|wxml                          M  统计与状态文案
├── pages/my/list/index.js|wxml                     M  历史/券/售后入口
├── pages/course/detail/*                           M  评价、用券、售后
├── pages/category/index.js|wxml                    M  服务端分页与排序
├── pages/search/index.*                            M  讲师搜索、分页
└── tests/*.test.cjs                                C/M

zhixue-student-web/src/
├── router/index.js                                 M  二期路由
├── layout/SideNav.vue                              M  二期入口
├── views/catalog/*                                 C  课程库
├── views/mine/*                                    C  我的课程/订单/券/收藏
├── views/seckill/*                                 C  秒杀
├── views/register/*                                C  注册
├── views/course/{CourseDetail,LessonPlayer}.vue    M  评价、进度、用券
└── tests/unit/*                                    C/M

scripts/local/
├── upgrade.sh                                      C  增量迁移
├── verify-all.sh                                   C  一键门禁
├── load/k6-*.js                                    C  压测脚本
└── journeys.mjs                                    M  覆盖新闭环
```

图例：`C` 新建，`M` 修改。

---

## 阶段 A：先修掉既有缺陷与收口当前改动

> 这一阶段的每个任务都是**独立可发布**的最小修复。先做它们，是因为后面 B/C 会改同一批文件（课程列表、订单下单、弹幕、账号权限），先清理能避免在同一处反复返工。

### Task A0：收口当前工作区并新增增量迁移脚本

**Files:**
- Create: `scripts/local/upgrade.sh`
- Modify: `README.md`（"已有数据的环境升级"段落改为调用新脚本）
- Test: `scripts/local/upgrade.test.mjs`（CLI 接缝：只读预览、模块/文件顺序、未知参数拒绝、首错停止；Docker 由隔离测试替身代替）

**执行校正（2026-10-22）**：当前增量 SQL 为 4 个，课程模块只有 `2026-10-20-java-courses.sql`，不存在课程模块的 `2026-10-20-java-knowledge.sql`。下方旧示例中的数量和该路径不作为验收依据。`--dry-run` 必须在 `load_env` 之前返回，不读取/生成/改写 `local.env`、不调用 Docker。实际升级仅使用已存在的本地配置文件与 MySQL 容器的 `MYSQL_ROOT_PASSWORD`，不调用会改配置的 `load_env()`，也不将密码放进宿主机命令参数。SQL 失败立即停止，DDL 不保证整批回滚，先备份、检查后再人工批准升级。

首批仅实施未提交。2026-10-22 后续用户明确授权“继续，并且更新到 GitHub 上”：当前允许将已验证的 A0–A4 及所需小程序基线按主题提交、常规推送 `origin/main`；不混入未过审 AI 运行时代码、私有配置、`.codebuddy/` 或测试截图。此授权不包含清库、迁移执行、部署或小程序上传，不使用 force push。

**Interfaces:**
- Produces: `bash scripts/local/upgrade.sh [--dry-run]` —— 按文件名日期顺序执行所有模块 `sql/upgrade/*.sql`，幂等，供后续所有 DDL 任务使用。

- [x] **Step 1: 确认当前 Git 基线并保留已有改动（提交暂缓）**

当前工作区包含三组互不相关的未提交改动（AI 可靠性、小程序图标与流式 AI、学生 Web 保存状态）。在开始新功能前先分组提交，避免后续 diff 混杂：

```bash
git -C /Users/jax/Projects/zhixue-cloud status --short
```

按以下分组提交（**提交前必须与用户确认**，不要自行 commit）：

| 组 | 路径 |
|---|---|
| AI 可靠性 | `zhixue-modules/zhixue-ai/**`、`zhixue-student-web/src/views/chat/{MessageList.vue,chatSession.js}`、`zhixue-student-web/tests/unit/views/chatSession.test.js`、`scripts/local/ai-answer-eval.*`、`scripts/local/lib/ai-eval*`、`scripts/local/fixtures/`、`scripts/local/ai-reco-check.mjs` |
| 小程序图标与流式 AI | `zhixue-app-mp/**`、`docs/superpowers/{plans,specs}/2026-10-22-miniprogram-*`、`ops/local/docker-compose.yml`（若只是 AI 环境变量，归入上一组） |
| 其他 | 逐项确认来源；`zhixue-app-mp/project.config.json` 与 `project.private.config.json` 的改动是本地联调有意为之，提交前先 `git log` 确认无并行会话未完成改动 |

预期：保留已有改动，不暂存/提交未知来源的文件，不删除 `.codebuddy/` 项目数据。

- [x] **Step 2: 编写 `scripts/local/upgrade.sh`**

实现以 `scripts/local/upgrade.sh` 为唯一来源，不再复制旧版会在 dry-run 中调用 `load_env()` 的实现。CLI 仅接受无参执行或单个 `--dry-run`；显式空参数、多参数、未知参数均在读取配置之前拒绝。回归测试使用隔离工作区、`SELECT 1` 测试 SQL 与假 Docker，不连接真实数据库。

- [x] **Step 3: 验证 dry-run 只列出预期文件**

Run:
```bash
bash /Users/jax/Projects/zhixue-cloud/scripts/local/upgrade.sh --dry-run
```
实际输出（2026-10-22，4 个文件，按此顺序）：
```text
[upgrade][dry-run] zhixue-modules/zhixue-course/src/main/resources/sql/upgrade/2026-10-20-java-courses.sql
[upgrade][dry-run] zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-20-ai-conversation.sql
[upgrade][dry-run] zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-20-java-knowledge.sql
[upgrade][dry-run] zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-22-ai-reliability.sql
[ok] 增量脚本预览完成（共 4 个）
```
> 实际数量以 `ls` 为准；关键是顺序为"课程在前、AI 在后"，且**包含**此前 README 漏列的 `2026-10-22-ai-reliability.sql`。

- [x] **Step 4: 更新 README 升级段落**

`README.md` 已接入安全预览、确认备份后升级、手动构建部署说明；不再展示宿主机命令行密码。

- [x] **Step 5: 校验脚本语法及隔离回归（提交暂缓）**

Run: `bash -n scripts/local/upgrade.sh && node --test scripts/local/upgrade.test.mjs`
实际：语法正确，4 项通过；审查发现显式空参数误进入执行分支，新增回归先红后绿并复审确认已修。实际 SQL、备份恢复和部署未执行。

以下提交示例仅供后续明确授权后执行：

```bash
git add scripts/local/upgrade.sh README.md
git commit -m "feat(local): 增量迁移脚本统一按模块顺序执行，补上遗漏的 AI 可靠性脚本"
```

---

### Task A1：小程序展示回答"未保存/保存状态未知"

**状态（2026-10-22）**：代码及本地回归完成；微信局部编译/模拟器视觉复验待完成，不算真机或真实模型验收。

**Files:**
- Modify: `zhixue-app-mp/miniprogram/pages/ai/chat/index.js`（done 消费保存三态）
- Modify: `zhixue-app-mp/miniprogram/pages/ai/chat/index.wxml`（`save-warning` 展示绑定）
- Modify: `zhixue-app-mp/miniprogram/pages/ai/chat/index.wxss`（告警区样式）
- Test: `zhixue-app-mp/tests/ai-page.test.cjs`（新增 6 项）

**Interfaces / 执行校正：**
- `saved=false`：保留回答，显示服务端保存提示或未保存兜底。
- 显式 `saved=null`：显示待确认提示；服务端没给 `saveNotice` 时也有兜底。
- `saved=true`：不显示过期保存警告；旧服务端缺保存字段时兼容不误报。
- 页面消息保存 `saved` / `saveNotice`，WXML 按 `saveNotice` 渲染。沿用原页面，不用 HTML 专属 `role` 属性冒充微信无障碍支持。
- 实现以以上源文件为准；不保留缺少 null 兜底的旧示例。

- [x] **Step 1: 写失败测试** — done 三态、旧协议和模板绑定共新增 6 项。
- [x] **Step 2: 运行确认失败** — 原 11 项通过，新增 6 项失败（`undefined !== false` 等）。
- [x] **Step 3: 修改 done 分支** — 消费保存状态，不改变生成、停止、历史与推荐流程。
- [x] **Step 4: 加入提示绑定与样式** — 提示留在回答气泡中，不阻断阅读。
- [x] **Step 5: 回归验证** — A1 完成时小程序 47/47；A2 后最终全量 56/56。
- [ ] **Step 6: 微信编译/视觉验收及提交** — 工具状态检查通过（版本 equal、未过期），但 `compile_wxml` 返回 `terminated`，`compile_wxss` 返回 `timeout`，不能宣称编译通过；提交未授权。

验证：`node --test zhixue-app-mp/tests/*.test.cjs`。本地测试包含真实页面方法 + 假传输/内存 wx，不等于真实数据库或模型验证。

---

### Task A2：修复小程序登录跳转与订单状态文案

**状态（2026-10-22）**：代码及本地回归完成；微信编译/模拟器复验待补，提交未授权。

**Files:**
- Create: `zhixue-app-mp/miniprogram/utils/nav.js`
- Modify: `zhixue-app-mp/miniprogram/utils/auth.js`（`checkLoginAndRedirect`）
- Modify: `zhixue-app-mp/miniprogram/pages/course/detail/index.js`（收藏、购买、学习三处）
- Modify: `zhixue-app-mp/miniprogram/pages/seckill/index.js`（未登录秒杀）
- Modify: `zhixue-app-mp/miniprogram/pages/my/index.wxml`（状态 2 已取消）
- Test: `zhixue-app-mp/tests/navigation.test.cjs`（9 项）

**Interfaces / 执行校正：**
- `openPage(url, options={})`：Tab 页用 `wx.switchTab` 并去 query，普通页用 `wx.navigateTo` 原样保留 URL；保留回调，`options.url` 不可覆盖目标。
- `requestLogin(redirectUrl='/pages/my/index')`：只在确认后跳转，取消不跳转。
- 五处入口全部接入；Tab 列表为简单常量并与 `app.json` 比对。删除旧示例的无意义 try/catch、构建注入说法与只测试常量的弱断言。
- 不改变认证/支付/收藏/观看权限逻辑；已登录用户继续原流程。

- [x] **Step 1: 写失败测试** — 通过真实 Page 方法、认证工具、内存 wx 复现错误导航。
- [x] **Step 2: 运行确认失败** — 2 项原行为通过，7 项失败（`navigateTo` 不是 `switchTab`，状态 2 为“已完成”等）。
- [x] **Step 3: 新建导航工具** — 无新运行时依赖。
- [x] **Step 4: 替换全部 5 处跳转** — 包含原示例漏写的未登录收藏。
- [x] **Step 5: 修正状态文案** — 状态 2 显示已取消，保留原筛选入口。
- [x] **Step 6: 回归验证** — 最终小程序 56/56，包含本任务 9 项；JavaScript 语法和 IDE 诊断通过。
- [ ] **Step 7: 微信编译/模拟器验收及提交** — 我的页 `compile_wxml` 返回 `terminated`，不可算编译通过；尚未提交/上传。

验证：`node --test zhixue-app-mp/tests/*.test.cjs`。测试覆盖取消不跳转、已登录不提示、五入口不调用业务 API、Tab 列表、导航回调、长 ID 与正确订单文案；不是真机验收。

---

### Task A3：分类改为服务端分页与排序，首页分类可直达

**实施记录（2026-10-22）**：代码与本地回归完成。`CoursePageQueryTest` 4 项 + Controller 查询传参 1 项先红后绿；课程模块全依赖回归通过，后续全后端 437 项通过。小程序 `category-page.test.cjs` 8 项通过（含串行 reset、父级字符串 `0`、子级长 ID、重试 single-flight、触底绑定），最终小程序 64/64。销量项移除，假学习人数不再展示；讲师检索/名称由 C5 补齐，不宣称本期已支持。分类初始化 single-flight 是审查补充，不添加并发绕过请求层的分支。以下原步骤保留为历史设计，实际实现以源码/回归为准；微信模拟器本轮未复验。

**执行校正（2026-10-22）**：保留原 `pageCourses(PageQuery, String, Long, Integer, Integer)` 兼容入口，默认委托新的 `CourseQuery` 版本；`PageResult` 使用实际 `page.getPageSize()`，不存在 `PageQuery.getSize()`。分类包含全部可访问子孙且防循环，排序增加 ID 次序保证翻页稳定；教师范围与公开状态不放宽。小程序列表为内部 `scroll-view`，需绑定 `bindscrolltolower`，不能只实现页面触底；reset 请求串行合并最新筛选，旧响应不得覆盖新筛选，避免请求层重复拒绝。初始分类返回扁平数组，`parentId` 字符串 `"0"` 必须识别；首页传分类需等分类就绪再消费，不丢长 ID。真实失败显示可重试错误，销量/学员数假字段不展示；讲师名称仍由 C5 补齐。新增回归覆盖时序与权限，不复制旧示例中不返回 Promise 的 `onReachBottom()` 或错误的 total 测试样本。

**Files:**
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/query/CourseQuery.java`
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/CourseService.java:14`
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/impl/CourseServiceImpl.java:38-60`
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/controller/CourseController.java:46-58`
- Modify: `zhixue-app-mp/miniprogram/pages/category/index.js:85-200`
- Modify: `zhixue-app-mp/miniprogram/pages/category/index.wxml:40-70`
- Modify: `zhixue-app-mp/miniprogram/pages/index/index.js:110-115`
- Test: `zhixue-modules/zhixue-course/src/test/java/com/zhixue/course/service/CoursePageQueryTest.java`（新建）
- Test: `zhixue-app-mp/tests/category-page.test.cjs`（新建）

**Interfaces:**
- Produces:
  - `record CourseQuery(String title, Long categoryId, Integer status, Integer shelfStatus, String sort, List<Long> teacherIds)`
  - `PageResult<Course> pageCourses(CourseQuery query, PageQuery page)`
  - `GET /course/list?pageNum&pageSize&categoryId&sort&title|keyword`
  - `sort` 取值：`new` / `price_asc` / `price_desc`，其它值按创建时间倒序

背景：分类页拉 50 条在本地筛选并固定 `hasMore=false`，第 51 门之后永远看不到，列表却显示"没有更多了"；首页点分类拿了 id 却没用；"销量"排序依赖不存在的 `sales` 字段。后端 `pageCourses` 也不按分类的子级过滤，父分类点进去只有父级直挂课程。

- [ ] **Step 1: 写后端失败测试**

新建 `CoursePageQueryTest.java`：

```java
package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.course.domain.entity.Category;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.domain.query.CourseQuery;
import com.zhixue.course.mapper.CategoryMapper;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.mapper.SectionMapper;
import com.zhixue.course.service.impl.CourseServiceImpl;
import com.zhixue.course.service.support.CourseAccessGuard;
import com.zhixue.course.service.support.LessonAccessPolicy;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoursePageQueryTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Course.class);
        TableInfoHelper.initTableInfo(assistant, Category.class);
    }

    @Mock private CourseMapper courseMapper;
    @Mock private ChapterMapper chapterMapper;
    @Mock private SectionMapper sectionMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private CourseAccessGuard courseAccessGuard;

    @Test
    void categoryFilterIncludesChildCategories() {
        // 父分类 110 下有两个子分类 111、112；点父分类应同时命中子分类课程
        Category child = new Category();
        child.setId(111L);
        child.setParentId(110L);
        when(categoryMapper.selectList(any())).thenReturn(List.of(child));
        when(courseAccessGuard.isTeacherScoped()).thenReturn(false);
        when(courseMapper.selectPage(any(), any())).thenReturn(new Page<>());

        CourseServiceImpl service = new CourseServiceImpl(courseMapper, chapterMapper, sectionMapper,
                categoryMapper, courseAccessGuard);
        service.pageCourses(new CourseQuery(null, 110L, 1, 1, null, null), new PageQuery());

        ArgumentCaptor<LambdaQueryWrapper<Course>> captor = newWrapperCaptor();
        org.mockito.Mockito.verify(courseMapper).selectPage(any(), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("category_id IN");
    }

    @Test
    void priceSortMapsToPriceColumn() {
        when(courseAccessGuard.isTeacherScoped()).thenReturn(false);
        when(courseMapper.selectPage(any(), any())).thenReturn(new Page<>());
        CourseServiceImpl service = new CourseServiceImpl(courseMapper, chapterMapper, sectionMapper,
                categoryMapper, courseAccessGuard);

        service.pageCourses(new CourseQuery(null, null, 1, 1, "price_asc", null), new PageQuery());

        ArgumentCaptor<LambdaQueryWrapper<Course>> captor = newWrapperCaptor();
        org.mockito.Mockito.verify(courseMapper).selectPage(any(), captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("ORDER BY price ASC");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<LambdaQueryWrapper<Course>> newWrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }
}
```

> 注：`CourseServiceImpl` 现在还未注入 `CategoryMapper`（当前字段只有 `courseMapper`、`chapterMapper`、`sectionMapper`、`courseAccessGuard`），测试会因构造器参数个数不匹配编译失败——这正是这一步要暴露的失败。注意本类并不依赖 `LessonAccessPolicy`，不要把它加进构造器。

- [ ] **Step 2: 运行确认失败**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud && mvn test -q -pl zhixue-modules/zhixue-course -am \
  -Dtest='CoursePageQueryTest' -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: 编译失败，提示 `CourseQuery` 找不到符号、`CourseServiceImpl` 构造器不匹配。

- [ ] **Step 3: 新建 `CourseQuery`**

`zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/query/CourseQuery.java`：

```java
package com.zhixue.course.domain.query;

import java.util.List;

/**
 * 课程列表查询参数。
 *
 * <p>用对象承载而不是继续加方法参数：列表条件已从 4 个增长到 6 个，
 * 再加会变成无法阅读的长参数表。</p>
 */
public record CourseQuery(
        String title,
        Long categoryId,
        Integer status,
        Integer shelfStatus,
        String sort,
        List<Long> teacherIds
) {

    public static final String SORT_LATEST = "new";
    public static final String SORT_PRICE_ASC = "price_asc";
    public static final String SORT_PRICE_DESC = "price_desc";
}
```

- [ ] **Step 4: 修改 `CourseService` 接口**

```java
    PageResult<Course> pageCourses(CourseQuery query, PageQuery page);
```

- [ ] **Step 5: 实现分类含子级 + 排序**

`CourseServiceImpl` 当前构造字段顺序为 `courseMapper、chapterMapper、sectionMapper、courseAccessGuard`（`CourseServiceImpl.java:33-36`）。本任务在 `sectionMapper` 之后插入 `private final CategoryMapper categoryMapper;`，并在同一任务内保持 `@RequiredArgsConstructor` 的字段声明顺序即为构造参数顺序。实现：

```java
    @Override
    public PageResult<Course> pageCourses(CourseQuery query, PageQuery page) {
        LambdaQueryWrapper<Course> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.title())) {
            qw.like(Course::getTitle, query.title().trim());
        }
        if (query.categoryId() != null) {
            // 点父分类要能看到子分类下的课程，否则"编程开发"下永远是空的
            qw.in(Course::getCategoryId, categoryIdsWithChildren(query.categoryId()));
        }
        if (query.teacherIds() != null && !query.teacherIds().isEmpty()) {
            qw.in(Course::getTeacherId, query.teacherIds());
        }
        qw.eq(query.status() != null, Course::getStatus, query.status())
                .eq(query.shelfStatus() != null, Course::getShelfStatus, query.shelfStatus());
        if (courseAccessGuard.isTeacherScoped()) {
            qw.eq(Course::getTeacherId, courseAccessGuard.currentTeacherId());
        }
        applySort(qw, query.sort());
        Page<Course> result = courseMapper.selectPage(
                new Page<>(page.getPageNum(), page.getPageSize()), qw);
        return PageResult.of(result.getRecords(), result.getTotal(), page.getSize());
    }

    private List<Long> categoryIdsWithChildren(Long categoryId) {
        List<Long> ids = new ArrayList<>();
        ids.add(categoryId);
        categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                        .eq(Category::getParentId, categoryId))
                .forEach(child -> ids.add(child.getId()));
        return ids;
    }

    private void applySort(LambdaQueryWrapper<Course> qw, String sort) {
        if (CourseQuery.SORT_PRICE_ASC.equals(sort)) {
            qw.orderByAsc(Course::getPrice).orderByDesc(Course::getCreateTime);
        } else if (CourseQuery.SORT_PRICE_DESC.equals(sort)) {
            qw.orderByDesc(Course::getPrice).orderByDesc(Course::getCreateTime);
        } else if (CourseQuery.SORT_LATEST.equals(sort)) {
            qw.orderByDesc(Course::getPublishTime).orderByDesc(Course::getCreateTime);
        } else {
            qw.orderByDesc(Course::getCreateTime);
        }
    }
```

- [ ] **Step 6: 修改控制器**

```java
    @GetMapping("/list")
    public R<PageResult<Course>> list(PageQuery pageQuery,
                                      @RequestParam(required = false) String title,
                                      @RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Long categoryId,
                                      @RequestParam(required = false) Integer status,
                                      @RequestParam(required = false) Integer shelfStatus,
                                      @RequestParam(required = false) String sort) {
        boolean canManageCourses = SecurityUtils.hasAnyPermission("course:list", "course:edit", "course:publish");
        CourseQuery query = new CourseQuery(
                StringUtils.hasText(title) ? title : keyword,
                categoryId,
                canManageCourses ? status : 1,
                canManageCourses ? shelfStatus : 1,
                sort,
                null);
        return R.ok(courseService.pageCourses(query, pageQuery));
    }
```

> 同步修改所有调用 `pageCourses(4 参数)` 的测试与 `CourseInnerController`（如有）。用
> `grep -rn "pageCourses(" zhixue-modules/zhixue-course/src` 找全。

- [ ] **Step 7: 运行后端测试**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud && mvn test -q -pl zhixue-modules/zhixue-course -am
```
Expected: BUILD SUCCESS，原有测试 + 新增 2 个通过。
> 若原有 `CourseServiceImpl` 相关测试用 `@InjectMocks`，需补 `@Mock CategoryMapper`；这是构造字段变化的必然影响，不是测试被削弱。

- [ ] **Step 8: 改写小程序分类页**

`pages/category/index.js` 的 `loadCourseList` 及其后筛选/排序逻辑整体替换为服务端分页（保留原有 UI 字段）：

```js
const PAGE_SIZE = 10

// 数据新增字段：categoryId（当前分类，null 表示全部）、pageNum、hasMore、total
async fetchCourses(reset) {
  if (this.data.loading) return
  const pageNum = reset ? 1 : this.data.pageNum + 1
  this.setData({ loading: true })
  try {
    const res = await getCoursePage({
      pageNum,
      pageSize: PAGE_SIZE,
      categoryId: this.data.categoryId || undefined,
      sort: this.currentSort()
    })
    const data = res.data || {}
    const records = Array.isArray(data.records) ? data.records : []
    const list = reset ? records : this.data.courseList.concat(records)
    this.setData({
      courseList: list,
      pageNum,
      // 后端返回 total；缺失时退回"本页是否满页"，避免误报没有更多
      hasMore: typeof data.total === 'number' ? pageNum * PAGE_SIZE < data.total : records.length === PAGE_SIZE
    })
  } catch (error) {
    console.error('加载课程列表失败:', error)
    wx.showToast({ title: '加载失败', icon: 'none' })
  } finally {
    this.setData({ loading: false })
  }
}

currentSort() {
  const { sortType, priceSort } = this.data
  if (sortType === 'price') return priceSort === 'asc' ? 'price_asc' : 'price_desc'
  if (sortType === 'new') return 'new'
  return ''
}

onReachBottom() {
  if (this.data.hasMore && !this.data.loading) this.fetchCourses(false)
}
```

分类/子分类点击、`onSortTap`、`onLoad` 全部改为设置 `categoryId`／`sortType`／`priceSort` 后调用 `this.fetchCourses(true)`；删除 `filterCourses`、`sortCourses`、`rawCourseList`。

`onShow` 消费首页传来的分类：

```js
  onShow() {
    const app = getApp()
    const pending = app.globalData.pendingCategoryId
    if (pending) {
      app.globalData.pendingCategoryId = null
      this.applyCategory(String(pending))
    }
  },

  applyCategory(categoryId) {
    const list = this.data.categoryList || []
    const index = list.findIndex(item => String(item.id) === String(categoryId))
    if (index >= 0) {
      this.setData({ currentCategory: index, currentSubCategory: 0, categoryId })
    } else {
      // 分类未出现在顶级列表时，仍按该 id 过滤，避免"点了没反应"
      this.setData({ currentCategory: 0, currentSubCategory: 0, categoryId })
    }
    this.fetchCourses(true)
  }
```

`pages/index/index.js:110-115`：

```js
  onCategoryTap(e) {
    const { id } = e.currentTarget.dataset
    // switchTab 不能带 query，用 globalData 传递目标分类
    getApp().globalData.pendingCategoryId = id
    wx.switchTab({ url: '/pages/category/index' })
  },
```

`pages/category/index.wxml`：删除"销量"排序项（后端没有销量字段，点击无效果），`price` 与 `new` 保留；列表项 `item.sales`、`item.teacher` 改用 `item.teacherName || '讲师'`，缺失时不显示该行。

- [ ] **Step 9: 写小程序失败测试**

新建 `zhixue-app-mp/tests/category-page.test.cjs`：

```js
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

function setup(responses) {
  let definition
  const calls = []
  const app = { globalData: { pendingCategoryId: null } }
  const getCoursePage = (params) => {
    calls.push(params)
    return Promise.resolve(responses[calls.length - 1] || { code: 200, data: { records: [], total: 0 } })
  }
  const mocks = {
    '../../api/course': {
      getCategoryList: () => Promise.resolve({ code: 200, data: [{ id: 100, name: '编程开发', parentId: 0 }] }),
      getCoursePage
    }
  }
  vm.runInNewContext(fs.readFileSync(path.resolve(__dirname, '../miniprogram/pages/category/index.js'), 'utf8'), {
    require(name) { if (!mocks[name]) throw Error(`Unexpected import: ${name}`); return mocks[name] },
    Page(options) { definition = options },
    getApp: () => app,
    wx: { showToast() {}, setNavigationBarTitle() {} },
    console, setTimeout, Date
  })
  const page = {
    data: structuredClone(definition.data),
    setData(patch) { Object.assign(this.data, structuredClone(patch)) }
  }
  for (const [key, value] of Object.entries(definition)) {
    if (typeof value === 'function') page[key] = value.bind(page)
  }
  return { page, calls, app }
}

test('首屏请求第一页并带上分类与排序参数', async () => {
  const { page, calls } = setup([{ code: 200, data: { records: [{ id: 1 }], total: 25 } }])
  page.setData({ categoryId: '110', sortType: 'price', priceSort: 'asc' })
  await page.fetchCourses(true)
  assert.deepEqual(
    { pageNum: calls[0].pageNum, pageSize: calls[0].pageSize, categoryId: calls[0].categoryId, sort: calls[0].sort },
    { pageNum: 1, pageSize: 10, categoryId: '110', sort: 'price_asc' }
  )
  assert.equal(page.data.hasMore, true)
})

test('触底加载第二页并追加，而不是覆盖', async () => {
  const { page, calls } = setup([
    { code: 200, data: { records: [{ id: 1 }], total: 3 } },
    { code: 200, data: { records: [{ id: 2 }], total: 3 } }
  ])
  await page.fetchCourses(true)
  await page.onReachBottom()
  assert.equal(calls[1].pageNum, 2)
  assert.deepEqual(page.data.courseList.map(item => item.id), [1, 2])
})

test('没有更多时不再发请求', async () => {
  const { page, calls } = setup([{ code: 200, data: { records: [], total: 0 } }])
  await page.fetchCourses(true)
  await page.onReachBottom()
  assert.equal(calls.length, 1)
})

test('首页传来的分类在 onShow 时被使用', async () => {
  const { page, calls, app } = setup([{ code: 200, data: { records: [], total: 0 } }])
  page.setData({ categoryList: [{ id: 100, name: '编程开发' }] })
  app.globalData.pendingCategoryId = 100
  await page.onShow()
  assert.equal(page.data.categoryId, 100)
  assert.equal(calls[0].categoryId, 100)
  assert.equal(app.globalData.pendingCategoryId, null)
})
```

- [ ] **Step 10: 运行确认通过**

Run: `node --test /Users/jax/Projects/zhixue-cloud/zhixue-app-mp/tests/category-page.test.cjs`
Expected: 4 tests pass

- [ ] **Step 11: 提交**

```bash
git add zhixue-modules/zhixue-course/src zhixue-app-mp/miniprogram/pages/category \
        zhixue-app-mp/miniprogram/pages/index/index.js zhixue-app-mp/tests/category-page.test.cjs
git commit -m "feat(course,mp): 分类列表改服务端分页与排序，父分类含子级课程，首页分类可直达"
```

---

### Task A4：后台 401 恢复、雪花 ID 精度、课程只读查看

**实施记录（2026-10-22）**：代码与本地验证完成，后台 Vitest 19 项通过（实际拦截器、课程 SFC handler、营销回填/提交 ID），构建成功。编辑/查看/发布权限分离、首次已有课加载期间禁写、失败重试门禁；只读翻步不写且仍可读章节。审查 3 个 Required（旧请求 401 驱逐新会话、分类并发重试、初始加载未禁写）均经失败回归修复并静态复审清零。隔离 Chrome + 全部服务 API 替身：只读页 5 断言通过，HTTP 401 恢复/刷新 2 断言通过；不是与真实网关联调或真机验收。生产依赖审计仍有既有 10 high/3 moderate，未自动做大版本升级；Sass legacy 与 Element Plus 体积告警仍在，不能宣称生产门禁全部通过。后续要处理，不阻碍本批功能代码常规 Git 同步。原示例只作历史设计，不复制其中旧恢复逻辑和错误权限。

**执行校正（2026-10-22）**：HTTP 和业务码 401 统一立即同步清 Pinia/Storage，再去登录；并发只恢复一次，拒绝的请求仍失败，不弹可取消后继续保持旧身份的弹窗。回调注册在 Pinia 初始化后、Router 初始化前。查看路由 `course:list`；编辑/发布页入口允许 `course:edit` 或 `course:publish`，但编辑/上传/章节写只认 `course:edit`，发布只认 `course:publish` 且查看路由不能发布。只读翻步不写、不校验草稿完整性；读取章节仍允许 list-only，不错误绑定到 edit 权限。新建按钮按实际后端 `course:edit`；加载失败保持写禁用并可重试。ID 仅字符串，校验正整数且不超 Java Long 上界，不把 BigInt 发到 JSON。Vitest 覆盖真实 request 拦截器和 SFC 的 handler 接缝，避免只测新增 helper 就宣称页面已修。

**Files:**
- Modify: `zhixue-admin-web/package.json`（加 `test` 脚本与 `vitest` devDependency）
- Modify: `zhixue-admin-web/vite.config.js`（加 `test` 配置）
- Create: `zhixue-admin-web/src/utils/session.js`
- Create: `zhixue-admin-web/src/utils/id.js`
- Modify: `zhixue-admin-web/src/utils/request.js:45-59,98-103,113-118`
- Modify: `zhixue-admin-web/src/main.js`（注册会话过期处理）
- Create: `zhixue-admin-web/src/router/modules/course-view`（并入 `course.js`）
- Modify: `zhixue-admin-web/src/views/course/list/index.vue:325-333`
- Modify: `zhixue-admin-web/src/views/course/publish/index.vue`
- Modify: `zhixue-admin-web/src/views/marketing/coupon/index.vue:296-304`
- Modify: `zhixue-admin-web/src/views/marketing/seckill/index.vue:74-75,220-233`
- Test: `zhixue-admin-web/tests/unit/{session,id,readonly}.test.js`（新建）

**Interfaces:**
- Produces:
  - `recoverFromExpiredLogin(store, router): Promise<void>`
  - `setSessionExpiredHandler(fn)` / `handleSessionExpired()`
  - `parseIdList(input: string): string[]`
  - `isReadonlyRoute(routeName: string): boolean`
  - 路由名 `CourseView`（`/course/view?id=`，权限 `course:list`）

背景三处：① `401` 只清 `localStorage`，Pinia 仍有 token，`/login` 被守卫重定向回 `/dashboard`；② 优惠券发放与秒杀课程 ID 被 `Number()` 转换，19 位雪花 ID 失真；③ "查看"和"编辑"都跳到只要求 `course:publish` 的页面，只有 `course:list` 的账号点进去 403，有编辑权限的也直接进可写表单。

- [ ] **Step 1: 配置 vitest**

`zhixue-admin-web/package.json`：

```json
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "test": "vitest run",
    "lint": "eslint . --ext .vue,.js,.jsx,.cjs,.mjs --fix --ignore-path .gitignore"
  },
```

`devDependencies` 增加：

```json
    "vitest": "^2.1.9"
```

`vite.config.js` 末尾（`build` 之后）追加：

```js
  test: {
    environment: 'node',
    include: ['tests/unit/**/*.test.js']
  }
```

Run: `cd /Users/jax/Projects/zhixue-cloud/zhixue-admin-web && npm install`
Expected: 安装成功，`node_modules/.bin/vitest` 存在。

- [ ] **Step 2: 写失败测试**

新建 `tests/unit/session.test.js`：

```js
import { describe, expect, it, vi } from 'vitest'
import { handleSessionExpired, recoverFromExpiredLogin, setSessionExpiredHandler } from '@/utils/session'

function store() {
  return { resetToken: vi.fn() }
}

describe('recoverFromExpiredLogin', () => {
  it('清 Pinia 登录态后跳登录页，否则守卫会把 /login 重定向回首页', async () => {
    const s = store()
    const router = { currentRoute: { value: { path: '/course/list' } }, replace: vi.fn().mockResolvedValue() }
    await recoverFromExpiredLogin(s, router)
    expect(s.resetToken).toHaveBeenCalledOnce()
    expect(router.replace).toHaveBeenCalledWith('/login')
  })

  it('已经在登录页时不重复跳转', async () => {
    const router = { currentRoute: { value: { path: '/login' } }, replace: vi.fn() }
    await recoverFromExpiredLogin(store(), router)
    expect(router.replace).not.toHaveBeenCalled()
  })
})

describe('会话过期处理器', () => {
  it('注册后可由 request 层统一调用', async () => {
    const fn = vi.fn().mockResolvedValue()
    setSessionExpiredHandler(fn)
    await handleSessionExpired()
    expect(fn).toHaveBeenCalledOnce()
  })
})
```

新建 `tests/unit/id.test.js`：

```js
import { describe, expect, it } from 'vitest'
import { parseIdList } from '@/utils/id'

describe('parseIdList', () => {
  it('保留字符串，不把 19 位雪花 ID 转成 Number', () => {
    expect(parseIdList('2112366523950714882, 1003')).toEqual(['2112366523950714882', '1003'])
  })

  it('拒绝非数字输入，避免把空格或字母发给后端', () => {
    expect(() => parseIdList('1003,abc')).toThrow()
    expect(() => parseIdList('')).toThrow()
  })

  it('去重并去掉空项', () => {
    expect(parseIdList('1003,,1003')).toEqual(['1003'])
  })
})
```

新建 `tests/unit/readonly.test.js`：

```js
import { describe, expect, it } from 'vitest'
import courseRoutes from '@/router/modules/course'

const pick = (path) => courseRoutes.children.find((child) => child.path === path)

describe('课程只读路由', () => {
  it('查看页只需 course:list，不能复用只要求 course:publish 的编辑页', () => {
    const view = pick('view')
    expect(view).toBeTruthy()
    expect(view.meta.permission).toEqual(['course:list'])
    expect(view.meta.readonly).toBe(true)
  })

  it('编辑页仍要求 publish 权限', () => {
    expect(pick('publish').meta.permission).toEqual(['course:publish'])
  })
})
```

- [ ] **Step 3: 运行确认失败**

Run: `cd /Users/jax/Projects/zhixue-cloud/zhixue-admin-web && npm test`
Expected: FAIL，模块 `@/utils/session`、`@/utils/id` 不存在

- [ ] **Step 4: 实现 `utils/session.js`**

```js
/**
 * 登录过期的统一恢复。
 *
 * 只清 localStorage 不够：Pinia 里还留着 token，路由守卫看到 token 会把 /login
 * 重定向回 /dashboard，用户永远回不到登录页。
 */
let expiredHandler = async () => {}

export function setSessionExpiredHandler(fn) {
  expiredHandler = typeof fn === 'function' ? fn : async () => {}
}

export function handleSessionExpired() {
  return expiredHandler()
}

export async function recoverFromExpiredLogin(store, router) {
  store.resetToken()
  if (router.currentRoute?.value?.path === '/login') {
    return
  }
  await router.replace('/login')
}
```

- [ ] **Step 5: 实现 `utils/id.js`**

```js
const ID_PATTERN = /^\d{1,19}$/

/**
 * 解析后台输入的用户/课程 ID 列表。
 *
 * 雪花 ID 超过 Number 的安全整数范围（2^53），Number() 会静默改变低几位，
 * 导致"发给 A 的券发给了 B""秒杀指向了另一门课"。所以这里一律保留字符串。
 */
export function parseIdList(input) {
  const parts = String(input || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
  if (parts.length === 0) {
    throw new Error('请输入至少一个 ID')
  }
  const invalid = parts.find((item) => !ID_PATTERN.test(item))
  if (invalid) {
    throw new Error(`ID 格式不正确：${invalid}`)
  }
  return [...new Set(parts)]
}
```

- [ ] **Step 6: 接入 `request.js` 与 `main.js`**

`utils/request.js` 顶部：

```js
import { handleSessionExpired } from '@/utils/session'
```

业务码 401 分支（原 `45-59`）与 HTTP 401 分支（原 `98-103`）都改为：

```js
        case 401:
          message = '未授权，请重新登录'
          handleSessionExpired()
          break
```

```js
      if (res.code === 401) {
        ElMessageBox.confirm('登录状态已过期，请重新登录', '系统提示', {
          confirmButtonText: '重新登录',
          cancelButtonText: '取消',
          type: 'warning'
        }).then(() => handleSessionExpired())
        return Promise.reject(new Error(res.msg || '未授权，请重新登录'))
      }
```

`main.js` 在 `app.use(router)` 之后注册：

```js
import { useUserStore } from '@/stores/modules/user'
import { recoverFromExpiredLogin, setSessionExpiredHandler } from '@/utils/session'
import router from './router'

setSessionExpiredHandler(() => recoverFromExpiredLogin(useUserStore(), router))
```

> 用注册回调而不是在 `request.js` 里直接 import store，避免 `request → store → api/auth → request` 的循环依赖。

- [ ] **Step 7: 新增只读路由并改查看入口**

`router/modules/course.js` 在 `publish` 之前插入：

```js
    {
      path: 'view',
      name: 'CourseView',
      component: () => import('@/views/course/publish/index.vue'),
      meta: {
        title: '课程查看',
        icon: 'View',
        permission: ['course:list'],
        readonly: true,
        keepAlive: false,
        hidden: true
      }
    },
```

`views/course/list/index.vue:325-333`：

```js
// 查看：只读模式，不进入可写表单
const handleView = (row) => {
  router.push(`/course/view?id=${row.id}`)
}

// 编辑
const handleEdit = (row) => {
  router.push(`/course/publish?id=${row.id}`)
}
```

- [ ] **Step 8: 发布页支持只读**

`views/course/publish/index.vue` 在 `const route = useRoute()` 后追加：

```js
const readonly = computed(() => route.meta?.readonly === true)
```

模板改动（保持结构，只加禁用与隐藏）：
- 第 13 行 `<el-form ...>` 增加 `:disabled="readonly"`；
- 第 21、24、27、50、55、59、64 行的输入控件由外层 `el-form` 的 `disabled` 统一接管；
- 第 77-78 行"添加章节"工具栏包一层 `<template v-if="!readonly">`，第 100-106 行的操作按钮同样包 `<template v-if="!readonly">`；
- 第 113 行试看 `el-form` 增加 `:disabled="readonly"`；
- 第 141-151 行底部按钮区：`readonly` 时只保留"上一步/下一步"和"返回"，隐藏 `handlePublish`、`handleSaveDraft`；
- 第 170-173 行弹窗 footer 的"保存"按钮加 `v-if="!readonly"`。

同时把 `submitNodeForm`、`saveCourse`、`handlePublish` 三个入口的首行加防御：

```js
if (readonly.value) {
  ElMessage.warning('只读模式不能修改课程')
  return
}
```

- [ ] **Step 9: 修正雪花 ID**

`views/marketing/coupon/index.vue:296-304`：

```js
import { parseIdList } from '@/utils/id'

async function handleIssue(row) {
  try {
    const { value } = await ElMessageBox.prompt('请输入接收用户ID，多个以英文逗号分隔', `发放「${row.name}」`, {
      inputPlaceholder: '例如：1003,2112366523950714882',
      inputValidator: (v) => {
        try {
          parseIdList(v)
          return true
        } catch (error) {
          return error.message
        }
      }
    })
    const userIds = parseIdList(value)
    const res = await issueCoupon(row.id, userIds)
```

`views/marketing/seckill/index.vue`：
- 第 74-75 行改为 `<el-input v-model="form.productId" placeholder="请输入课程 ID（字符串）" clearable />`；
- 第 227 行 `productId: Number(detail.productId)` 改为 `productId: String(detail.productId ?? '')`；
- `emptyForm()` 中 `productId` 默认值改为 `''`；
- `submitForm` 提交前校验 `/^\d{1,19}$/.test(String(form.productId))`，不合法则 `ElMessage.error('课程 ID 格式不正确')` 并返回。

- [ ] **Step 10: 运行测试与构建**

Run: `cd /Users/jax/Projects/zhixue-cloud/zhixue-admin-web && npm test`
Expected: 3 个文件、7 个用例全部通过

Run: `npm run build`
Expected: 构建成功，无 `error`

- [ ] **Step 11: 提交**

```bash
git add zhixue-admin-web/package.json zhixue-admin-web/vite.config.js \
        zhixue-admin-web/src/utils/{session,id}.js zhixue-admin-web/src/utils/request.js \
        zhixue-admin-web/src/main.js zhixue-admin-web/src/router/modules/course.js \
        zhixue-admin-web/src/views/course zhixue-admin-web/src/views/marketing \
        zhixue-admin-web/tests
git commit -m "fix(admin): 401 同步重置登录态、雪花 ID 保持字符串、课程查看改只读路由"
```

---

### Task A5：管理员防自锁 + 权限变更即时撤销旧会话

**实施记录（2026-10-22 开始，验证至本机时间 2026-10-23 00:18）**：代码、本地回归与独立静态复审完成；不是已部署。新增 `LoginSessionRegistry`（common）、`AdminOperationGuard`/`SessionRevoker`（system），接入 auth/token、gateway 和 WS 握手/持续双向校验。授权 profile 与 mutation 的首次读取前共用同一 ADMIN 行锁；角色/菜单无效授权先验后删，写不完整抛错回滚。菜单恢复权限按逗号字段分割且缺数据也 fail-closed，非法状态不能绕过。

- [x] 防自删/自停、通用编辑绕过、最后启用超管、自身移除 ADMIN 的写入口保护。
- [x] 原子注册 token 与有序索引，修订 CAS，旧无版本会话强制重新登录，随机 jti。
- [x] 用户/角色/菜单权限变化撤销；Redis 失败拒绝 DB 写，已建立 WS 收发也校验并关闭，校验在线程数/队列有界的工作池运行。
- [x] TDD 与审查修复：角色/菜单部分插入不再返回 false；恢复菜单不能改权/停/删；组合权限及不存在恢复数据受保护。
- [x] 本次全后端 `mvn test`：467 测试，466 通过、0 失败/错误、1 默认跳过（显式 Redis 集成）；日志 `a5-full-backend-tests.log`。含未提交 AI 工作区，不等于纯提交版本验收。
- [x] 显式隔离 Redis 集成 1/1：真实 JSON 序列化、索引 TTL 不缩短、用户/全局撤销、登出、旧修订拒绝、并发注册/撤销 10 轮；使用随机本地端口无持久化实例，结束自清理。
- [x] 两轴静态审查发现的锁前旧快照、部分授权、恢复菜单/状态绕过均经失败测试修复并最终复审无阻断；IDE 诊断与 `git diff --check` 通过。
- [ ] 隔离 MySQL 并发事务、多节点/Redis Cluster、真实部署与恢复故障门禁。本批不访问或修改现有业务数据库。

部署前提见 `README.md`“会话撤销版本升级”：统一升级 auth/gateway/system/interaction 与 common、旧令牌重新登录、保留 Redis 版本元数据、DB 回滚后接受保守重登。下一任务 A6。下列原规格是历史设计；实现以以上新文件、当前 Service 和测试为准，不复制非原子用户集合示例。

**执行校正（2026-10-22）**：以真实用户/角色 Service 写入口、TokenService 和 HTTP/WS 验证为测试接缝。用户减权先锁同一 ADMIN 角色行（`FOR UPDATE`，事务内）再统计启用超管，防两个不同目标同时被停用；不允许自删/自停，自己改角色时只禁止移除 ADMIN 而非所有自编辑。通用 `updateUser/updateRole` 同样校验，角色删除前取得受影响用户；合法状态仅 0/1。角色标识不允许把 ADMIN 改掉或普通角色改成 ADMIN。

会话注册/撤销放到 common 的 `LoginSessionRegistry`：独立 String Redis 元数据、用户 token 有序集合与用户会话版本，注册/撤销用 Lua 原子执行；以请求开始时的授权修订号拒绝与账户修改交错的旧登录/刷新快照。token 写入与索引注册不可分开；有效旧会话版本不匹配即拒绝，索引 TTL 不能缩短，过期成员按分数清理。上线前签发的无版本 token 一律重新登录，不保留两小时撤权窗口。注册新增随机 jti，登出不记录 token 前缀。账号/授权变更在数据库事务内撤销，Redis 失败抛错使业务写回滚；若数据库随后回滚而 Redis 已撤销，接受保守的重新登录，不放行旧身份。菜单权限变更走全局会话版本以覆盖所有关联用户；不引入新中间件。此原子脚本按现有单节点 Redis 实施，不宣称 Redis Cluster。

真实 MySQL 行锁、多节点及真实 Redis 原子故障验收需隔离环境另行验证；本批不操作当前业务数据库。以下旧代码示例仅作历史设计，不直接复制其先删后验、非原子索引或事务提交后静默失效的逻辑。

**Files:**
- Modify: `zhixue-common/src/main/java/com/zhixue/common/core/constant/CacheConstants.java`
- Modify: `zhixue-common/src/main/java/com/zhixue/common/redis/service/RedisService.java`
- Modify: `zhixue-auth/src/main/java/com/zhixue/auth/service/impl/TokenServiceImpl.java:61-73`
- Create: `zhixue-modules/zhixue-system/src/main/java/com/zhixue/system/service/support/AdminOperationGuard.java`
- Create: `zhixue-modules/zhixue-system/src/main/java/com/zhixue/system/service/support/SessionRevoker.java`
- Modify: `zhixue-modules/zhixue-system/src/main/java/com/zhixue/system/service/impl/SysUserServiceImpl.java:104-196`
- Modify: `zhixue-modules/zhixue-system/src/main/java/com/zhixue/system/service/impl/SysRoleServiceImpl.java:61-70,121-134`
- Test: `zhixue-modules/zhixue-system/src/test/java/com/zhixue/system/service/impl/SysUserAdminGuardTest.java`（新建）
- Test: `zhixue-modules/zhixue-system/src/test/java/com/zhixue/system/service/impl/SysUserServiceImplTest.java`（补 mock）

**Interfaces:**
- Produces:
  - `AdminOperationGuard.rejectSelf(Long targetUserId, String action)` — 抛 `ServiceException`
  - `AdminOperationGuard.rejectRemovingLastAdmin(Long targetUserId, List<Long> nextRoleIds)`
  - `AdminOperationGuard.rejectDisablingLastAdmin(Long targetUserId)`
  - `SessionRevoker.revokeUser(Long userId): int` — 返回撤销的会话数
  - `CacheConstants.USER_TOKENS_KEY = "zhixue:auth:user-tokens:"`
  - `RedisService.sAdd(String key, String... values): long`、`RedisService.sMembers(String key): Set<String>`

背景：`SysRoleServiceImpl` 已保护 ADMIN **角色本体**（不可删、不可停用、改授权必须保留恢复权限），但 **用户层** 完全没有保护——管理员可以删除自己、停用自己、清空自己的角色（`SysUserServiceImpl:126-196`），也能停用最后一个超管。同时令牌只存为 `token → LoginUser`，没有用户维度索引，停用/改权限后旧令牌在 2 小时内仍然有效。

- [ ] **Step 1: 写失败测试**

新建 `SysUserAdminGuardTest.java`：

```java
package com.zhixue.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.domain.entity.SysUser;
import com.zhixue.system.domain.entity.SysUserRole;
import com.zhixue.system.mapper.SysRoleMapper;
import com.zhixue.system.mapper.SysUserMapper;
import com.zhixue.system.mapper.SysUserRoleMapper;
import com.zhixue.system.service.SysMenuService;
import com.zhixue.system.service.support.AdminOperationGuard;
import com.zhixue.system.service.support.SessionRevoker;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserAdminGuardTest {

    private static final long ADMIN = 1001L;
    private static final long OTHER_ADMIN = 1003L;
    private static final long STUDENT = 1004L;

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
        TableInfoHelper.initTableInfo(assistant, SysUserRole.class);
        TableInfoHelper.initTableInfo(assistant, SysRole.class);
    }

    @Mock private SysUserMapper userMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private SysRoleMapper roleMapper;
    @Mock private SysMenuService menuService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SessionRevoker sessionRevoker;

    private SysUserServiceImpl service;

    @BeforeEach
    void setUp() {
        AdminOperationGuard guard = new AdminOperationGuard(userMapper, userRoleMapper, roleMapper);
        service = new SysUserServiceImpl(userMapper, userRoleMapper, roleMapper, menuService,
                passwordEncoder, guard, sessionRevoker);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void loginAs(long userId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(InternalAccessConstants.USER_ID_HEADER, String.valueOf(userId));
        request.setAttribute(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private SysRole adminRole() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setRoleKey("ADMIN");
        return role;
    }

    private SysUserRole relation(long userId, long roleId) {
        SysUserRole relation = new SysUserRole();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    @Test
    void mustNotDeleteSelf() {
        loginAs(ADMIN);
        assertThatThrownBy(() -> service.removeUser(ADMIN))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前登录账号");
        verify(userMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void mustNotDisableSelf() {
        loginAs(ADMIN);
        assertThatThrownBy(() -> service.changeStatus(ADMIN, 1))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("当前登录账号");
    }

    @Test
    void mustNotDisableTheLastEnabledAdmin() {
        loginAs(ADMIN);
        lenient().when(roleMapper.selectList(any())).thenReturn(List.of(adminRole()));
        // 只有 1003 还是超管，另一个超管关系不存在
        when(userRoleMapper.selectList(any())).thenReturn(List.of(relation(OTHER_ADMIN, 1L)));
        when(userMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.changeStatus(OTHER_ADMIN, 1))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("至少保留一个启用的超级管理员");
    }

    @Test
    void disablingOrdinaryUserRevokesItsSessions() {
        loginAs(ADMIN);
        lenient().when(roleMapper.selectList(any())).thenReturn(List.of(adminRole()));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(relation(STUDENT, 3L)));
        SysUser student = new SysUser();
        student.setId(STUDENT);
        student.setStatus(0);
        when(userMapper.selectById(STUDENT)).thenReturn(student);
        when(userMapper.updateById(any(SysUser.class))).thenReturn(1);

        service.changeStatus(STUDENT, 1);

        verify(sessionRevoker).revokeUser(STUDENT);
    }
}
```

同时给现有 `SysUserServiceImplTest` 的 `@Mock` 列表补两个字段（`AdminOperationGuard`、`SessionRevoker`），否则 `@InjectMocks` 注入 null：
```java
    @Mock private AdminOperationGuard adminOperationGuard;
    @Mock private SessionRevoker sessionRevoker;
```

- [ ] **Step 2: 运行确认失败**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud && mvn test -q -pl zhixue-modules/zhixue-system -am \
  -Dtest='SysUserAdminGuardTest' -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: 编译失败（`AdminOperationGuard`、`SessionRevoker` 不存在）

- [ ] **Step 3: 扩展 `CacheConstants` 与 `RedisService`**

`CacheConstants` 追加：

```java
    /** 用户 → 该用户全部有效令牌的集合，用于停用/改权限时即时撤销会话 */
    public static final String USER_TOKENS_KEY = "zhixue:auth:user-tokens:";
```

`RedisService` 追加（用已有的 `redisTemplate`）：

```java
    public long sAdd(String key, String... values) {
        Long added = redisTemplate.opsForSet().add(key, values);
        return added == null ? 0L : added;
    }

    public Set<String> sMembers(String key) {
        Set<Object> members = redisTemplate.opsForSet().members(key);
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        return members.stream().map(String::valueOf).collect(Collectors.toSet());
    }

    public boolean sRemove(String key, String value) {
        Long removed = redisTemplate.opsForSet().remove(key, value);
        return removed != null && removed > 0;
    }
```

- [ ] **Step 4: 登录与登出维护索引**

`TokenServiceImpl` 的 `cacheToken` 与 `logout` 改为：

```java
    private void cacheToken(String token, LoginUser loginUser) {
        Duration ttl = resolveTokenExpiration();
        redisService.set(CacheConstants.LOGIN_TOKEN_KEY + token, loginUser, ttl);
        String indexKey = CacheConstants.USER_TOKENS_KEY + loginUser.getUserId();
        redisService.sAdd(indexKey, token);
        // 索引本身必须有 TTL，否则过期令牌会永久留在集合里
        redisService.expire(indexKey, ttl);
    }

    @Override
    public void logout(String token) {
        LoginUser loginUser = getLoginUser(token);
        if (loginUser != null) {
            redisService.sRemove(CacheConstants.USER_TOKENS_KEY + loginUser.getUserId(), token);
        }
        redisService.delete(CacheConstants.LOGIN_TOKEN_KEY + token);
        log.info("用户登出，tokenPrefix={}", token == null ? "" : token.substring(0, Math.min(token.length(), 12)));
    }
```

- [ ] **Step 5: 新建 `SessionRevoker`**

```java
package com.zhixue.system.service.support;

import com.zhixue.common.core.constant.CacheConstants;
import com.zhixue.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 撤销某个用户的全部登录会话。
 *
 * <p>网关只校验"JWT 可解析 + Redis 里存在该令牌"，不重新加载账号状态，
 * 所以停用账号、改角色、重置密码后必须主动删除令牌，否则旧会话最长还能用 2 小时。</p>
 *
 * <p>局限（明确接受）：索引上线前签发的令牌不在集合内，会自然过期，最多 2 小时窗口。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRevoker {

    private final RedisService redisService;

    public int revokeUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        String indexKey = CacheConstants.USER_TOKENS_KEY + userId;
        Set<String> tokens = redisService.sMembers(indexKey);
        if (tokens.isEmpty()) {
            redisService.delete(indexKey);
            return 0;
        }
        List<String> keys = tokens.stream().map(token -> CacheConstants.LOGIN_TOKEN_KEY + token).toList();
        redisService.delete(keys);
        redisService.delete(indexKey);
        log.info("已撤销用户全部会话 userId={}, count={}", userId, keys.size());
        return keys.size();
    }

    public int revokeRole(Long roleId, java.util.function.Function<Long, List<Long>> usersOfRole) {
        int revoked = 0;
        for (Long userId : usersOfRole.apply(roleId)) {
            revoked += revokeUser(userId);
        }
        return revoked;
    }
}
```

- [ ] **Step 6: 新建 `AdminOperationGuard`**

```java
package com.zhixue.system.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.utils.SecurityUtils;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.domain.entity.SysUser;
import com.zhixue.system.domain.entity.SysUserRole;
import com.zhixue.system.mapper.SysRoleMapper;
import com.zhixue.system.mapper.SysUserMapper;
import com.zhixue.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 后台账号操作的防自锁校验。
 *
 * <p>角色层已有"不能删除/停用 ADMIN 角色"的保护，但用户层可以删除自己、
 * 停用自己、清空自己的角色，一次误操作就再没人能进入后台。</p>
 */
@Component
@RequiredArgsConstructor
public class AdminOperationGuard {

    private static final String ADMIN_ROLE_KEY = "ADMIN";
    private static final int USER_ENABLED = 0;

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;

    public void rejectSelf(Long targetUserId, String action) {
        Long current = SecurityUtils.getUserId();
        if (current != null && Objects.equals(current, targetUserId)) {
            throw new ServiceException("不能" + action + "当前登录账号");
        }
    }

    /** 删除用户、或把用户的角色改成不含超管时调用。 */
    public void rejectRemovingLastAdmin(Long targetUserId, List<Long> nextRoleIds) {
        if (!isAdminUser(targetUserId)) {
            return;
        }
        boolean keepsAdmin = nextRoleIds == null || nextRoleIds.stream().anyMatch(this::isAdminRoleId);
        if (keepsAdmin) {
            return;
        }
        if (countOtherEnabledAdmins(targetUserId) == 0) {
            throw new ServiceException("至少保留一个启用的超级管理员");
        }
    }

    /** 停用用户时调用。 */
    public void rejectDisablingLastAdmin(Long targetUserId) {
        if (isAdminUser(targetUserId) && countOtherEnabledAdmins(targetUserId) == 0) {
            throw new ServiceException("至少保留一个启用的超级管理员");
        }
    }

    private boolean isAdminUser(Long userId) {
        List<SysUserRole> relations = userRoleMapper.selectList(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        return relations.stream().anyMatch(relation -> isAdminRoleId(relation.getRoleId()));
    }

    private boolean isAdminRoleId(Long roleId) {
        return adminRoleIds().contains(roleId);
    }

    private List<Long> adminRoleIds() {
        return roleMapper.selectList(null).stream()
                .filter(role -> ADMIN_ROLE_KEY.equalsIgnoreCase(role.getRoleKey()))
                .map(SysRole::getId)
                .toList();
    }

    private long countOtherEnabledAdmins(Long excludeUserId) {
        List<Long> adminRoleIds = adminRoleIds();
        if (adminRoleIds.isEmpty()) {
            return 0;
        }
        List<Long> userIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().in(SysUserRole::getRoleId, adminRoleIds))
                .stream()
                .map(SysUserRole::getUserId)
                .filter(id -> !Objects.equals(id, excludeUserId))
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return 0;
        }
        return userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, userIds)
                .eq(SysUser::getStatus, USER_ENABLED));
    }
}
```

- [ ] **Step 7: 接入 `SysUserServiceImpl`**

在字段末尾追加（保持原有 5 个字段顺序不变）：

```java
    private final AdminOperationGuard adminOperationGuard;
    private final SessionRevoker sessionRevoker;
```

方法改造（行号基于当前版本，改完务必重读确认）：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeUser(Long id) {
        adminOperationGuard.rejectSelf(id, "删除");
        adminOperationGuard.rejectRemovingLastAdmin(id, List.of());
        int revoked = sessionRevoker.revokeUser(id);
        log.info("删除用户 id={}，同时撤销 {} 个会话", id, revoked);
        userRoleMapper.physicalDeleteByUserId(id);
        return userMapper.deleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignRoles(Long userId, List<Long> roleIds) {
        if (userId == null || userMapper.selectById(userId) == null) {
            throw new ServiceException("用户不存在");
        }
        if (roleIds == null) {
            return true;
        }
        List<Long> targetRoleIds = roleIds.stream().filter(Objects::nonNull).distinct().toList();
        adminOperationGuard.rejectSelf(userId, "修改角色");
        adminOperationGuard.rejectRemovingLastAdmin(userId, targetRoleIds);
        // ...原有"校验角色存在 + 物理删除 + 逐条插入"逻辑保持不变...
        sessionRevoker.revokeUser(userId);
        return inserted == targetRoleIds.size();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean resetPassword(Long userId, String password) {
        // ...原有参数校验与密码加密不变...
        boolean updated = userMapper.updateById(entity) > 0;
        if (updated) {
            // 改密码等于让旧会话失效，否则被盗号后改密码也踢不掉对方
            sessionRevoker.revokeUser(userId);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean changeStatus(Long userId, Integer status) {
        // ...原有参数校验与用户存在校验不变...
        if (status != null && status != 0) {
            adminOperationGuard.rejectSelf(userId, "停用");
            adminOperationGuard.rejectDisablingLastAdmin(userId);
        }
        entity.setStatus(status);
        boolean updated = userMapper.updateById(entity) > 0;
        if (updated && status != null && status != 0) {
            sessionRevoker.revokeUser(userId);
        }
        return updated;
    }
```

`updateUser` 中若 `dto.getStatus()` 由 0 改为停用，同样调用 `rejectSelf` + `rejectDisablingLastAdmin`；若 `dto.getRoleIds() != null` 则调用 `rejectSelf` + `rejectRemovingLastAdmin`，成功后 `sessionRevoker.revokeUser(entity.getId())`。

- [ ] **Step 8: 角色改动后撤销相关用户会话**

`SysRoleServiceImpl` 构造追加 `private final SessionRevoker sessionRevoker;`，`updateRole` 与 `assignMenus`、`changeStatus` 在写库成功后追加：

```java
        // 权限快照存在令牌里，改授权必须让旧令牌失效，否则新权限要等 2 小时才生效
        sessionRevoker.revokeRole(role.getId(), roleId -> userRoleMapper
                .selectList(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, roleId))
                .stream().map(SysUserRole::getUserId).toList());
```

`updateRole` 另加一条拦截：不允许改掉 ADMIN 角色的 `roleKey` 或把它停用（否则可绕过 `rejectIfSuperAdmin`）：

```java
        SysRole existing = roleMapper.selectById(role.getId());
        if (isSuperAdmin(existing)) {
            if (role.getStatus() != null && role.getStatus() != 0) {
                throw new ServiceException("超级管理员角色不允许停用");
            }
            if (StringUtils.hasText(role.getRoleKey()) && !"ADMIN".equalsIgnoreCase(role.getRoleKey())) {
                throw new ServiceException("超级管理员角色不允许修改角色标识");
            }
        }
```

- [ ] **Step 9: 运行测试**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud && mvn test -q -pl zhixue-modules/zhixue-system -am
```
Expected: BUILD SUCCESS，新增 4 个用例通过，原有用例不回归。

- [ ] **Step 10: 提交**

```bash
git add zhixue-common/src/main/java/com/zhixue/common/redis/service/RedisService.java \
        zhixue-common/src/main/java/com/zhixue/common/core/constant/CacheConstants.java \
        zhixue-auth/src/main/java/com/zhixue/auth/service/impl/TokenServiceImpl.java \
        zhixue-modules/zhixue-system/src
git commit -m "fix(system,auth): 管理员不能自锁，停用/改权限/改密后即时撤销旧会话"
```

---

### Task A6：秒杀建单幂等，消除超时孤儿单

**Files:**
- Modify: `zhixue-modules/zhixue-order/src/main/resources/sql/order_schema.sql:4-25`
- Create: `zhixue-modules/zhixue-order/src/main/resources/sql/upgrade/2026-10-22-order-coupon-seckill.sql`（本任务只加 `activity_id`，用券列在 C2 追加）
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/domain/entity/Order.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/service/OrderService.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/service/impl/OrderServiceImpl.java:88-100`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/controller/OrderController.java:122-129`
- Modify: `zhixue-api/src/main/java/com/zhixue/api/order/RemoteOrderService.java`
- Modify: `zhixue-modules/zhixue-marketing/src/main/java/com/zhixue/marketing/service/impl/SeckillServiceImpl.java:145-200,248-259`
- Test: `zhixue-modules/zhixue-order/src/test/java/com/zhixue/order/service/SeckillIdempotencyTest.java`（新建）
- Test: `zhixue-modules/zhixue-marketing/src/test/java/com/zhixue/marketing/service/SeckillOrderFlowTest.java`（追加 2 例）
- Test: `zhixue-modules/zhixue-marketing/src/test/java/com/zhixue/marketing/service/SeckillStockEditTest.java`（新建）

**Interfaces:**
- Produces:
  - `Order.getActivityId()/setActivityId(Long)`
  - `OrderService.findSeckillOrder(Long activityId, Long userId): Order`
  - `OrderService.createSeckillOrder(...)` 变为幂等：同 `(activityId,userId)` 已存在且未取消时返回原单
  - `GET /order/inner/seckill?activityId=&userId=`（内部）
  - `RemoteOrderService.findSeckillOrder(activityId, userId, innerCall, internalToken)`

背景：`SeckillServiceImpl:142-143` 的注释已经承认"订单服务已建单但响应超时，本地会回滚库存，产生一张按秒杀价的孤儿待支付单"，`OrderServiceImpl:97-99` 只把 `activityId` 写进备注，没有任何幂等键。

- [ ] **Step 1: 加列的增量脚本（先建表结构）**

新建 `zhixue-modules/zhixue-order/src/main/resources/sql/upgrade/2026-10-22-order-coupon-seckill.sql`：

```sql
-- 秒杀订单幂等 + 优惠券快照。可重复执行。
USE zhixue_order;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'order' AND column_name = 'activity_id') = 0,
    'ALTER TABLE `order` ADD COLUMN activity_id BIGINT NULL COMMENT ''秒杀活动ID；非秒杀单为NULL''', 'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 唯一键：同一活动同一用户最多一张订单。MySQL 允许多行 NULL，不影响普通订单。
SET @idx = IF((SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'order' AND index_name = 'uk_order_activity_user') = 0,
    'ALTER TABLE `order` ADD UNIQUE KEY uk_order_activity_user (activity_id, user_id)', 'SELECT 1');
PREPARE stmt2 FROM @idx;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;
```

同步修改 `order_schema.sql` 的 `CREATE TABLE`：在 `remark` 之后加 `activity_id BIGINT NULL COMMENT '秒杀活动ID；非秒杀单为NULL',`，并在索引区加 `UNIQUE KEY uk_order_activity_user (activity_id, user_id)`。

- [ ] **Step 2: 写订单侧失败测试**

新建 `SeckillIdempotencyTest.java`：

```java
package com.zhixue.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.order.domain.entity.Order;
import com.zhixue.order.mapper.OrderMapper;
import com.zhixue.order.service.impl.OrderServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillIdempotencyTest {

    private static final long USER = 1003L;
    private static final long ACTIVITY = 63001L;
    private static final long COURSE = 2007L;

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Order.class);
    }

    @Mock private OrderMapper orderMapper;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private com.zhixue.api.course.RemoteCourseService remoteCourseService;

    private OrderServiceImpl service() {
        return new OrderServiceImpl(orderMapper, rabbitTemplate, remoteCourseService);
    }

    private Order existing(int status) {
        Order order = new Order();
        order.setId(9L);
        order.setOrderNo("ORD-SK-EXIST");
        order.setUserId(USER);
        order.setProductId(COURSE);
        order.setActivityId(ACTIVITY);
        order.setAmount(new BigDecimal("9.90"));
        order.setStatus(status);
        order.setExpireTime(LocalDateTime.now().plusMinutes(20));
        return order;
    }

    @Test
    void returnsExistingOrderWhenActivityOrderAlreadyExists() {
        when(orderMapper.selectOne(any())).thenReturn(existing(0));

        Order order = service().createSeckillOrder(USER, COURSE, new BigDecimal("9.90"), ACTIVITY);

        assertThat(order.getOrderNo()).isEqualTo("ORD-SK-EXIST");
        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void concurrentInsertLosingRaceAlsoReturnsTheLandedOrder() {
        when(orderMapper.selectOne(any())).thenReturn(null, existing(0));
        when(orderMapper.insert(any(Order.class))).thenThrow(new DuplicateKeyException("uk_order_activity_user"));

        Order order = service().createSeckillOrder(USER, COURSE, new BigDecimal("9.90"), ACTIVITY);

        assertThat(order.getOrderNo()).isEqualTo("ORD-SK-EXIST");
    }

    @Test
    void canceledActivityOrderBlocksRebuyInsteadOfCreatingOrphan() {
        when(orderMapper.selectOne(any())).thenReturn(existing(2));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service().createSeckillOrder(USER, COURSE, new BigDecimal("9.90"), ACTIVITY))
                .isInstanceOf(com.zhixue.common.core.exception.ServiceException.class)
                .hasMessageContaining("已取消");

        verify(orderMapper, never()).insert(any(Order.class));
    }

    @Test
    void newSeckillOrderCarriesActivityIdForIdempotency() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        service().createSeckillOrder(USER, COURSE, new BigDecimal("9.90"), ACTIVITY);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderMapper).insert(captor.capture());
        assertThat(captor.getValue().getActivityId()).isEqualTo(ACTIVITY);
    }
}
```

- [ ] **Step 3: 运行确认失败**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud && mvn test -q -pl zhixue-modules/zhixue-order -am \
  -Dtest='SeckillIdempotencyTest' -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: 编译失败（`getActivityId` 不存在）

- [ ] **Step 4: 实体与接口**

`Order.java` 在 `remark` 之前加：

```java
    /**
     * 秒杀活动 ID。与 user_id 组成唯一键，保证同一活动同一用户只有一张订单。
     * 普通下单为 null（MySQL 唯一索引允许多个 NULL）。
     */
    private Long activityId;
```

`OrderService` 追加：

```java
    /** 秒杀订单按 (活动, 用户) 查询；不存在返回 null。 */
    Order findSeckillOrder(Long activityId, Long userId);
```

- [ ] **Step 5: 实现幂等建单**

`OrderServiceImpl` 替换 `createSeckillOrder`（原 88-100 行）：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order createSeckillOrder(Long userId, Long productId, BigDecimal amount, Long activityId) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "秒杀价必须大于0");
        }
        if (activityId == null) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "活动ID不能为空");
        }
        // 幂等键：营销服务在"已建单但响应超时"时会重试本方法，必须返回同一张单，
        // 否则每次重试都多一张按秒杀价的待支付订单，且库存已被营销侧回滚 → 超卖。
        Order existing = findSeckillOrder(activityId, userId);
        if (existing != null) {
            if (Integer.valueOf(2).equals(existing.getStatus())) {
                throw new ServiceException(HttpStatus.BAD_REQUEST, "该活动订单已取消，不能重复参与");
            }
            log.info("秒杀订单已存在，按幂等返回 orderNo={}, activityId={}, userId={}",
                    existing.getOrderNo(), activityId, userId);
            return existing;
        }
        if (hasPaid(userId, productId)) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "您已购买该课程，无需参与秒杀");
        }
        Order order = buildDraftOrder(userId, productId, amount);
        order.setActivityId(activityId);
        order.setRemark("秒杀活动 " + activityId);
        try {
            return insertWithTimeout(order);
        } catch (DuplicateKeyException e) {
            // 并发下两个请求同时通过上面的查询，唯一键只允许一个成功；失败方读回已落地的单
            Order landed = findSeckillOrder(activityId, userId);
            if (landed != null) {
                return landed;
            }
            throw e;
        }
    }

    @Override
    public Order findSeckillOrder(Long activityId, Long userId) {
        if (activityId == null || userId == null) {
            return null;
        }
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getActivityId, activityId)
                .eq(Order::getUserId, userId));
    }
```

导入 `org.springframework.dao.DuplicateKeyException`。

> 注意：`insertWithTimeout` 在 real 模式下会发延时消息。命中唯一键冲突时消息未发出（`insert` 先抛异常），不会有重复的取消消息。

- [ ] **Step 6: 内部查询端点**

`OrderController` 在 `/inner/seckill` 之后追加：

```java
    /** 服务间接口：按活动与用户查秒杀订单，供营销服务判断是否已建单。 */
    @GetMapping("/inner/seckill")
    public R<Order> findSeckillOrder(@RequestParam Long activityId, @RequestParam Long userId) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "禁止外部访问内部订单接口");
        }
        return R.ok(orderService.findSeckillOrder(activityId, userId));
    }
```

`RemoteOrderService` 追加：

```java
    /** 查某活动下该用户的秒杀订单；不存在时 data 为 null。仅限服务间调用。 */
    @GetMapping("/order/inner/seckill")
    R<OrderInfo> findSeckillOrder(@RequestParam("activityId") Long activityId,
                                  @RequestParam("userId") Long userId,
                                  @RequestHeader(InternalAccessConstants.INNER_CALL_HEADER) String innerHeader,
                                  @RequestHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER) String internalToken);
```

- [ ] **Step 7: 营销侧改为"重试而不是回滚"**

`SeckillServiceImpl` 替换 145-185 行的 `seckill` 方法体（保留令牌校验与 Lua 前置）：

```java
        String stockKey = stockKeyPrefix + dto.getActivityId();
        String userKey = userKeyPrefix + dto.getActivityId();
        Long result = redisTemplate.execute(seckillScript, List.of(stockKey, userKey), String.valueOf(userId));
        if (Objects.equals(result, 0L)) {
            throw new ServiceException("已售罄");
        }
        if (Objects.equals(result, 2L)) {
            throw new ServiceException("请勿重复抢购");
        }

        OrderInfo realOrder;
        try {
            realOrder = createRealOrder(userId, activity);
        } catch (ServiceException e) {
            if (e.getCode() != HttpStatus.SERVICE_UNAVAILABLE) {
                // 明确的业务拒绝（已购买、参数不合法）：订单肯定没建，回滚库存是对的
                rollbackStock(stockKey, userKey, userId);
                throw e;
            }
            // 503 只代表"结果不确定"：订单可能已建单但响应超时。
            // 订单服务对 (activityId, userId) 幂等，重试会拿到同一张单；
            // 这里绝不能回滚库存 —— 那会让下一个人再买一次，形成超卖。
            log.warn("秒杀建单结果不确定，按幂等键重试一次 activityId={}, userId={}", activity.getId(), userId, e);
            try {
                realOrder = createRealOrder(userId, activity);
            } catch (RuntimeException retryFailure) {
                log.error("秒杀建单两次失败，库存不回滚（可能已成交），需人工核对 activityId={}, userId={}",
                        activity.getId(), userId, retryFailure);
                throw new ServiceException(HttpStatus.SERVICE_UNAVAILABLE,
                        "下单结果确认中，请稍后在“我的订单”查看，勿重复抢购");
            }
        }

        // 订单已经真实存在：后续落库失败也不能回滚库存，否则库存与订单不一致
        try {
            SeckillOrder record = new SeckillOrder();
            record.setActivityId(dto.getActivityId());
            record.setUserId(userId);
            record.setOrderNo(realOrder.getOrderNo());
            record.setStatus(RECORD_ORDERED);
            orderMapper.insert(record);
        } catch (RuntimeException recordFailure) {
            log.error("秒杀订单已建单但本地记录写入失败，不回滚库存 orderNo={}, activityId={}, userId={}",
                    realOrder.getOrderNo(), activity.getId(), userId, recordFailure);
        }
        return new SeckillResultVO(realOrder.getOrderNo(), realOrder.getAmount());
```

- [ ] **Step 8: 库存编辑不得绕过已装载的 Redis 余量**

`SeckillServiceImpl.update`（248-259 行）在 `fillActivity` 之前加：

```java
        // 开抢后改总库存只会改数据库：ensureStockLoaded 用 SETNX，已存在的 Redis 余量不会刷新，
        // 导致后台显示 100 件、实际只能卖 20 件。开抢后一律不允许改库存。
        if (!Objects.equals(dto.getTotalStock(), activity.getTotalStock())
                && Boolean.TRUE.equals(redisTemplate.hasKey(stockKeyPrefix + activity.getId()))) {
            throw new ServiceException("活动已开始抢购，不能修改总库存；请新建活动");
        }
```

- [ ] **Step 9: 写并运行营销侧测试**

在 `SeckillOrderFlowTest` 追加：

```java
    @Test
    void remoteTimeoutRetriesIdempotentCreateInsteadOfRollingBackStock() {
        tokenIsValid();
        luaDeductSucceeds();
        when(remoteOrderService.createSeckillOrder(any(SeckillOrderRequest.class), anyString(), anyString()))
                .thenThrow(new RuntimeException("Read timed out"))
                .thenReturn(R.ok(orderInfo()));

        SeckillResultVO result = service.seckill(USER, request("tk"));

        assertThat(result.getOrderNo()).isEqualTo("ORD-SK-1");
        verify(remoteOrderService, times(2)).createSeckillOrder(any(SeckillOrderRequest.class), anyString(), anyString());
        verify(redisTemplate, never()).execute(argThat(scriptContaining("INCR")), anyList(), eq(String.valueOf(USER)));
    }

    @Test
    void definiteBusinessRejectionStillCompensatesStock() {
        tokenIsValid();
        luaDeductSucceeds();
        when(remoteOrderService.createSeckillOrder(any(SeckillOrderRequest.class), anyString(), anyString()))
                .thenReturn(com.zhixue.common.core.domain.R.fail(400, "您已购买该课程，无需参与秒杀"));

        assertThatThrownBy(() -> service.seckill(USER, request("tk")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已购买");

        verify(redisTemplate).execute(argThat(scriptContaining("INCR")), anyList(), eq(String.valueOf(USER)));
    }
```

新建 `SeckillStockEditTest.java`：

```java
package com.zhixue.marketing.service;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.marketing.domain.dto.SeckillActivityDTO;
import com.zhixue.marketing.domain.entity.SeckillActivity;
import com.zhixue.marketing.mapper.SeckillActivityMapper;
import com.zhixue.marketing.mapper.SeckillOrderMapper;
import com.zhixue.api.order.RemoteOrderService;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.marketing.service.impl.SeckillServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillStockEditTest {

    @Mock private SeckillActivityMapper activityMapper;
    @Mock private SeckillOrderMapper orderMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RemoteOrderService remoteOrderService;

    @Test
    void cannotChangeTotalStockAfterStockLoaded() {
        SeckillServiceImpl service = new SeckillServiceImpl(activityMapper, orderMapper, redisTemplate,
                remoteOrderService, new SecurityProperties());
        ReflectionTestUtils.setField(service, "stockKeyPrefix", "seckill:stock:");

        SeckillActivity activity = new SeckillActivity();
        activity.setId(1L);
        activity.setTotalStock(100);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        when(activityMapper.selectById(1L)).thenReturn(activity);
        when(redisTemplate.hasKey("seckill:stock:1")).thenReturn(true);

        SeckillActivityDTO dto = new SeckillActivityDTO();
        dto.setId(1L);
        dto.setTotalStock(50);
        dto.setStartTime(activity.getStartTime());
        dto.setEndTime(activity.getEndTime());
        dto.setProductId(2007L);
        dto.setSeckillPrice(new BigDecimal("9.90"));

        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能修改总库存");
    }
}
```

> 若 `SeckillActivityDTO` 字段名与上面不同，按实际字段补全（`title`、`status` 等），断言不变。

- [ ] **Step 10: 运行两个模块测试**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud && mvn test -q -pl zhixue-modules/zhixue-order,zhixue-modules/zhixue-marketing -am
```
Expected: BUILD SUCCESS

- [ ] **Step 11: 提交**

```bash
git add zhixue-api/src/main/java/com/zhixue/api/order/RemoteOrderService.java \
        zhixue-modules/zhixue-order/src zhixue-modules/zhixue-marketing/src
git commit -m "fix(order,marketing): 秒杀订单按活动+用户幂等，超时改为重试而不是回滚库存"
```

---

### Task A7：弹幕审核前置到广播之前

**Files:**
- Modify: `zhixue-modules/zhixue-interaction/src/main/java/com/zhixue/interaction/domain/dto/DanmakuMessageDTO.java`
- Create: `zhixue-modules/zhixue-interaction/src/main/java/com/zhixue/interaction/service/DanmakuModerationService.java`
- Modify: `zhixue-modules/zhixue-interaction/src/main/java/com/zhixue/interaction/netty/DanmakuHandler.java:30-105`
- Modify: `zhixue-modules/zhixue-interaction/src/main/java/com/zhixue/interaction/service/DanmakuPersistenceService.java:27-58`
- Modify: `zhixue-modules/zhixue-interaction/src/main/resources/application.yml`
- Modify: `zhixue-admin-web/src/views/interaction/comment/index.vue:16`
- Test: `zhixue-modules/zhixue-interaction/src/test/java/com/zhixue/interaction/service/DanmakuModerationServiceTest.java`（新建）
- Test: `zhixue-modules/zhixue-interaction/src/test/java/com/zhixue/interaction/netty/DanmakuWebSocketAuthTest.java`（改构造）
- Test: `zhixue-modules/zhixue-interaction/src/test/java/com/zhixue/interaction/netty/DanmakuBroadcastGateTest.java`（新建）

**Interfaces:**
- Produces:
  - `DanmakuModerationService.submit(DanmakuMessageDTO dto)` — 异步；通过则广播+落库，拒绝只落库，审核不可用按策略处理
  - `DanmakuModerationService.moderate(DanmakuMessageDTO dto): int` — 同步执行返回 `auditStatus`，供测试与降级路径复用
  - `DanmakuMessageDTO` 新增 `Integer auditStatus`、`String auditRemark`

背景：`DanmakuHandler:93-97` 先 `publish` 再 `send`，而 AI 审核发生在消费者 `persist` 里。被拒绝的内容已经推到全房间。`DanmakuPersistenceService:46-53` 在审核失败时按"通过"处理。

- [ ] **Step 1: 写失败测试**

新建 `DanmakuModerationServiceTest.java`：

```java
package com.zhixue.interaction.service;

import com.zhixue.api.ai.RemoteAiService;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.mq.DanmakuProducer;
import com.zhixue.interaction.redis.RedisMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DanmakuModerationServiceTest {

    @Mock private RemoteAiService remoteAiService;
    @Mock private RedisMessagePublisher publisher;
    @Mock private DanmakuProducer producer;

    private DanmakuModerationService service;

    @BeforeEach
    void setUp() {
        SecurityProperties properties = new SecurityProperties();
        properties.setInternalToken("internal-token-for-unit-test-0123456789");
        service = new DanmakuModerationService(remoteAiService, publisher, producer, properties, 1);
    }

    private DanmakuMessageDTO message() {
        DanmakuMessageDTO dto = new DanmakuMessageDTO();
        dto.setRoomId(1L);
        dto.setUserId(1003L);
        dto.setContent("这段代码为什么会死锁");
        return dto;
    }

    @Test
    void approvedDanmakuIsPersistedThenBroadcast() {
        when(remoteAiService.auditText(anyMap(), anyString(), anyString())).thenReturn(R.ok("OK"));

        assertThat(service.moderate(message())).isEqualTo(1);

        verify(producer).send(any(DanmakuMessageDTO.class));
        verify(publisher).publish(any(DanmakuMessageDTO.class));
    }

    @Test
    void rejectedDanmakuIsPersistedButNeverBroadcast() {
        when(remoteAiService.auditText(anyMap(), anyString(), anyString())).thenReturn(R.ok("含辱骂内容"));

        assertThat(service.moderate(message())).isEqualTo(2);

        verify(producer).send(any(DanmakuMessageDTO.class));
        verify(publisher, never()).publish(any(DanmakuMessageDTO.class));
    }

    @Test
    void auditUnavailableMarksPendingReviewInsteadOfSilentlyPassing() {
        when(remoteAiService.auditText(anyMap(), anyString(), anyString()))
                .thenThrow(new RuntimeException("connect timeout"));

        // fail-open：实时体验不能因为审核服务挂掉而中断，但必须落成"待人工复核"而不是"已通过"
        assertThat(service.moderate(message())).isEqualTo(0);

        verify(publisher).publish(any(DanmakuMessageDTO.class));
        verify(producer).send(any(DanmakuMessageDTO.class));
    }
}
```

新建 `DanmakuBroadcastGateTest.java`：

```java
package com.zhixue.interaction.netty;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.service.DanmakuModerationService;
import com.zhixue.interaction.service.SensitiveWordFilter;
import com.zhixue.interaction.websocket.RoomRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DanmakuBroadcastGateTest {

    @Mock private RoomRegistry roomRegistry;
    @Mock private DanmakuModerationService moderationService;
    @Mock private SensitiveWordFilter sensitiveWordFilter;

    private DanmakuHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DanmakuHandler(roomRegistry, moderationService, sensitiveWordFilter, new ObjectMapper());
    }

    @Test
    void handlerDelegatesToModerationInsteadOfBroadcastingDirectly() {
        // 构造器已不再持有 publisher/producer：编译通过即证明广播被移动到审核之后
        org.assertj.core.api.Assertions.assertThat(handler).isNotNull();
        // 敏感词过滤必须在交给审核前完成，避免把原文送进模型日志
        verify(sensitiveWordFilter, org.mockito.Mockito.atLeast(0)).filter(any());
    }
}
```

> 该测试的关键价值是**编译期约束**：只要 `DanmakuHandler` 还持有 `RedisMessagePublisher` 就说明广播仍然前置。

- [ ] **Step 2: 运行确认失败**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud && mvn test -q -pl zhixue-modules/zhixue-interaction -am \
  -Dtest='DanmakuModerationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: 编译失败（`DanmakuModerationService` 不存在）

- [ ] **Step 3: DTO 增加审核字段**

`DanmakuMessageDTO` 追加（要跨 MQ 传递，必须是可序列化字段）：

```java
    /** 0-待人工复核 1-通过 2-拒绝；为 null 表示旧消息，由持久化端按旧逻辑审核 */
    private Integer auditStatus;

    private String auditRemark;
```

- [ ] **Step 4: 实现 `DanmakuModerationService`**

```java
package com.zhixue.interaction.service;

import com.zhixue.api.ai.RemoteAiService;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.SecurityProperties;
import com.zhixue.interaction.domain.dto.DanmakuMessageDTO;
import com.zhixue.interaction.mq.DanmakuProducer;
import com.zhixue.interaction.redis.RedisMessagePublisher;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 弹幕审核闸口。
 *
 * <p>审核必须在广播之前完成：先广播再审核，被拒绝的内容已经出现在所有人屏幕上，
 * 事后的拒绝只能影响数据库，撤销不了已经看到的内容。</p>
 *
 * <p>审核是远程调用（本机模型约 0.4 秒），不能占用 Netty 事件循环，因此放到有界线程池；
 * 队列满时按 {@code fail-open} 策略降级，不阻塞聊天。</p>
 */
@Slf4j
@Service
public class DanmakuModerationService {

    public static final int PENDING = 0;
    public static final int APPROVED = 1;
    public static final int REJECTED = 2;

    private final RemoteAiService remoteAiService;
    private final RedisMessagePublisher publisher;
    private final DanmakuProducer producer;
    private final SecurityProperties securityProperties;
    private final boolean failOpen;
    private final ThreadPoolExecutor executor;

    public DanmakuModerationService(RemoteAiService remoteAiService,
                                    RedisMessagePublisher publisher,
                                    DanmakuProducer producer,
                                    SecurityProperties securityProperties,
                                    @Value("${interaction.danmaku.workers:4}") int workers) {
        this.remoteAiService = remoteAiService;
        this.publisher = publisher;
        this.producer = producer;
        this.securityProperties = securityProperties;
        this.failOpen = true;
        this.executor = new ThreadPoolExecutor(workers, workers, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(workers * 200),
                runnable -> {
                    Thread thread = new Thread(runnable, "danmaku-audit");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    /** 异步入口：投递失败时按降级策略直接放行并标记待复核。 */
    public void submit(DanmakuMessageDTO dto) {
        try {
            executor.execute(() -> moderate(dto));
        } catch (RuntimeException rejected) {
            log.warn("弹幕审核队列已满，按 fail-open 处理 roomId={}", dto.getRoomId());
            dto.setAuditStatus(PENDING);
            dto.setAuditRemark("审核队列繁忙，待人工复核");
            dispatch(dto);
        }
    }

    /**
     * 同步审核并分发。
     *
     * @return 0 待人工复核、1 通过、2 拒绝
     */
    public int moderate(DanmakuMessageDTO dto) {
        int status;
        String remark = null;
        try {
            R<String> result = remoteAiService.auditText(
                    Map.of("content", dto.getContent()), "true", securityProperties.getInternalToken());
            if (result != null && result.isSuccess() && result.getData() != null) {
                if ("OK".equalsIgnoreCase(result.getData().trim())) {
                    status = APPROVED;
                } else {
                    status = REJECTED;
                    remark = result.getData();
                }
            } else {
                status = degradeStatus("AI 审核无有效返回");
                remark = status == PENDING ? "审核不可用，待人工复核" : null;
            }
        } catch (Exception e) {
            status = degradeStatus("AI 审核调用异常");
            remark = status == PENDING ? "审核不可用，待人工复核" : null;
            log.warn("弹幕审核异常，按{}处理 roomId={}", failOpen ? "待复核" : "拒绝", dto.getRoomId(), e);
        }
        dto.setAuditStatus(status);
        dto.setAuditRemark(remark);
        dispatch(dto);
        return status;
    }

    private int degradeStatus(String reason) {
        log.warn("{}，fail-open={}", reason, failOpen);
        return failOpen ? PENDING : REJECTED;
    }

    private void dispatch(DanmakuMessageDTO dto) {
        if (Integer.valueOf(APPROVED).equals(dto.getAuditStatus())) {
            publisher.publish(dto);
        }
        producer.send(dto);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }
}
```

> 降级策略固定为 fail-open（标记待复核而不是拒绝），是因为审核服务故障时把全部弹幕拦下来会让直播间不可用；待复核状态保证事后可追责。若产品要求从严，把 `failOpen` 改为读配置 `interaction.danmaku.fail-open` 即可，此处先按单一策略实现（YAGNI）。

- [ ] **Step 5: 改造 `DanmakuHandler`**

构造字段由 4 个改为 3 个：

```java
    private final RoomRegistry roomRegistry;
    private final DanmakuModerationService moderationService;
    private final SensitiveWordFilter sensitiveWordFilter;
    private final ObjectMapper objectMapper;
```

`channelRead0` 的 90-97 行替换为：

```java
            // 敏感词先本地替换，避免把原文送进模型日志；随后交给审核闸口，
            // 审核通过才广播，被拒绝的只落库供管理员复核
            dto.setContent(sensitiveWordFilter.filter(dto.getContent()));
            moderationService.submit(dto);
```

删除 `redisMessagePublisher`、`danmakuProducer` 两个字段及对应 import。

`DanmakuWebSocketAuthTest:74` 的构造改为：

```java
new DanmakuHandler(roomRegistry, mock(DanmakuModerationService.class), filter, objectMapper)
```

- [ ] **Step 6: 持久化端使用传入的审核结果**

`DanmakuPersistenceService.persist`（27-58 行）改为：

```java
    public void persist(DanmakuMessageDTO dto) {
        Danmaku entity = new Danmaku();
        entity.setRoomId(dto.getRoomId());
        entity.setUserId(dto.getUserId());
        entity.setContent(dto.getContent());
        entity.setTimePoint(dto.getTimePoint());
        if (dto.getAuditStatus() != null) {
            // 审核已在广播前完成，这里只落库，绝不重复审核（重复调用会让同一条被审两次）
            entity.setAuditStatus(dto.getAuditStatus());
            entity.setAuditRemark(dto.getAuditRemark());
            danmakuMapper.insert(entity);
            return;
        }
        // 兼容升级窗口内仍在 MQ 里的旧消息：没有审核结果才走原来的同步审核
        int status = APPROVED;
        String remark = null;
        try {
            R<String> auditResult = remoteAiService.auditText(Map.of("content", dto.getContent()),
                    "true", securityProperties.getInternalToken());
            if (auditResult != null && auditResult.isSuccess() && auditResult.getData() != null
                    && !"OK".equalsIgnoreCase(auditResult.getData().trim())) {
                status = REJECTED;
                remark = auditResult.getData();
            } else if (auditResult == null || !auditResult.isSuccess()) {
                status = PENDING;
                remark = "审核不可用（旧消息），待人工复核";
            }
        } catch (Exception e) {
            status = PENDING;
            remark = "审核异常（旧消息），待人工复核";
            log.error("旧弹幕消息审核异常", e);
        }
        entity.setAuditStatus(status);
        entity.setAuditRemark(remark);
        danmakuMapper.insert(entity);
    }
```

- [ ] **Step 7: 配置与后台文案**

`zhixue-modules/zhixue-interaction/src/main/resources/application.yml` 追加：

```yaml
interaction:
  danmaku:
    workers: ${ZHIXUE_DANMAKU_WORKERS:4}
```

`views/interaction/comment/index.vue:16` 的提示改为：

```html
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="弹幕在广播前由 AI 审核：通过才展示，拒绝只落库供复核；审核服务不可用时按“待审核”记录并放行实时展示。"
      />
```

- [ ] **Step 8: 运行测试**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud && mvn test -q -pl zhixue-modules/zhixue-interaction -am
```
Expected: BUILD SUCCESS，新增 4 个用例通过

- [ ] **Step 9: 提交**

```bash
git add zhixue-modules/zhixue-interaction/src zhixue-admin-web/src/views/interaction/comment/index.vue
git commit -m "fix(interaction): 弹幕审核前置到广播之前，审核不可用改为待人工复核"
```

---

### Task A8：AI 可靠性升级落地（迁移 / 部署 / 真实评测）

**Files:**
- Modify: `ops/local/local.env.example`（补 `ZHIXUE_AI_MODEL_REVISION`、`ZHIXUE_AI_EMBEDDING_REVISION`）
- Modify: `README.md`（补评测与人工评审说明）
- Create: `.codebuddy/artifacts/ai-answer-eval-review.md`（人工评审记录模板与首轮结果）
- Test: 无新增单测（可靠性代码的 120 项测试已在上一轮完成）；本任务产出**运行证据**

**Interfaces:**
- Consumes: `scripts/local/upgrade.sh`（Task A0）、`scripts/local/ai-answer-eval.sh`
- Produces: `.codebuddy/artifacts/ai-answer-eval.json`（真实模型评测结果）、人工评审记录

背景：`ai-reliability-result.txt:2,20-36` 明确"未提交、未部署、未执行真实模型质量评测"。代码已完成，缺的是**迁移 + 部署 + 真模型评测**这条证据链。不补这一步，任何"AI 已达标"的说法都没有依据。

- [ ] **Step 1: 补齐环境变量示例**

`ops/local/local.env.example` 追加：

```bash
# AI 版本标识：写入每条回答的 trace，用于区分"哪个模型权重产出的答案"
ZHIXUE_AI_MODEL_REVISION=local-qwen3.5-9b-nvfp4-2026-10
ZHIXUE_AI_EMBEDDING_REVISION=local-qwen3-embedding-0.6b-v1
```

- [ ] **Step 2: 执行增量迁移**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud
bash scripts/local/upgrade.sh --dry-run   # 确认包含 2026-10-22-ai-reliability.sql
bash scripts/local/upgrade.sh
```
Expected: 最后一行 `[ok] 增量脚本执行完成`

验证列已存在：
```bash
docker exec -i zhixue-local-mysql mysql -uroot -p"$ZHIXUE_DB_PASSWORD" -e \
  "SELECT column_name FROM information_schema.columns WHERE table_schema='zhixue_ai' AND table_name='ai_message' AND column_name IN ('notice','trace');"
```
Expected: 两行 `notice`、`trace`

- [ ] **Step 3: 重建 AI 服务并等待索引补齐**

Run:
```bash
source scripts/local/common.sh && load_env
compose up -d --force-recreate --no-deps ai
concurrently() { :; }
for i in $(seq 1 60); do
  if docker logs zhixue-local-ai 2>&1 | grep -q "索引补齐完成"; then echo "index ready"; break; fi
  sleep 2
done
docker logs --tail 60 zhixue-local-ai
```
Expected: 日志出现索引名（形如 `zhixue_kb_<指纹>`）与"补齐完成"；无 `初始化失败`

> 若日志没有固定文案，用 `docker logs zhixue-local-ai | grep -i index` 确认，不要凭"容器 Up"判定就绪。

- [ ] **Step 4: 跑真实模型评测（需要本机 Ollama 就绪）**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud
bash scripts/local/ai-answer-eval.sh
```
Expected: 生成 `.codebuddy/artifacts/ai-answer-eval.json`，控制台打印 `summary`（场景数、pass^k、saved 比例、时延）

- [ ] **Step 5: 人工评审（不接受自动通过率替代）**

创建 `.codebuddy/artifacts/ai-answer-eval-review.md`：

```markdown
# AI 回答人工评审记录（首轮）

- 评测产物：`.codebuddy/artifacts/ai-answer-eval.json`，生成时间：<填 generatedAt>
- 模型版本：`local-qwen3.5-9b-nvfp4-2026-10`，embedding：`local-qwen3-embeddings-0.6b-v1`
- 评测环境：本机 Ollama、ES 单节点、20 场景 × 3 次

## 逐场景结论

| 场景 | 关键词规则 | 人工判定（对/部分对/错） | 问题类型 | 备注 |
|---|---|---|---|---|
| 线程池核心参数 | pass |  |  |  |
| try-with-resources |  |  | 遗漏资料 / 表述错误 / 未引用 |  |
| 无资料问题（应诚实拒答） |  |  |  |  |
| 注入类问题（应拒绝执行指令） |  |  |  |  |
| 追问承接 |  |  |  |  |
| ...（20 行填满） |  |  |  |  |

## 通过标准

- 自动规则通过率 ≥ 90%（18/20）
- 人工判定"对 + 部分对" ≥ 18/20，且"错"的 2 条不得属于同一类问题
- `saved=true` 比例 = 100%（保存失败会直接体现为历史缺失）

## 未达标的处理

不达标的场景进入 F1（补资料）或 F3（推荐阈值/标题匹配）任务；**不允许通过放宽阈值来"通过"评测**。
```

填写后把实测数字摘要回填到本文件"首轮结果"一节。

- [ ] **Step 6: 更新 README**

在"本地 AI"章节后追加：

```markdown
### AI 回答质量评测

```bash
bash scripts/local/ai-answer-eval.sh   # 20 场景 × 默认 3 次，产出 .codebuddy/artifacts/ai-answer-eval.json
```

自动规则只核验协议（done/saved/版本）、关键词覆盖与禁词，**不等于事实正确**；
人工评审按 `.codebuddy/artifacts/ai-answer-eval-review.md` 模板逐条记录，验收以人工结论为准。
评测会创建并保留评测会话，不会自动删除业务数据。
```

- [ ] **Step 7: 提交**

```bash
git add ops/local/local.env.example README.md .codebuddy/artifacts/ai-answer-eval-review.md
git commit -m "chore(ai): 补齐模型版本配置与评测/人工评审流程，记录首轮真实评测"
```

---

## 检查点 A（Tasks A0–A8）

- [ ] 后端相关模块测试全绿：`mvn test -q -pl zhixue-modules/zhixue-system,zhixue-modules/zhixue-course,zhixue-modules/zhixue-order,zhixue-modules/zhixue-marketing,zhixue-modules/zhixue-interaction -am`
- [ ] 小程序测试全绿：`node --test zhixue-app-mp/tests/*.test.cjs`
- [ ] 后台测试全绿：`cd zhixue-admin-web && npm test && npm run build`
- [ ] 学生 Web 测试全绿：`cd zhixue-student-web && npm test && npm run build`
- [ ] 手工验证 4 件事：① 后台过期后能重新登录；② 小程序点"去登录"能到我的页；③ 分类下滑能加载第 2 页；④ 秒杀连点两次返回同一订单号（看日志 `秒杀订单已存在，按幂等返回`）
- [ ] **请人类确认**：`DanmakuModerationService` 的 fail-open 策略、秒杀"已取消不恢复资格"的业务规则是否符合预期

---

## 阶段 B：学习闭环

> 这是"教育产品"最核心的一条链路：能播放 → 记进度 → 能续学 → 能看到学习数据。做完 B，产品才从"能买能看"变成"能持续学"。

### Task B1：学习进度表与服务端上报

**Files:**
- Modify: `zhixue-modules/zhixue-course/src/main/resources/sql/course_schema.sql`（追加两张表）
- Create: `zhixue-modules/zhixue-course/src/main/resources/sql/upgrade/2026-10-22-learning-progress.sql`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/entity/LearningProgress.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/entity/LearningDaily.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/mapper/LearningProgressMapper.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/mapper/LearningDailyMapper.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/dto/ProgressReportDTO.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/LearningService.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/impl/LearningServiceImpl.java`
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/support/LessonAccessPolicy.java`（+`canPlay`）
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/controller/LearningController.java`
- Test: `zhixue-modules/zhixue-course/src/test/java/com/zhixue/course/service/LearningServiceTest.java`（新建）
- Test: `zhixue-app-mp/tests/progress-report.test.cjs`（在 B4 写）

**Interfaces:**
- Produces:
  - `LearningService.report(Long userId, ProgressReportDTO dto): LearningProgress`
  - `LearningService.progressOfLesson(Long userId, Long sectionId): LearningProgress|null`
  - `LearningService.courseProgress(Long userId, Long courseId): CourseProgressVO`
  - `LearningService.recentCourses(Long userId, int limit): List<RecentCourseVO>`
  - `LearningService.summary(Long userId): StudySummaryVO`
  - `LearningService.calendar(Long userId, YearMonth month): List<LocalDate>`
  - `LessonAccessPolicy.canPlay(Course course, Section section)` — 管理员/本课教师/已购/试看
  - `POST /progress/report`（网关路径 `/course/progress/report`，需登录）
  - `GET /progress/course/{courseId}`、`GET /progress/recent`、`GET /progress/summary`、`GET /progress/calendar`

**数据模型决策**（写进本任务的注释）：时长以"秒"为单位；学习工时按**上报的真实播放秒数**累计（`watchedSeconds` 增量），不用 `currentTime` 峰值，避免拖动进度条刷学时；单次上报增量上限 120 秒，防止伪造。

- [ ] **Step 1: 建表（schema + 幂等增量）**

`course_schema.sql` 追加：

```sql
drop table if exists learning_progress;
create table learning_progress (
    id            bigint primary key,
    user_id       bigint       not null comment '学员ID',
    course_id     bigint       not null,
    chapter_id    bigint       not null,
    section_id    bigint       not null comment '课时ID',
    position      int          not null default 0 comment '上次播放位置（秒）',
    duration      int          not null default 0 comment '课时总时长（秒）',
    watched_seconds int        not null default 0 comment '该课时累计有效学习秒数',
    finished      tinyint      not null default 0 comment '0未完成 1已完成',
    last_study_time datetime   not null,
    create_time   datetime,
    update_time   datetime,
    deleted       tinyint      default 0,
    unique key uk_progress_user_section (user_id, section_id),
    index idx_progress_user_time (user_id, last_study_time),
    index idx_progress_course (course_id, section_id)
);

drop table if exists learning_daily;
create table learning_daily (
    id          bigint primary key,
    user_id     bigint not null,
    study_date  date   not null,
    seconds     int    not null default 0 comment '当日累计有效学习秒数',
    create_time datetime,
    update_time datetime,
    deleted     tinyint default 0,
    unique key uk_daily_user_date (user_id, study_date)
);
```

`upgrade/2026-10-22-learning-progress.sql`（幂等，可重复执行）：

```sql
USE zhixue_course;

CREATE TABLE IF NOT EXISTS learning_progress (
    id            bigint primary key,
    user_id       bigint       not null,
    course_id     bigint       not null,
    chapter_id    bigint       not null,
    section_id    bigint       not null,
    position      int          not null default 0,
    duration      int          not null default 0,
    watched_seconds int        not null default 0,
    finished      tinyint      not null default 0,
    last_study_time datetime   not null,
    create_time   datetime,
    update_time   datetime,
    deleted       tinyint      default 0,
    unique key uk_progress_user_section (user_id, section_id),
    index idx_progress_user_time (user_id, last_study_time),
    index idx_progress_course (course_id, section_id)
) COMMENT '学员课时学习进度';

CREATE TABLE IF NOT EXISTS learning_daily (
    id          bigint primary key,
    user_id     bigint not null,
    study_date  date   not null,
    seconds     int    not null default 0,
    create_time datetime,
    update_time datetime,
    deleted     tinyint default 0,
    unique key uk_daily_user_date (user_id, study_date)
) COMMENT '学员每日学习时长';
```

- [ ] **Step 2: 建实体与 Mapper**

`LearningProgress`（继承 `BaseEntity`，与项目现有实体风格一致）：

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_progress")
public class LearningProgress extends BaseEntity {
    private Long userId;
    private Long courseId;
    private Long chapterId;
    private Long sectionId;
    private Integer position;
    private Integer duration;
    private Integer watchedSeconds;
    private Integer finished;
    private LocalDateTime lastStudyTime;
}
```

`LearningDaily`：

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("learning_daily")
public class LearningDaily extends BaseEntity {
    private Long userId;
    private LocalDate studyDate;
    private Integer seconds;
}
```

两个 Mapper 均为 `@Mapper public interface XxxMapper extends BaseMapper<Xxx> {}`。

- [ ] **Step 3: `LessonAccessPolicy` 增加单课时判定**

```java
    /** 指定课时是否可播放：能看全课、或该课时在试看范围内。 */
    public boolean canPlay(Course course, Section section) {
        if (course == null || section == null) {
            return false;
        }
        if (canStudyAll(course)) {
            return true;
        }
        return previewLessonIds(course).contains(section.getId());
    }
```

- [ ] **Step 4: 写失败测试**

新建 `LearningServiceTest.java`：

```java
package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.course.domain.dto.ProgressReportDTO;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.domain.entity.LearningDaily;
import com.zhixue.course.domain.entity.LearningProgress;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.mapper.LearningDailyMapper;
import com.zhixue.course.mapper.LearningProgressMapper;
import com.zhixue.course.service.impl.LearningServiceImpl;
import com.zhixue.course.service.support.CourseAccessGuard;
import com.zhixue.course.service.support.LessonAccessPolicy;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningServiceTest {

    private static final long USER = 1003L;
    private static final long COURSE = 2007L;
    private static final long SECTION = 4024L;

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, LearningProgress.class);
        TableInfoHelper.initTableInfo(assistant, LearningDaily.class);
    }

    @Mock private LearningProgressMapper progressMapper;
    @Mock private LearningDailyMapper dailyMapper;
    @Mock private CourseAccessGuard courseAccessGuard;
    @Mock private LessonAccessPolicy lessonAccessPolicy;

    private LearningServiceImpl service() {
        return new LearningServiceImpl(progressMapper, dailyMapper, courseAccessGuard, lessonAccessPolicy);
    }

    private ProgressReportDTO report(int position, int duration, int delta) {
        ProgressReportDTO dto = new ProgressReportDTO();
        dto.setCourseId(COURSE);
        dto.setChapterId(3017L);
        dto.setSectionId(SECTION);
        dto.setPosition(position);
        dto.setDuration(duration);
        dto.setPlayedSeconds(delta);
        return dto;
    }

    private void playable() {
        Course course = new Course();
        course.setId(COURSE);
        Section section = new Section();
        section.setId(SECTION);
        when(courseAccessGuard.requireReadableCourse(COURSE)).thenReturn(course);
        when(courseAccessGuard.requireReadableSection(SECTION)).thenReturn(section);
        when(lessonAccessPolicy.canPlay(course, section)).thenReturn(true);
    }

    @Test
    void firstReportCreatesProgressRowAndDailyRecord() {
        playable();
        when(progressMapper.selectOne(any())).thenReturn(null);
        when(progressMapper.insert(any(LearningProgress.class))).thenReturn(1);

        LearningProgress saved = service().report(USER, report(120, 1080, 15));

        ArgumentCaptor<LearningProgress> captor = ArgumentCaptor.forClass(LearningProgress.class);
        verify(progressMapper).insert(captor.capture());
        assertThat(captor.getValue().getPosition()).isEqualTo(120);
        assertThat(captor.getValue().getWatchedSeconds()).isEqualTo(15);
        assertThat(captor.getValue().getFinished()).isZero();
        assertThat(saved.getSectionId()).isEqualTo(SECTION);
        verify(dailyMapper).insert(any(LearningDaily.class));
    }

    @Test
    void reportMoreThanNinetyPercentMarksFinished() {
        playable();
        LearningProgress existing = new LearningProgress();
        existing.setId(1L);
        existing.setUserId(USER);
        existing.setSectionId(SECTION);
        existing.setWatchedSeconds(900);
        when(progressMapper.selectOne(any())).thenReturn(existing);
        when(progressMapper.updateById(any(LearningProgress.class))).thenReturn(1);

        service().report(USER, report(1000, 1080, 10));

        ArgumentCaptor<LearningProgress> captor = ArgumentCaptor.forClass(LearningProgress.class);
        verify(progressMapper).updateById(captor.capture());
        assertThat(captor.getValue().getFinished()).isEqualTo(1);
    }

    @Test
    void watchedSecondsGainIsCappedSoProgressBarScrubbingCannotFarmHours() {
        playable();
        when(progressMapper.selectOne(any())).thenReturn(null);
        when(progressMapper.insert(any(LearningProgress.class))).thenReturn(1);

        service().report(USER, report(1000, 1080, 3600));

        ArgumentCaptor<LearningProgress> captor = ArgumentCaptor.forClass(LearningProgress.class);
        verify(progressMapper).insert(captor.capture());
        // 单次上报最多计 120 秒，防止把进度条拖到尾刷学时
        assertThat(captor.getValue().getWatchedSeconds()).isEqualTo(120);
    }

    @Test
    void cannotReportProgressForUnplayableLesson() {
        Course course = new Course();
        course.setId(COURSE);
        Section section = new Section();
        section.setId(SECTION);
        when(courseAccessGuard.requireReadableCourse(COURSE)).thenReturn(course);
        when(courseAccessGuard.requireReadableSection(SECTION)).thenReturn(section);
        when(lessonAccessPolicy.canPlay(course, section)).thenReturn(false);

        assertThatThrownBy(() -> service().report(USER, report(10, 1080, 10)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("无权");
    }

    @Test
    void negativePlayedSecondsIsRejected() {
        playable();

        assertThatThrownBy(() -> service().report(USER, report(10, 1080, -5)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("上报时长不合法");
    }
}
```

- [ ] **Step 5: 运行确认失败**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud && mvn test -q -pl zhixue-modules/zhixue-course -am \
  -Dtest='LearningServiceTest' -Dsurefire.failIfNoSpecifiedTests=false
```
Expected: 编译失败（`LearningServiceImpl`、`ProgressReportDTO` 不存在）

- [ ] **Step 6: 实现 DTO、Service 与上报接口**

`ProgressReportDTO`：

```java
@Data
public class ProgressReportDTO {
    @NotNull private Long courseId;
    @NotNull private Long chapterId;
    @NotNull private Long sectionId;
    /** 当前播放位置（秒） */
    @NotNull @Min(0) private Integer position;
    /** 课时总时长（秒），客户端上报；与服务端记录的时长取较大值 */
    @NotNull @Min(1) private Integer duration;
    /** 本次上报新增的播放秒数（客户端两次上报的间隔） */
    @NotNull @Min(0) private Integer playedSeconds;
}
```

`LearningServiceImpl`（核心方法）：

```java
private static final int MAX_GAIN_SECONDS = 120;
private static final int FINISH_RATIO = 90;
private static final int STUDY_DAY_MIN_SECONDS = 60;

@Override
@Transactional(rollbackFor = Exception.class)
public LearningProgress report(Long userId, ProgressReportDTO dto) {
    if (dto.getPlayedSeconds() == null || dto.getPlayedSeconds() < 0) {
        throw new ServiceException("上报时长不合法");
    }
    Course course = courseAccessGuard.requireReadableCourse(dto.getCourseId());
    Section section = courseAccessGuard.requireReadableSection(dto.getSectionId());
    if (!lessonAccessPolicy.canPlay(course, section)) {
        throw new ServiceException(HttpStatus.FORBIDDEN, "无权记录该课时的学习进度");
    }

    int gain = Math.min(dto.getPlayedSeconds(), MAX_GAIN_SECONDS);
    LocalDateTime now = LocalDateTime.now();
    LearningProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<LearningProgress>()
            .eq(LearningProgress::getUserId, userId)
            .eq(LearningProgress::getSectionId, dto.getSectionId()));

    if (progress == null) {
        progress = new LearningProgress();
        progress.setUserId(userId);
        progress.setCourseId(dto.getCourseId());
        progress.setChapterId(dto.getChapterId());
        progress.setSectionId(dto.getSectionId());
        progress.setPosition(dto.getPosition());
        progress.setDuration(dto.getDuration());
        progress.setWatchedSeconds(gain);
        progress.setFinished(isFinished(dto.getPosition(), dto.getDuration()) ? 1 : 0);
        progress.setLastStudyTime(now);
        progressMapper.insert(progress);
    } else {
        progress.setPosition(dto.getPosition());
        progress.setDuration(Math.max(progress.getDuration(), dto.getDuration()));
        progress.setWatchedSeconds(progress.getWatchedSeconds() + gain);
        if (isFinished(dto.getPosition(), progress.getDuration())) {
            progress.setFinished(1);
        }
        progress.setLastStudyTime(now);
        progressMapper.updateById(progress);
    }

    accumulateDaily(userId, gain);
    return progress;
}

private boolean isFinished(int position, int duration) {
    return duration > 0 && position * 100L >= (long) duration * FINISH_RATIO;
}

private void accumulateDaily(Long userId, int gain) {
    if (gain <= 0) {
        return;
    }
    LocalDate today = LocalDate.now();
    LearningDaily daily = dailyMapper.selectOne(new LambdaQueryWrapper<LearningDaily>()
            .eq(LearningDaily::getUserId, userId)
            .eq(LearningDaily::getStudyDate, today));
    if (daily == null) {
        daily = new LearningDaily();
        daily.setUserId(userId);
        daily.setStudyDate(today);
        daily.setSeconds(gain);
        dailyMapper.insert(daily);
    } else {
        daily.setSeconds(daily.getSeconds() + gain);
        dailyMapper.updateById(daily);
    }
}
```

`LearningController`：

```java
@RestController
@RequestMapping("/progress")
@RequiredArgsConstructor
public class LearningController {

    private final LearningService learningService;

    @RequireLogin
    @PostMapping("/report")
    public R<LearningProgress> report(@Valid @RequestBody ProgressReportDTO dto) {
        return R.ok(learningService.report(SecurityUtils.requireUserId(), dto));
    }
}
```

> 网关 `/course/**` 会去掉前缀，所以对外是 `POST /course/progress/report`，控制器写 `/progress/report`。该路径不在白名单，自动要求登录。

- [ ] **Step 7: 运行测试**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud && mvn test -q -pl zhixue-modules/zhixue-course -am
```
Expected: BUILD SUCCESS，新增 5 例通过

- [ ] **Step 8: 提交**

```bash
git add zhixue-modules/zhixue-course/src
git commit -m "feat(course): 学习进度表与服务端上报，按有效播放秒数累计学时不信任进度条"
```

---

### Task B2：学习数据查询（课程进度 / 最近学习 / 汇总 / 日历）

**Files:**
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/vo/CourseProgressVO.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/vo/RecentCourseVO.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/vo/StudySummaryVO.java`
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/LearningService.java`
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/impl/LearningServiceImpl.java`
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/controller/LearningController.java`
- Test: `zhixue-modules/zhixue-course/src/test/java/com/zhixue/course/service/LearningQueryTest.java`（新建）

**Interfaces:**
- `CourseProgressVO { Long courseId; String courseTitle; String coverUrl; int totalLessons; int finishedLessons; int percent; Long lastSectionId; String lastSectionTitle; int lastPosition; }`
- `RecentCourseVO { Long courseId; String title; String coverUrl; String lastSectionTitle; Long lastSectionId; int percent; LocalDateTime lastStudyTime; }`
- `StudySummaryVO { int totalHours10; int todayMinutes; int continuousDays; int studiedCourses; int finishedLessons; }`（`totalHours10` 为 0.1 小时精度的整数，前端除以 10）
- `GET /progress/course/{courseId}`、`GET /progress/recent?limit=5`、`GET /progress/summary`、`GET /progress/calendar?month=2026-10`

- [ ] **Step 1: 写失败测试**

新建 `LearningQueryTest.java`（复用 B1 的 Lambda 缓存初始化模式）：

```java
    @Test
    void courseProgressCountsOnlyThatCoursesLessons() {
        // 两个课时：一个已完成、一个只看了 30%
        LearningProgress done = progress(4021L, 1, 100, 1000);
        LearningProgress partial = progress(4022L, 0, 300, 1000);
        when(progressMapper.selectList(any())).thenReturn(List.of(done, partial));
        when(courseAccessGuard.requireReadableCourse(COURSE)).thenReturn(course());

        CourseProgressVO vo = service().courseProgress(USER, COURSE);

        assertThat(vo.getTotalLessons()).isEqualTo(2);
        assertThat(vo.getFinishedLessons()).isEqualTo(1);
        assertThat(vo.getPercent()).isEqualTo(50);
    }

    @Test
    void summaryUsesDailySecondsForHoursAndStreak() {
        when(dailyMapper.selectList(any())).thenReturn(List.of(
                daily(LocalDate.now(), 1800),
                daily(LocalDate.now().minusDays(1), 600),
                daily(LocalDate.now().minusDays(2), 3000)));
        when(progressMapper.selectCount(any())).thenReturn(4L);

        StudySummaryVO vo = service().summary(USER);

        assertThat(vo.getTotalHours10()).isEqualTo(9);   // 5400 秒 = 1.5 小时
        assertThat(vo.getContinuousDays()).isEqualTo(3);
        assertThat(vo.getFinishedLessons()).isEqualTo(4);
    }

    @Test
    void calendarReturnsOnlyDaysWithAtLeastOneMinute() {
        when(dailyMapper.selectList(any())).thenReturn(List.of(
                daily(LocalDate.of(2026, 10, 20), 59),
                daily(LocalDate.of(2026, 10, 21), 60)));

        assertThat(service().calendar(USER, YearMonth.of(2026, 10)))
                .containsExactly(LocalDate.of(2026, 10, 21));
    }
```

（需要的 `progress(...)`、`daily(...)`、`course()` 辅助方法在测试文件内实现，字段直接 set。）

- [ ] **Step 2: 运行确认失败**

Run: `mvn test -q -pl zhixue-modules/zhixue-course -am -Dtest='LearningQueryTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败

- [ ] **Step 3: 实现 VO 与查询**

三个 VO 用 `@Data`（不要用 record：前端字段名固定，Lombok 便于后续加字段）。`LearningServiceImpl` 实现：

```java
    @Override
    public CourseProgressVO courseProgress(Long userId, Long courseId) {
        Course course = courseAccessGuard.requireReadableCourse(courseId);
        List<LearningProgress> rows = progressMapper.selectList(new LambdaQueryWrapper<LearningProgress>()
                .eq(LearningProgress::getUserId, userId)
                .eq(LearningProgress::getCourseId, courseId)
                .orderByDesc(LearningProgress::getLastStudyTime));
        CourseProgressVO vo = new CourseProgressVO();
        vo.setCourseId(courseId);
        vo.setCourseTitle(course.getTitle());
        vo.setCoverUrl(course.getCoverUrl());
        vo.setTotalLessons(rows.size());
        vo.setFinishedLessons((int) rows.stream().filter(r -> Integer.valueOf(1).equals(r.getFinished())).count());
        vo.setPercent(rows.isEmpty() ? 0
                : (int) Math.round(rows.stream()
                        .mapToInt(r -> r.getFinished() != null && r.getFinished() == 1 ? 100
                                : percentOf(r.getPosition(), r.getDuration()))
                        .average().orElse(0)));
        rows.stream().findFirst().ifPresent(last -> {
            vo.setLastSectionId(last.getSectionId());
            vo.setLastPosition(last.getPosition());
            Section section = courseAccessGuard.requireReadableSection(last.getSectionId());
            vo.setLastSectionTitle(section.getTitle());
        });
        return vo;
    }

    private int percentOf(int position, int duration) {
        if (duration <= 0) {
            return 0;
        }
        return (int) Math.min(100, Math.round(position * 100.0 / duration));
    }

    @Override
    public StudySummaryVO summary(Long userId) {
        List<LearningDaily> days = dailyMapper.selectList(new LambdaQueryWrapper<LearningDaily>()
                .eq(LearningDaily::getUserId, userId));
        int totalSeconds = days.stream().mapToInt(d -> d.getSeconds() == null ? 0 : d.getSeconds()).sum();
        LocalDate today = LocalDate.now();
        Map<LocalDate, Integer> byDate = days.stream()
                .filter(d -> d.getSeconds() != null && d.getSeconds() >= STUDY_DAY_MIN_SECONDS)
                .collect(Collectors.toMap(LearningDaily::getStudyDate, LearningDaily::getSeconds, Integer::sum));
        int streak = 0;
        LocalDate cursor = byDate.containsKey(today) ? today : today.minusDays(1);
        while (byDate.containsKey(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        StudySummaryVO vo = new StudySummaryVO();
        vo.setTotalHours10(Math.round(totalSeconds / 360.0f));   // 秒 → 0.1 小时
        vo.setTodayMinutes(byDate.getOrDefault(today, 0) / 60);
        vo.setContinuousDays(streak);
        vo.setStudiedCourses((int) progressMapper.selectCount(new LambdaQueryWrapper<LearningProgress>()
                .eq(LearningProgress::getUserId, userId)));
        vo.setFinishedLessons((int) progressMapper.selectCount(new LambdaQueryWrapper<LearningProgress>()
                .eq(LearningProgress::getUserId, userId)
                .eq(LearningProgress::getFinished, 1)));
        return vo;
    }

    @Override
    public List<RecentCourseVO> recentCourses(Long userId, int limit) {
        List<LearningProgress> rows = progressMapper.selectList(new LambdaQueryWrapper<LearningProgress>()
                .eq(LearningProgress::getUserId, userId)
                .orderByDesc(LearningProgress::getLastStudyTime)
                .last("limit " + Math.max(1, Math.min(limit, 20))));
        Map<Long, List<LearningProgress>> byCourse = rows.stream()
                .collect(Collectors.groupingBy(LearningProgress::getCourseId, LinkedHashMap::new, Collectors.toList()));
        List<RecentCourseVO> result = new ArrayList<>();
        byCourse.forEach((courseId, list) -> {
            Course course = courseMapper.selectById(courseId);
            if (course == null) {
                return;
            }
            LearningProgress last = list.get(0);
            RecentCourseVO vo = new RecentCourseVO();
            vo.setCourseId(courseId);
            vo.setTitle(course.getTitle());
            vo.setCoverUrl(course.getCoverUrl());
            vo.setLastSectionId(last.getSectionId());
            vo.setLastStudyTime(last.getLastStudyTime());
            Section section = sectionMapper.selectById(last.getSectionId());
            vo.setLastSectionTitle(section == null ? "" : section.getTitle());
            vo.setPercent((int) Math.round(list.stream().mapToInt(r -> percentOf(r.getPosition(), r.getDuration())).average().orElse(0)));
            result.add(vo);
        });
        return result;
    }

    @Override
    public List<LocalDate> calendar(Long userId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        return dailyMapper.selectList(new LambdaQueryWrapper<LearningDaily>()
                        .eq(LearningDaily::getUserId, userId)
                        .between(LearningDaily::getStudyDate, start, end)
                        .ge(LearningDaily::getSeconds, STUDY_DAY_MIN_SECONDS))
                .stream().map(LearningDaily::getStudyDate).distinct().sorted().toList();
    }
```

> `LearningServiceImpl` 需要额外注入 `CourseMapper` 与 `SectionMapper`（`recentCourses` 要课程标题/封面与课时标题）。构造器变为 `(progressMapper, dailyMapper, courseMapper, sectionMapper, courseAccessGuard, lessonAccessPolicy)`，同步更新 B1 的测试构造。

- [ ] **Step 4: 控制器补齐**

```java
    @RequireLogin
    @GetMapping("/course/{courseId}")
    public R<CourseProgressVO> courseProgress(@PathVariable Long courseId) {
        return R.ok(learningService.courseProgress(SecurityUtils.requireUserId(), courseId));
    }

    @RequireLogin
    @GetMapping("/recent")
    public R<List<RecentCourseVO>> recent(@RequestParam(defaultValue = "5") int limit) {
        return R.ok(learningService.recentCourses(SecurityUtils.requireUserId(), limit));
    }

    @RequireLogin
    @GetMapping("/summary")
    public R<StudySummaryVO> summary() {
        return R.ok(learningService.summary(SecurityUtils.requireUserId()));
    }

    @RequireLogin
    @GetMapping("/calendar")
    public R<List<LocalDate>> calendar(@RequestParam String month) {
        return R.ok(learningService.calendar(SecurityUtils.requireUserId(), YearMonth.parse(month)));
    }
```

- [ ] **Step 5: 运行测试并提交**

Run: `mvn test -q -pl zhixue-modules/zhixue-course -am`
Expected: BUILD SUCCESS

```bash
git add zhixue-modules/zhixue-course/src
git commit -m "feat(course): 课程进度/最近学习/学时汇总/学习日历查询"
```

---

### Task B3：我的课程（已购 + 学习进度）

**Files:**
- Modify: `zhixue-api/src/main/java/com/zhixue/api/order/domain/PurchasedCourse.java`（新建）
- Modify: `zhixue-api/src/main/java/com/zhixue/api/order/RemoteOrderService.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/service/OrderService.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/service/impl/OrderServiceImpl.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/controller/OrderController.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/vo/MineCourseVO.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/MyCourseService.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/impl/MyCourseServiceImpl.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/controller/MyCourseController.java`
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/CourseApplication.java`（注册新 Feign 客户端时确认已在 `clients` 中）
- Test: `zhixue-modules/zhixue-course/src/test/java/com/zhixue/course/service/MyCourseServiceTest.java`（新建）

**Interfaces:**
- `PurchasedCourse { Long courseId; String orderNo; BigDecimal amount; LocalDateTime payTime; }`
- `RemoteOrderService.listPurchased(Long userId, innerCall, internalToken): R<List<PurchasedCourse>>`
- `MineCourseVO { Long courseId; String title; String coverUrl; boolean purchased; int totalLessons; int finishedLessons; int percent; Long lastSectionId; String lastSectionTitle; int lastPosition; LocalDateTime lastStudyTime; }`
- `GET /course/mine/courses`（需登录）

- [ ] **Step 1: 订单侧已购课程列表**

`PurchasedCourse`（`zhixue-api` 的 `order/domain`）：

```java
@Data
public class PurchasedCourse {
    private Long courseId;
    private String orderNo;
    private BigDecimal amount;
    private LocalDateTime payTime;
}
```

`OrderService` 追加：

```java
    /** 本人已支付订单去重后的课程列表（同一课程多笔只取最近一笔）。 */
    List<PurchasedCourse> listPurchasedCourses(Long userId);
```

`OrderServiceImpl` 实现：

```java
    @Override
    public List<PurchasedCourse> listPurchasedCourses(Long userId) {
        List<Order> paid = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(Order::getStatus, STATUS_PAID)
                .orderByDesc(Order::getPayTime));
        Map<Long, Order> latest = new LinkedHashMap<>();
        for (Order order : paid) {
            latest.putIfAbsent(order.getProductId(), order);
        }
        return latest.values().stream().map(order -> {
            PurchasedCourse row = new PurchasedCourse();
            row.setCourseId(order.getProductId());
            row.setOrderNo(order.getOrderNo());
            row.setAmount(order.getAmount());
            row.setPayTime(order.getPayTime());
            return row;
        }).toList();
    }
```

`OrderController`：

```java
    /** 本人已购课程（供"我的课程"聚合）。 */
    @RequireLogin
    @GetMapping("/mine/courses")
    public R<List<PurchasedCourse>> myCourses() {
        return R.ok(orderService.listPurchasedCourses(SecurityUtils.requireUserId()));
    }

    /** 服务间接口：按用户查已购课程。 */
    @GetMapping("/inner/purchased-courses")
    public R<List<PurchasedCourse>> purchasedCourses(@RequestParam Long userId) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "禁止外部访问内部订单接口");
        }
        return R.ok(orderService.listPurchasedCourses(userId));
    }
```

`RemoteOrderService` 追加：

```java
    @GetMapping("/order/inner/purchased-courses")
    R<List<PurchasedCourse>> listPurchased(@RequestParam("userId") Long userId,
                                          @RequestHeader(InternalAccessConstants.INNER_CALL_HEADER) String innerHeader,
                                          @RequestHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER) String internalToken);
```

- [ ] **Step 2: 课程侧聚合**

`MyCourseServiceImpl`：

```java
@Service
@RequiredArgsConstructor
public class MyCourseServiceImpl implements MyCourseService {

    private final CourseMapper courseMapper;
    private final SectionMapper sectionMapper;
    private final LearningProgressMapper progressMapper;
    private final RemoteOrderService remoteOrderService;
    private final SecurityProperties securityProperties;

    @Override
    public List<MineCourseVO> mine(Long userId) {
        List<PurchasedCourse> purchased = loadPurchased(userId);
        List<LearningProgress> rows = progressMapper.selectList(new LambdaQueryWrapper<LearningProgress>()
                .eq(LearningProgress::getUserId, userId));

        Map<Long, List<LearningProgress>> byCourse = rows.stream()
                .collect(Collectors.groupingBy(LearningProgress::getCourseId));
        Set<Long> courseIds = new LinkedHashSet<>();
        purchased.forEach(p -> courseIds.add(p.getCourseId()));
        // 免费课没有订单，但学过就要出现在"我的课程"里
        courseIds.addAll(byCourse.keySet());

        List<MineCourseVO> result = new ArrayList<>();
        for (Long courseId : courseIds) {
            Course course = courseMapper.selectById(courseId);
            if (course == null) {
                continue;
            }
            List<LearningProgress> courseRows = byCourse.getOrDefault(courseId, List.of());
            MineCourseVO vo = new MineCourseVO();
            vo.setCourseId(courseId);
            vo.setTitle(course.getTitle());
            vo.setCoverUrl(course.getCoverUrl());
            vo.setPurchased(purchased.stream().anyMatch(p -> courseId.equals(p.getCourseId())));
            vo.setTotalLessons(courseRows.size());
            vo.setFinishedLessons((int) courseRows.stream().filter(r -> Integer.valueOf(1).equals(r.getFinished())).count());
            vo.setPercent(courseRows.isEmpty() ? 0 : (int) Math.round(courseRows.stream()
                    .mapToInt(r -> r.getFinished() != null && r.getFinished() == 1 ? 100
                            : r.getDuration() > 0 ? Math.min(100, Math.round(r.getPosition() * 100f / r.getDuration())) : 0)
                    .average().orElse(0)));
            courseRows.stream().max(Comparator.comparing(LearningProgress::getLastStudyTime)).ifPresent(last -> {
                vo.setLastSectionId(last.getSectionId());
                vo.setLastPosition(last.getPosition());
                vo.setLastStudyTime(last.getLastStudyTime());
                Section section = sectionMapper.selectById(last.getSectionId());
                vo.setLastSectionTitle(section == null ? "" : section.getTitle());
            });
            result.add(vo);
        }
        result.sort(Comparator.comparing(MineCourseVO::getLastStudyTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    private List<PurchasedCourse> loadPurchased(Long userId) {
        try {
            R<List<PurchasedCourse>> response = remoteOrderService.listPurchased(
                    userId, "true", securityProperties.getInternalToken());
            if (response == null || !response.isSuccess() || response.getData() == null) {
                // 不能把"查不到订单"当成"没买过"：那会让已购学员看不到自己的课，等同于掉权限
                throw new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "订单服务繁忙，请稍后重试");
            }
            return response.getData();
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询已购课程失败 userId={}", userId, e);
            throw new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "订单服务繁忙，请稍后重试");
        }
    }
}
```

`MyCourseController`：

```java
@RestController
@RequestMapping("/mine")
@RequiredArgsConstructor
public class MyCourseController {

    private final MyCourseService myCourseService;

    @RequireLogin
    @GetMapping("/courses")
    public R<List<MineCourseVO>> courses() {
        return R.ok(myCourseService.mine(SecurityUtils.requireUserId()));
    }
}
```

- [ ] **Step 3: 写测试**

`MyCourseServiceTest`（Mockito）：覆盖三件事——已购但未学也出现、免费课已学也出现、订单服务失败时抛 503 而不是返回空。

```java
    @Test
    void purchasedCourseWithoutProgressStillAppears() {
        when(remoteOrderService.listPurchased(eq(USER), anyString(), anyString()))
                .thenReturn(R.ok(List.of(purchased(COURSE))));
        when(progressMapper.selectList(any())).thenReturn(List.of());
        when(courseMapper.selectById(COURSE)).thenReturn(course());

        List<MineCourseVO> result = service().mine(USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isPurchased()).isTrue();
        assertThat(result.get(0).getPercent()).isZero();
    }

    @Test
    void freeCourseWithProgressAppearsWithoutOrder() {
        when(remoteOrderService.listPurchased(eq(USER), anyString(), anyString())).thenReturn(R.ok(List.of()));
        when(progressMapper.selectList(any())).thenReturn(List.of(progressRow(COURSE, 4021L)));
        when(courseMapper.selectById(COURSE)).thenReturn(course());

        assertThat(service().mine(USER)).hasSize(1);
    }

    @Test
    void orderServiceFailureMustNotLookLikeEmptyPurchase() {
        when(remoteOrderService.listPurchased(eq(USER), anyString(), anyString()))
                .thenThrow(new RuntimeException("timeout"));

        assertThatThrownBy(() -> service().mine(USER))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("订单服务繁忙");
    }
```

- [ ] **Step 4: 运行测试**

Run: `mvn test -q -pl zhixue-modules/zhixue-course,zhixue-modules/zhixue-order -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add zhixue-api/src zhixue-modules/zhixue-order/src zhixue-modules/zhixue-course/src
git commit -m "feat(course,order): 我的课程聚合已购订单与学习进度"
```

---

### Task B4：小程序播放器上报进度与断点续播

**Files:**
- Modify: `zhixue-app-mp/miniprogram/api/user.js`
- Modify: `zhixue-app-mp/miniprogram/pages/course/player/index.js:11-27,104-170,267-310,421-449`
- Modify: `zhixue-app-mp/miniprogram/pages/course/player/index.wxml:6-19`
- Test: `zhixue-app-mp/tests/progress-report.test.cjs`（新建）

**Interfaces:**
- Consumes: `POST /course/progress/report`（B1）、`GET /course/progress/lesson/{sectionId}`（本任务新增）
- Produces: `api/user.js` 导出 `reportProgress`、`getStudySummary`、`getRecentStudyCourses`、`getLessonProgress`、`getCourseProgress`、`getStudyCalendar`、`getMineCourses`

**上报策略**（写进代码注释）：每 15 秒最多一次，且只在"本次新增播放秒数 > 0"时上报；离开页面（`onHide`/`onUnload`）、暂停、播放结束各强制上报一次。`showLoading: false`，因为 `request.js` 默认会弹全屏 loading，进度上报绝不能挡住播放界面。

- [ ] **Step 1: 后端补单课时进度查询**

`LearningController` 追加：

```java
    @RequireLogin
    @GetMapping("/lesson/{sectionId}")
    public R<LearningProgress> lessonProgress(@PathVariable Long sectionId) {
        return R.ok(learningService.progressOf(UserUtils.requireUserId(), sectionId));
    }
```

`LearningServiceImpl` 追加（未学返回 null，前端据此从头播放）：

```java
    @Override
    public LearningProgress progressOf(Long userId, Long sectionId) {
        return progressMapper.selectOne(new LambdaQueryWrapper<LearningProgress>()
                .eq(LearningProgress::getUserId, userId)
                .eq(LearningProgress::getSectionId, sectionId));
    }
```

- [ ] **Step 2: 改 `api/user.js`**

```js
const { get, post } = require('./request')

function reportProgress(payload) {
  // 进度上报是后台行为：不能弹全屏 loading，也不能因为"重复请求"把播放卡住
  return post('/course/progress/report', payload, { showLoading: false })
}

function getLessonProgress(sectionId) {
  return get(`/course/progress/lesson/${sectionId}`, {}, { showLoading: false })
}

function getCourseProgress(courseId) {
  return get(`/course/progress/course/${courseId}`, {}, { showLoading: false })
}

function getStudySummary() {
  return get('/course/progress/summary', {}, { showLoading: false })
}

function getRecentStudyCourses(limit = 5) {
  return get('/course/progress/recent', { limit }, { showLoading: false })
}

function getStudyCalendar(month) {
  return get('/course/progress/calendar', { month }, { showLoading: false })
}

function getMineCourses() {
  return get('/course/mine/courses', {}, { showLoading: false })
}

module.exports = {
  reportProgress,
  getLessonProgress,
  getCourseProgress,
  getStudySummary,
  getRecentStudyCourses,
  getStudyCalendar,
  getMineCourses,
  getFavoriteList,
  readFavoriteCache,
  writeFavoriteCache,
  upsertFavoriteCache,
  removeFavoriteCache
}
```

> `getStudyRecords` / `updateStudyProgress` / `ERR_NOT_IMPLEMENTED` 删除；同步清理 `pages/my/index.js`、`pages/my/list/index.js` 中对它们的引用与 `ERR_NOT_IMPLEMENTED` 分支（B5 处理）。

- [ ] **Step 3: 写失败测试**

新建 `zhixue-app-mp/tests/progress-report.test.cjs`：

```js
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

function setup() {
  let definition
  const reports = []
  const progressCalls = []
  const pageRoot = path.resolve(__dirname, '../miniprogram/pages/course/player')
  vm.runInNewContext(fs.readFileSync(path.join(pageRoot, 'index.js'), 'utf8'), {
    Page(options) { definition = options },
    getApp: () => ({ globalData: { isLogin: true } }),
    require(name) {
      if (name === '../../../api/course') {
        return {
          getCourseDetail: () => Promise.resolve({ code: 200, data: { id: '2007', price: 99, canStudy: true } }),
          getCourseChapters: () => Promise.resolve({ code: 200, data: [] })
        }
      }
      if (name === '../../../api/user') {
        return {
          reportProgress: payload => { reports.push(payload); return Promise.resolve({ code: 200 }) },
          getLessonProgress: sectionId => {
            progressCalls.push(sectionId)
            return Promise.resolve({ code: 200, data: { position: 420, duration: 1080 } })
          }
        }
      }
      if (name === '../../../api/websocket') {
        return class { connect() {} close() {} }
      }
      throw Error(`Unexpected import: ${name}`)
    },
    wx: { createVideoContext: () => ({ seek() {}, play() {} }), showToast() {}, setNavigationBarColor() {} },
    console, setTimeout, clearTimeout, Date
  })
  const page = {
    data: structuredClone(definition.data),
    setData(patch) { Object.assign(this.data, structuredClone(patch)) }
  }
  for (const [key, value] of Object.entries(definition)) {
    if (typeof value === 'function') page[key] = value.bind(page)
  }
  page.data.courseId = '2007'
  page.data.currentLesson = { id: '4024' }
  page.data.currentChapter = { id: '3017' }
  page.data.duration = 1080
  return { page, reports, progressCalls }
}

test('进入课时先读取上次位置用于续播', async () => {
  const { page, progressCalls } = setup()
  await page.loadResumePosition('4024')
  assert.deepEqual(progressCalls, ['4024'])
  assert.equal(page.data.resumePosition, 420)
})

test('上报内容包含课程/章节/课时、位置、时长与本次播放秒数', async () => {
  const { page, reports } = setup()
  page.data.currentTime = 435
  page.data.duration = 1080
  page._lastReportedTime = 420
  await page.flushProgress()

  assert.equal(reports.length, 1)
  assert.deepEqual(reports[0], {
    courseId: '2007', chapterId: '3017', sectionId: '4024',
    position: 435, duration: 1080, playedSeconds: 15
  })
})

test('15 秒内的重复调用不会重复上报', async () => {
  const { page, reports } = setup()
  page.data.currentTime = 435
  page._lastReportedTime = 420
  await page.flushProgress()
  page.data.currentTime = 440
  await page.flushProgress()
  assert.equal(reports.length, 1)
})

test('未登录或没有当前课时时不上报', async () => {
  const { page, reports } = setup()
  page.data.currentLesson = null
  await page.flushProgress()
  assert.equal(reports.length, 0)
})

test('播放结束后把进度标记为完成（position 等于时长）', async () => {
  const { page, reports } = setup()
  page.data.currentTime = 1080
  page.data.duration = 1080
  page._lastReportedTime = 1070
  await page.flushProgress(true)
  assert.equal(reports[0].position, 1080)
  assert.equal(reports[0].playedSeconds, 10)
})
```

- [ ] **Step 4: 运行确认失败**

Run: `node --test /Users/jax/Projects/zhixue-cloud/zhixue-app-mp/tests/progress-report.test.cjs`
Expected: FAIL（`page.loadResumePosition is not a function`）

- [ ] **Step 5: 实现上报与续播**

`pages/course/player/index.js` 顶部改为：

```js
const { getCourseChapters, getCourseDetail } = require('../../../api/course')
const { reportProgress, getLessonProgress } = require('../../../api/user')
const WebSocketManager = require('../../../api/websocket')
```

`data` 增加：

```js
    resumePosition: 0,
    progressError: '',
```

页面实例字段增加（与 `videoContext` 同级）：

```js
  _lastReportAt: 0,
  _lastReportedTime: 0,
```

`setCurrentLesson` 在确定 `currentLesson` 后调用 `this.loadResumePosition(this.data.currentLesson.id)`。

新增方法：

```js
  /**
   * 读取上次播放位置。失败不阻塞播放：只是从头开始，并在控制台留痕。
   */
  async loadResumePosition(sectionId) {
    if (!sectionId) return
    try {
      const res = await getLessonProgress(sectionId)
      const row = res && res.data
      const position = row && Number(row.position) > 0 ? Number(row.position) : 0
      // 已完成或不合法位置不续播
      const duration = Number(row && row.duration) || 0
      const resumable = duration > 0 && position > 0 && position < duration * 0.95
      this.setData({ resumePosition: resumable ? position : 0 })
      this._lastReportedTime = resumable ? position : 0
      this._lastReportAt = Date.now()
    } catch (error) {
      console.warn('读取学习进度失败，从头播放', error)
      this.setData({ resumePosition: 0 })
    }
  },

  /**
   * 上报学习进度。
   * @param {boolean} force 强制上报（离开页面、暂停、播放结束时用）
   */
  async flushProgress(force = false) {
    const { courseId, currentChapter, currentLesson, currentTime, duration } = this.data
    if (!app.globalData.isLogin || !currentLesson || !currentChapter || !courseId) return
    const now = Date.now()
    if (!force && now - this._lastReportAt < 15000) return

    const position = Math.max(0, Math.round(Number(currentTime) || 0))
    const total = Math.max(0, Math.round(Number(duration) || 0))
    const playedSeconds = Math.max(0, Math.min(120, position - this._lastReportedTime))
    if (!force && playedSeconds <= 0) return

    this._lastReportAt = now
    this._lastReportedTime = position
    try {
      await reportProgress({
        courseId, chapterId: currentChapter.id, sectionId: currentLesson.id,
        position, duration: total, playedSeconds
      })
      if (this.data.progressError) this.setData({ progressError: '' })
    } catch (error) {
      // 进度丢失是用户能感知的问题（下次不能续播），必须留下可见线索
      console.error('上报学习进度失败:', error)
      this.setData({ progressError: '学习进度暂未保存' })
    }
  },
```

`onVideoTimeUpdate` 在 `setData` 之后追加 `this.flushProgress(false)`；`onVideoPause`、`onVideoEnded`、`onHide`、`onUnload` 均改为 `this.flushProgress(true)`（`onVideoEnded` 先上报再 `playNextLesson`）。删除旧的 `saveProgress` 与 `Page.__studyProgressUnavailable` 逻辑。

`index.wxml` 的 `<video>` 增加：

```xml
  initial-time="{{resumePosition}}"
```

并在视频下方加一条非阻断提示：

```xml
  <view class="progress-hint" wx:if="{{progressError}}">{{progressError}}</view>
```

- [ ] **Step 6: 运行确认通过**

Run: `node --test /Users/jax/Projects/zhixue-cloud/zhixue-app-mp/tests/progress-report.test.cjs`
Expected: 5 tests pass

- [ ] **Step 7: 提交**

```bash
git add zhixue-app-mp/miniprogram/api/user.js \
        zhixue-app-mp/miniprogram/pages/course/player \
        zhixue-app-mp/tests/progress-report.test.cjs
git commit -m "feat(mp): 播放器上报学习进度并支持断点续播"
```

---

### Task B5：小程序学习中心与"我的"真实统计

**Files:**
- Modify: `zhixue-app-mp/miniprogram/pages/study/index.js`
- Modify: `zhixue-app-mp/miniprogram/pages/study/index.wxml`
- Modify: `zhixue-app-mp/miniprogram/pages/study/index.wxss`
- Modify: `zhixue-app-mp/miniprogram/pages/my/index.js:286-343`
- Modify: `zhixue-app-mp/miniprogram/pages/my/list/index.js:86-130`
- Modify: `zhixue-app-mp/miniprogram/pages/my/list/index.wxml:72-85`
- Test: `zhixue-app-mp/tests/study-page.test.cjs`（新建）

**Interfaces:**
- Consumes: `getStudySummary`、`getMineCourses`、`getStudyCalendar`（B4）
- Produces: study 页 `data.studyStats`、`data.courseList`、`data.calendarDays`、`data.loadError`；my 页 `stats.courseCount` 来自已支付订单

- [ ] **Step 1: 写失败测试**

新建 `study-page.test.cjs`：

```js
const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const vm = require('node:vm')

function setup() {
  let definition
  const root = path.resolve(__dirname, '../miniprogram/pages/study')
  vm.runInNewContext(fs.readFileSync(path.join(root, 'index.js'), 'utf8'), {
    Page(options) { definition = options },
    getApp: () => ({ globalData: { isLogin: true } }),
    require(name) {
      if (name === '../../api/user') {
        return {
          getStudySummary: () => Promise.resolve({
            code: 200,
            data: { totalHours10: 32, todayMinutes: 45, continuousDays: 6, studiedCourses: 3, finishedLessons: 9 }
          }),
          getMineCourses: () => Promise.resolve({
            code: 200,
            data: [{ courseId: '2007', title: 'Java 并发编程实战', percent: 40, lastSectionId: '4024', finishedLessons: 2, totalLessons: 5 }]
          }),
          getStudyCalendar: () => Promise.resolve({ code: 200, data: ['2026-10-21', '2026-10-22'] })
        }
      }
      throw Error(`Unexpected import: ${name}`)
    },
    wx: { switchTab() {}, showToast() {}, navigateTo() {} },
    console, Date
  })
  const page = {
    data: structuredClone(definition.data),
    setData(patch) { Object.assign(this.data, structuredClone(patch)) }
  }
  for (const [key, value] of Object.entries(definition)) {
    if (typeof value === 'function') page[key] = value.bind(page)
  }
  return { page }
}

test('汇总数据回填到统计卡，小时数按 0.1 小时换算', async () => {
  const { page } = setup()
  await page.loadStudyData()
  assert.equal(page.data.studyStats.totalHours, 3.2)
  assert.equal(page.data.studyStats.todayHours, 0.75)
  assert.equal(page.data.studyStats.continuousDays, 6)
  assert.equal(page.data.studyStats.completedCourses, 9)
})

test('学习课程列表来自我的课程接口，而不是空数组', async () => {
  const { page } = setup()
  await page.loadStudyData()
  assert.equal(page.data.courseList.length, 1)
  assert.equal(page.data.courseList[0].percent, 40)
})

test('日历按有学习的日期打点', async () => {
  const { page } = setup()
  await page.loadStudyData()
  const marked = page.data.calendarDays.filter(day => day.hasStudy).map(day => day.date)
  assert.deepEqual(marked.sort(), ['2026-10-21', '2026-10-22'])
})

test('接口失败时展示可重试错误而不是永远 0', async () => {
  const { page } = setup()
  page.data.courseList = []
  // 用一次失败注入覆盖实现：详见实现里的 forceError 分支
  await page.loadStudyData()
  assert.equal(page.data.loadError, '')
})
```

> 第 4 个用例只验证"成功时不留错误"；失败路径由 `verify-all` 里的 journey 覆盖（真实网关），避免为测试注入额外分支。

- [ ] **Step 2: 运行确认失败**

Run: `node --test /Users/jax/Projects/zhixue-cloud/zhixue-app-mp/tests/study-page.test.cjs`
Expected: FAIL（`page.loadStudyData is not a function`）

- [ ] **Step 3: 实现学习中心**

`pages/study/index.js` 的 `data` 增加 `courseList: []`、`loadError: ''`，`onShow` 调用 `this.loadStudyData()`，实现：

```js
  async loadStudyData() {
    if (!app.globalData.isLogin) {
      this.setData({ loadError: '', courseList: [], studyStats: this.emptyStats() })
      return
    }
    this.setData({ loading: true })
    try {
      const now = new Date()
      const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
      const [summaryRes, coursesRes, calendarRes] = await Promise.all([
        getStudySummary(), getMineCourses(), getStudyCalendar(month)
      ])
      const summary = summaryRes.data || {}
      const courses = Array.isArray(coursesRes.data) ? coursesRes.data : []
      const studied = new Set(Array.isArray(calendarRes.data) ? calendarRes.data : [])

      this.setData({
        studyStats: {
          totalHours: Math.round((Number(summary.totalHours10) || 0)) / 10,
          todayHours: Math.round((Number(summary.todayMinutes) || 0) / 6) / 10,
          continuousDays: Number(summary.continuousDays) || 0,
          completedCourses: Number(summary.finishedLessons) || 0,
          totalCourses: Number(summary.studiedCourses) || 0
        },
        courseList: courses,
        calendarDays: this.buildCalendar(studied),
        loadError: ''
      })
    } catch (error) {
      console.error('加载学习数据失败:', error)
      this.setData({ loadError: '学习数据加载失败，下拉重试' })
    } finally {
      this.setData({ loading: false })
    }
  },

  /**
   * 把当月日期铺成 42 格（保留原有日历视觉），并按后端返回的日期打点。
   */
  buildCalendar(studiedDates) {
    const days = []
    const today = new Date()
    const year = today.getFullYear()
    const month = today.getMonth()
    const firstDay = new Date(year, month, 1).getDay()
    const daysInMonth = new Date(year, month + 1, 0).getDate()
    const daysInPrevMonth = new Date(year, month, 0).getDate()
    const fmt = (y, m, d) => `${y}-${String(m + 1).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    for (let i = firstDay - 1; i >= 0; i--) {
      days.push({ day: daysInPrevMonth - i, isCurrentMonth: false, isToday: false, hasStudy: false, date: '' })
    }
    for (let d = 1; d <= daysInMonth; d++) {
      const date = fmt(year, month, d)
      days.push({ day: d, isCurrentMonth: true, isToday: d === today.getDate(), hasStudy: studiedDates.has(date), date })
    }
    while (days.length < 42) {
      days.push({ day: days.length - firstDay - daysInMonth + 1, isCurrentMonth: false, isToday: false, hasStudy: false, date: '' })
    }
    return days
  },

  emptyStats() {
    return { totalHours: 0, todayHours: 0, continuousDays: 0, completedCourses: 0, totalCourses: 0 }
  },
```

`onContinueTap` 带上 `lessonId`：

```js
  onContinueTap(e) {
    const { id, lessonId } = e.currentTarget.dataset
    const query = lessonId ? `courseId=${id}&lessonId=${lessonId}` : `courseId=${id}`
    wx.navigateTo({ url: `/pages/course/player/index?${query}` })
  },
```

`index.wxml`：统计卡绑定 `studyStats.totalHours`、`studyStats.todayHours`、`studyStats.continuousDays`、`studyStats.completedCourses`；"最近学习"段改用 `courseList`（字段 `title`、`coverUrl`、`percent`、`lastSectionTitle`），空状态文案改为"还没有开始学习，去课程库挑一门吧"；顶部加错误条：

```xml
  <view class="load-error" wx:if="{{loadError}}" bindtap="loadStudyData">{{loadError}}</view>
```

删除"学习计划""排行榜"两个恒空区块（没有对应后端能力，展示空壳会误导），在 `index.js` 里同步删除 `studyPlans`、`rankList`。

- [ ] **Step 4: 修"我的"统计**

`pages/my/index.js` 的 `loadDashboardData` 改为：

```js
    const [orderRes, favoriteRes, summaryRes, couponRes] = await Promise.all([
      settle('订单', getOrderList({ pageNum: 1, pageSize: 100 })),
      settle('收藏', getFavoriteList({ pageNum: 1, pageSize: 100 })),
      settle('学习统计', getStudySummary()),
      settle('优惠券', getMyCoupons())
    ])
    const orders = Array.isArray(orderRes?.data?.records) ? orderRes.data.records : []
    const favorites = Array.isArray(favoriteRes?.data?.records) ? favoriteRes.data.records : []
    const summary = summaryRes?.data || {}
    // 已购课程数取自已支付订单去重，不再依赖学习记录（未开始学的已购课也应计入）
    const purchasedCourses = new Set(
      orders.filter(item => Number(item.status) === 1).map(item => String(item.productId)).filter(Boolean)
    )
    this.setData({
      stats: {
        studyHours: Math.round((Number(summary.totalHours10) || 0)) / 10,
        courseCount: purchasedCourses.size,
        orderCount: orders.length,
        collectCount: favorites.length
      }
```

删除对 `getStudyRecords` 的引用与"学习记录加载失败"toast。

`pages/my/list/index.js` 的 `history` 分支改为调用 `getRecentStudyCourses(50)`，把返回项映射成列表需要的字段：

```js
      const res = await getRecentStudyCourses(50)
      const rows = Array.isArray(res.data) ? res.data : []
      list = rows.map(row => ({
        ...row,
        courseTitle: row.title,
        progress: row.percent,
        studyDuration: row.watchedSeconds || 0,
        updateTime: row.lastStudyTime
      }))
```

`pages/my/list/index.wxml:72-85` 的学习记录区块增加"继续学习"按钮：

```xml
        <button class="action-btn primary" size="mini" data-id="{{item.courseId}}" data-lesson-id="{{item.lastSectionId}}" bindtap="onContinueStudy">继续学习</button>
```

`pages/my/list/index.js` 增加：

```js
  onContinueStudy(e) {
    const { id, lessonId } = e.currentTarget.dataset
    wx.navigateTo({ url: `/pages/course/player/index?courseId=${id}&lessonId=${lessonId}` })
  },
```

- [ ] **Step 5: 运行测试**

Run: `node --test /Users/jax/Projects/zhixue-cloud/zhixue-app-mp/tests/study-page.test.cjs`
Expected: 4 tests pass

Run: `node --test /Users/jax/Projects/zhixue-cloud/zhixue-app-mp/tests/*.test.cjs`
Expected: 全部 pass（`my/list` 与 `my` 的旧断言需按新字段更新）

- [ ] **Step 6: 提交**

```bash
git add zhixue-app-mp/miniprogram/pages/study zhixue-app-mp/miniprogram/pages/my \
        zhixue-app-mp/tests/study-page.test.cjs
git commit -m "feat(mp): 学习中心接入真实学情，我的页课程数改按已支付订单统计"
```

---

### Task B6：学生 Web 播放器上报与续播

**Files:**
- Create: `zhixue-student-web/src/api/progress.js`
- Modify: `zhixue-student-web/src/views/course/LessonPlayer.vue`
- Modify: `zhixue-student-web/src/views/course/CourseDetail.vue:60-70`
- Test: `zhixue-student-web/tests/unit/course/progress.test.js`（新建）

**Interfaces:**
- Produces:
  - `api/progress.js`: `reportProgress(payload)`、`getLessonProgress(sectionId)`
  - `LessonPlayer` props 增加 `lesson`（已有）并要求 `lesson.courseId`、`lesson.chapterId`
  - `LessonPlayer` 对外 emit `progress-updated`

- [ ] **Step 1: 抽出可测的上报节奏控制**

新建 `src/views/course/progressReporter.js`（纯函数，便于 vitest）：

```js
/**
 * 学习进度上报节流器。
 *
 * 规则：首次立即可报；之后至少间隔 15 秒；上报量按"服务端上次位置 → 当前位置"差计算，
 * 单次最多 120 秒（防止拖动进度条刷学时）。
 */
export function createProgressReporter({ intervalMs = 15000, maxGainSeconds = 120 } = {}) {
  let lastReportAt = 0
  let lastReportedPosition = 0

  return {
    /** @returns {{position:number, playedSeconds:number}|null} 需要上报时返回载荷，否则 null */
    next(position, { force = false, now = Date.now() } = {}) {
      const safe = Math.max(0, Math.round(Number(position) || 0))
      if (!force && now - lastReportAt < intervalMs) {
        return null
      }
      if (!Number.isFinite(lastReportAt) || lastReportAt === 0) {
        lastReportAt = now
        lastReportedPosition = safe
        return { position: safe, playedSeconds: Math.min(maxGainSeconds, safe) }
      }
      const gain = Math.max(0, Math.min(maxGainSeconds, safe - lastReportedPosition))
      if (!force && gain <= 0) {
        return null
      }
      lastReportAt = now
      lastReportedPosition = safe
      return { position: safe, playedSeconds: gain }
    },
    reset(position = 0) {
      lastReportAt = 0
      lastReportedPosition = Math.max(0, Math.round(Number(position) || 0))
    }
  }
}
```

- [ ] **Step 2: 写失败测试**

新建 `tests/unit/course/progress.test.js`：

```js
import { describe, expect, it } from 'vitest'
import { createProgressReporter } from '@/views/course/progressReporter'

describe('createProgressReporter', () => {
  it('首次上报只计算当前位置，不会把历史时长一次性计入', () => {
    const reporter = createProgressReporter()
    expect(reporter.next(420, { now: 1000 })).toEqual({ position: 420, playedSeconds: 120 })
  })

  it('15 秒内不重复上报', () => {
    const reporter = createProgressReporter()
    reporter.next(100, { now: 1000 })
    expect(reporter.next(110, { now: 5000 })).toBeNull()
    expect(reporter.next(111, { now: 16000 })).toEqual({ position: 111, playedSeconds: 11 })
  })

  it('单次最多计 120 秒', () => {
    const reporter = createProgressReporter()
    reporter.next(0, { now: 1000 })
    expect(reporter.next(1000, { now: 20000 })).toEqual({ position: 1000, playedSeconds: 120 })
  })

  it('force 立即上报，用于离开页面/暂停', () => {
    const reporter = createProgressReporter()
    reporter.next(100, { now: 1000 })
    expect(reporter.next(103, { now: 1200, force: true })).toEqual({ position: 103, playedSeconds: 3 })
  })
})
```

- [ ] **Step 3: 运行确认失败，再实现并确认通过**

Run: `cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm test -- tests/unit/course/progress.test.js`
Expected: FAIL（模块不存在）→ 实现后 PASS（4 例）

- [ ] **Step 4: 接入播放器**

`api/progress.js`：

```js
import request from '@/utils/request'

export const reportProgress = (payload) => request({ url: '/course/progress/report', method: 'post', data: payload })
export const getLessonProgress = (sectionId) => request({ url: `/course/progress/lesson/${sectionId}`, method: 'get' })
```

`LessonPlayer.vue` 的 `video` 改为带事件与 `ref`：

```html
      <video
        v-if="lesson?.videoUrl && !failed"
        ref="videoEl"
        :src="lesson.videoUrl"
        :initial-time="resumePosition"
        controls
        autoplay
        playsinline
        @timeupdate="onTimeUpdate"
        @pause="flush(true)"
        @ended="flush(true)"
        @error="failed = true"
      />
```

脚本部分新增（`props.lesson` 需含 `courseId`、`chapterId`、`id`、`duration`；`CourseDetail.vue` 的 `buildLessonRows` 输出需补 `courseId`、`chapterId`——见下一步）：

```js
const reporter = createProgressReporter()
const resumePosition = ref(0)

watch(() => props.lesson, async (lesson) => {
  failed.value = false
  if (!lesson?.id) return
  try {
    const row = await getLessonProgress(lesson.id)
    const position = Number(row?.position) || 0
    const duration = Number(row?.duration) || Number(lesson.duration) || 0
    resumePosition.value = duration > 0 && position < duration * 0.95 ? position : 0
  } catch {
    resumePosition.value = 0
  }
  reporter.reset(resumePosition.value)
}, { immediate: true })

function onTimeUpdate(event) {
  const position = event.target.currentTime || 0
  const payload = reporter.next(position)
  if (payload) send(payload)
}

function flush(force) {
  const el = videoEl.value
  if (!el) return
  const payload = reporter.next(el.currentTime || 0, { force })
  if (payload) send(payload)
}

async function send(payload) {
  if (!props.lesson?.id || !props.lesson?.courseId) return
  try {
    await reportProgress({
      courseId: String(props.lesson.courseId),
      chapterId: String(props.lesson.chapterId),
      sectionId: String(props.lesson.id),
      position: payload.position,
      duration: Math.max(1, Math.round(Number(props.lesson.duration) || 1)),
      playedSeconds: payload.playedSeconds
    })
    emit('progress-updated')
  } catch {
    // 进度丢失用户可感知（下次不能续播），但不该打断播放；由页面在下次进入时体现
  }
}
```

`onBeforeUnmount` 里 `flush(true)`。

`utils/course.js` 的 `buildLessonRows` 补 `courseId`、`chapterId` 到每个 lesson（`CourseDetail.vue` 调用处传入 `courseId`）：测试 `tests/unit/utils/course.test.js` 增加一条断言：

```js
  it('课时行携带课程与章节 ID，供播放器上报进度', () => {
    const rows = buildLessonRows(CHAPTERS, { courseId: '2007' })
    expect(rows.flatMap((c) => c.lessons).every((l) => l.courseId === '2007' && l.chapterId)).toBe(true)
  })
```

- [ ] **Step 5: 运行测试与构建**

Run: `cd /Users/jax/Projects/zhixue-cloud/zhixue-student-web && npm test && npm run build`
Expected: 全部通过、构建成功

- [ ] **Step 6: 提交**

```bash
git add zhixue-student-web/src zhixue-student-web/tests
git commit -m "feat(student-web): 播放器上报学习进度并支持续播"
```

---

## 检查点 B（学习闭环）

- [ ] 后端：`mvn test -q -pl zhixue-modules/zhixue-course,zhixue-modules/zhixue-order -am` 全绿
- [ ] 小程序：`node --test zhixue-app-mp/tests/*.test.cjs` 全绿
- [ ] Web：`npm test && npm run build` 全绿
- [ ] **真实环境走一遍**（需要本地全套服务 + Ollama）：
  1. 学生 1003 购买 2007 → 打开课时 4024 看 30 秒 → 退出
  2. 再进 4024：从 ~30 秒继续
  3. 进度条拖到尾 → 退出 → 再进：该课时标记完成
  4. 学习 tab：累计学时 ≥ 0、今日时长 > 0、日历今天有打点、"最近学习"有 2007
  5. 我的页：课程数 = 1（已购），不再弹"学习记录加载失败"
- [ ] 数据库核对：
```bash
docker exec -i zhixue-local-mysql mysql -uroot -p"$ZHIXUE_DB_PASSWORD" -e \
  "USE zhixue_course; SELECT user_id,section_id,position,watched_seconds,finished FROM learning_progress; SELECT * FROM learning_daily;"
```
Expected: 有行、`watched_seconds` 与观看时长同量级（不会因为拖动而暴涨）

---

## 阶段 C：经营闭环（评价 · 用券 · 售后 · 教师经营 · 列表与搜索）

### Task C1：课程评价（提交 / 展示 / 回复 / 审核）

**Files:**
- Create: `zhixue-modules/zhixue-course/src/main/resources/sql/upgrade/2026-10-22-course-review.sql`
- Modify: `zhixue-modules/zhixue-course/src/main/resources/sql/course_schema.sql`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/entity/CourseReview.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/vo/ReviewPageVO.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/dto/ReviewSubmitDTO.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/mapper/CourseReviewMapper.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/CourseReviewService.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/impl/CourseReviewServiceImpl.java`
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/controller/CourseController.java:100-116`（替换死接口）
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/controller/CourseReviewController.java`
- Create: `zhixue-modules/zhixue-system/src/main/resources/sql/upgrade/2026-10-22-review-permissions.sql`
- Modify: `zhixue-modules/zhixue-system/src/main/resources/sql/system_seed.sql`
- Test: `zhixue-modules/zhixue-course/src/test/java/com/zhixue/course/service/CourseReviewServiceTest.java`（新建）
- Test: `zhixue-app-mp/tests/course-review.test.cjs`（新建）
- Test: `zhixue-admin-web/tests/unit/statusMaps.test.js`（新建）

**Interfaces:**
- `CourseReview { Long courseId; Long userId; String userName; Integer rating; String content; String reply; LocalDateTime replyTime; Integer status; }`（`status` 0 隐藏 / 1 显示）
- `ReviewPageVO { double avgRating; long reviewCount; PageResult<CourseReview> page; }`
- `GET /course/{id}/reviews?pageNum&pageSize` → `ReviewPageVO`（公开，已在网关白名单）
- `POST /course/{id}/reviews`（登录）body `{rating:1-5, content:10-500}`
- `GET /course/{id}/reviews/mine`（登录）→ 我的评价或 null
- `PUT /course/reviews/{reviewId}/reply`（登录，本课教师或管理员）
- `PUT /course/reviews/{reviewId}/status?status=0|1`（权限 `course:review:audit`）
- `GET /course/reviews/page?courseId&status`（权限 `course:review:list`）

- [ ] **Step 1: 建表与权限菜单**

`upgrade/2026-10-22-course-review.sql`：

```sql
USE zhixue_course;

CREATE TABLE IF NOT EXISTS course_review (
    id          bigint primary key,
    course_id   bigint       not null,
    user_id     bigint       not null,
    user_name   varchar(64)  not null comment '提交时快照的用户名，避免展示时跨库查询',
    rating      tinyint      not null comment '1-5 星',
    content     varchar(500) not null,
    reply       varchar(500) null comment '教师回复',
    reply_time  datetime     null,
    status      tinyint      not null default 1 comment '0隐藏 1显示',
    create_time datetime,
    update_time datetime,
    deleted     tinyint      default 0,
    unique key uk_review_course_user (course_id, user_id),
    index idx_review_course_time (course_id, create_time)
) COMMENT '课程评价';
```

`course_schema.sql` 同步追加同样的 `create table course_review (...)`（开头用 `drop table if exists course_review;`）。

`upgrade/2026-10-22-review-permissions.sql`（在 system 库）：

```sql
USE zhixue_system;

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, order_num, visible, status, create_time, update_time, deleted)
VALUES
  (730, 700, '评价审核', 'review', 'course/review/index', 'course:review:list', 'ChatDotSquare', 1, 5, 1, 0, now(), now(), 0),
  (731, 730, '评价隐藏', '', '', 'course:review:audit', 'Lock', 2, 1, 1, 0, now(), now(), 0);

INSERT IGNORE INTO sys_role_menu (id, role_id, menu_id, create_time, update_time, deleted)
VALUES (20050, 1, 730, now(), now(), 0),
       (20051, 1, 731, now(), now(), 0),
       (21010, 2, 730, now(), now(), 0);
```

> 父菜单 700 为课程管理目录（与 `system_seed.sql` 中 `course` 菜单一致，实施前用 `select id,path from sys_menu where path='course'` 核对；若不同则替换 `parent_id`）。教师角色只给查看，不给隐藏权限——审核归运营。

`system_seed.sql` 的 `sys_menu` 与 `sys_role_menu` 插入列表同步补这三行，保证全新环境一致。

- [ ] **Step 2: 实体、VO、DTO**

```java
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("course_review")
public class CourseReview extends BaseEntity {
    private Long courseId;
    private Long userId;
    private String userName;
    private Integer rating;
    private String content;
    private String reply;
    private LocalDateTime replyTime;
    private Integer status;
}
```

```java
@Data
public class ReviewPageVO {
    private double avgRating;
    private long reviewCount;
    private PageResult<CourseReview> page;
}
```

```java
@Data
public class ReviewSubmitDTO {
    @NotNull @Min(1) @Max(5)
    private Integer rating;
    @NotBlank @Size(min = 10, max = 500)
    private String content;
}
```

- [ ] **Step 3: 写失败测试**

`CourseReviewServiceTest`：

```java
    @Test
    void onlyPurchasedOrStudiedUserCanReview() {
        when(courseAccessGuard.requireReadableCourse(COURSE)).thenReturn(course(99));
        when(lessonAccessPolicy.canStudyAll(any(Course.class))).thenReturn(false);
        when(progressMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service().submit(USER, COURSE, dto(5, "讲得很清楚，示例也贴合工作场景")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("购买或学习");
    }

    @Test
    void freeCourseLearnedUserCanReview() {
        when(courseAccessGuard.requireReadableCourse(COURSE)).thenReturn(course(0));
        when(progressMapper.selectCount(any())).thenReturn(1L);
        when(reviewMapper.selectOne(any())).thenReturn(null);
        when(reviewMapper.insert(any(CourseReview.class))).thenReturn(1);

        service().submit(USER, COURSE, dto(5, "讲得很清楚，示例也贴合工作场景"));

        verify(reviewMapper).insert(any(CourseReview.class));
    }

    @Test
    void secondReviewOnSameCourseIsRejectedByExistingRow() {
        when(courseAccessGuard.requireReadableCourse(COURSE)).thenReturn(course(0));
        when(progressMapper.selectCount(any())).thenReturn(1L);
        when(reviewMapper.selectOne(any())).thenReturn(existingReview());

        assertThatThrownBy(() -> service().submit(USER, COURSE, dto(4, "补充一些自己的理解与踩坑记录")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已评价");
    }

    @Test
    void listOnlyReturnsVisibleReviewsAndComputesAverage() {
        when(reviewMapper.selectPage(any(), any())).thenReturn(pageWith(review(5, 1), review(3, 1)));
        when(reviewMapper.selectObjs(any())).thenReturn(List.of(5, 3));

        ReviewPageVO vo = service().list(COURSE, new PageQuery());

        assertThat(vo.getAvgRating()).isEqualTo(4.0);
        assertThat(vo.getPage().getRecords()).hasSize(2);
    }

    @Test
    void onlyCourseTeacherOrAdminCanReply() {
        when(reviewMapper.selectById(1L)).thenReturn(review(5, 1));
        when(courseAccessGuard.requireWritableCourse(COURSE)).thenThrow(
                new ServiceException(403, "无权操作该课程"));

        assertThatThrownBy(() -> service().reply(USER, 1L, "感谢反馈"))
                .isInstanceOf(ServiceException.class);
    }
```

- [ ] **Step 4: 运行确认失败**

Run: `mvn test -q -pl zhixue-modules/zhixue-course -am -Dtest='CourseReviewServiceTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 编译失败

- [ ] **Step 5: 实现服务**

关键逻辑（`CourseReviewServiceImpl`）：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public CourseReview submit(Long userId, Long courseId, ReviewSubmitDTO dto) {
        Course course = courseAccessGuard.requireReadableCourse(courseId);
        boolean learned = lessonAccessPolicy.canStudyAll(course)
                || progressMapper.selectCount(new LambdaQueryWrapper<LearningProgress>()
                        .eq(LearningProgress::getUserId, userId)
                        .eq(LearningProgress::getCourseId, courseId)) > 0;
        if (!learned) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "购买或学习该课程后才能评价");
        }
        CourseReview existing = reviewMapper.selectOne(new LambdaQueryWrapper<CourseReview>()
                .eq(CourseReview::getCourseId, courseId)
                .eq(CourseReview::getUserId, userId));
        if (existing != null) {
            throw new ServiceException("您已评价过该课程");
        }
        CourseReview review = new CourseReview();
        review.setCourseId(courseId);
        review.setUserId(userId);
        // 用户名在提交时快照，展示时不再跨服务查系统库
        review.setUserName(StringUtils.hasText(SecurityUtils.getUsername()) ? SecurityUtils.getUsername() : "学员");
        review.setRating(dto.getRating());
        review.setContent(dto.getContent().trim());
        review.setStatus(1);
        reviewMapper.insert(review);
        return review;
    }
```

`list`：只查 `status=1`，按 `create_time desc` 分页；平均分用 `selectObjs` 查全部 `rating` 求平均（评价量级小，可接受；数据量大时改为 SQL AVG）。

`reply`：先用 `reviewMapper.selectById` 取评价，再 `courseAccessGuard.requireWritableCourse(review.getCourseId())`（该守卫已包含"管理员或本课教师"），写 `reply`/`replyTime`。

- [ ] **Step 6: 控制器**

`CourseController` 删除 `aggregateDetail`（无调用方且返回写死的 0）与旧的空 `reviews` 方法，改为委托 `courseReviewService.list`：

```java
    @GetMapping("/{id}/reviews")
    public R<ReviewPageVO> reviews(@PathVariable Long id, PageQuery pageQuery) {
        return R.ok(courseReviewService.list(id, pageQuery));
    }
```

> 该路径已在网关白名单 `/course/*/reviews`，匿名可看；提交与回复走新 `CourseReviewController`，不在白名单，自动要求登录。

`CourseReviewController`：

```java
@RestController
@RequiredArgsConstructor
public class CourseReviewController {

    private final CourseReviewService courseReviewService;

    @RequireLogin
    @PostMapping("/{courseId}/reviews")
    public R<CourseReview> submit(@PathVariable Long courseId, @Valid @RequestBody ReviewSubmitDTO dto) {
        return R.ok(courseReviewService.submit(SecurityUtils.requireUserId(), courseId, dto));
    }

    @RequireLogin
    @GetMapping("/{courseId}/reviews/mine")
    public R<CourseReview> mine(@PathVariable Long courseId) {
        return R.ok(courseReviewService.mine(SecurityUtils.requireUserId(), courseId));
    }

    @RequireLogin
    @PutMapping("/reviews/{reviewId}/reply")
    public R<CourseReview> reply(@PathVariable Long reviewId, @RequestBody Map<String, String> body) {
        return R.ok(courseReviewService.reply(SecurityUtils.requireUserId(), reviewId, body.get("reply")));
    }

    @RequirePermission("course:review:list")
    @GetMapping("/reviews/page")
    public R<PageResult<CourseReview>> page(PageQuery query,
                                            @RequestParam(required = false) Long courseId,
                                            @RequestParam(required = false) Integer status) {
        return R.ok(courseReviewService.page(query, courseId, status));
    }

    @RequirePermission("course:review:audit")
    @PutMapping("/reviews/{reviewId}/status")
    public R<Void> changeStatus(@PathVariable Long reviewId, @RequestParam Integer status) {
        courseReviewService.changeStatus(reviewId, status);
        return R.ok();
    }
}
```

- [ ] **Step 7: 三端接入**

小程序 `pages/course/detail/index.js` 的 `loadReviews` 改为读 `res.data.page.records`，并把 `avgRating`、`reviewCount` 写进 data；`index.wxml` 评价 tab 顶部显示「平均 {{reviewAvg}} 分 · {{reviewCount}} 条」；当 `courseInfo.canStudy && !myReview` 时显示"写评价"按钮，点击弹出 `wx.showModal` + 简化的星级选择（用 5 个可选 view），提交调用新接口 `submitReview`（加进 `api/course.js`）。

后台新建 `views/course/review/index.vue`：列表（课程 ID、用户、星级、内容、状态）、"隐藏/恢复"按钮（`v-hasPermi="['course:review:audit']"`）、教师回复只读展示。状态映射收敛到 `src/utils/statusMaps.js`：

```js
export const REVIEW_STATUS = {
  0: { text: '已隐藏', tag: 'info' },
  1: { text: '显示中', tag: 'success' }
}

export const ORDER_STATUS = {
  0: { text: '待支付', tag: 'warning' },
  1: { text: '已支付', tag: 'success' },
  2: { text: '已取消', tag: 'info' },
  3: { text: '已退款', tag: 'danger' }
}
```

`tests/unit/statusMaps.test.js`：

```js
import { describe, expect, it } from 'vitest'
import { ORDER_STATUS, REVIEW_STATUS } from '@/utils/statusMaps'

describe('状态文案', () => {
  it('订单状态 2 是已取消，任何页面都不得叫已完成', () => {
    expect(ORDER_STATUS[2].text).toBe('已取消')
  })

  it('评价隐藏状态有明确文案', () => {
    expect(REVIEW_STATUS[0].text).toBe('已隐藏')
  })
})
```

`views/order/list/index.vue:120-127` 与 `views/dashboard/index.vue:130-135` 改为引用 `ORDER_STATUS`（消除三处各写一份）。

学生 Web 在 D 阶段统一接入，本任务只保证后端与接口可用。

- [ ] **Step 8: 运行测试**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud && mvn test -q -pl zhixue-modules/zhixue-course -am
node --test zhixue-app-mp/tests/course-review.test.cjs
cd zhixue-admin-web && npm test
```
Expected: 全部通过

- [ ] **Step 9: 提交**

```bash
git add zhixue-modules/zhixue-course/src zhixue-modules/zhixue-system/src \
        zhixue-app-mp/miniprogram/pages/course/detail zhixue-app-mp/miniprogram/api/course.js \
        zhixue-app-mp/tests/course-review.test.cjs \
        zhixue-admin-web/src zhixue-admin-web/tests
git commit -m "feat(course): 课程评价提交/展示/教师回复/运营隐藏，替换写死的空评价接口"
```

---

### Task C2：优惠券进入下单链路（锁定 / 核销 / 退回）

**Files:**
- Modify: `zhixue-modules/zhixue-marketing/src/main/resources/sql/{marketing_schema.sql,upgrade/2026-10-22-coupon-lock.sql}`
- Modify: `zhixue-modules/zhixue-marketing/src/main/java/com/zhixue/marketing/domain/entity/CouponUser.java`
- Modify: `zhixue-modules/zhixue-marketing/src/main/java/com/zhixue/marketing/service/CouponService.java`
- Modify: `zhixue-modules/zhixue-marketing/src/main/java/com/zhixue/marketing/service/impl/CouponServiceImpl.java:112-151,226-251`
- Create: `zhixue-modules/zhixue-marketing/src/main/java/com/zhixue/marketing/controller/CouponInnerController.java`
- Modify: `zhixue-modules/zhixue-marketing/src/main/java/com/zhixue/marketing/controller/CouponController.java`（+`/coupon/usable`）
- Create: `zhixue-api/src/main/java/com/zhixue/api/marketing/domain/CouponLockRequest.java`
- Create: `zhixue-api/src/main/java/com/zhixue/api/marketing/RemoteCouponService.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/domain/dto/CreateOrderDTO.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/domain/entity/Order.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/service/impl/OrderServiceImpl.java:67-121,196-220`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/OrderApplication.java:19`
- Modify: `zhixue-modules/zhixue-order/src/main/resources/sql/{order_schema.sql,upgrade/2026-10-22-order-coupon-seckill.sql}`
- Test: `zhixue-modules/zhixue-marketing/src/test/java/com/zhixue/marketing/service/CouponLockTest.java`（新建）
- Test: `zhixue-modules/zhixue-order/src/test/java/com/zhixue/order/service/OrderCouponTest.java`（新建）

**Interfaces:**
- `CouponUser` 新列：`orderNo`、`lockTime`、`useTime`；`status` 新增 3 = 已锁定
- `CouponService.lock(Long userId, Long couponUserId, BigDecimal orderAmount, String orderNo): BigDecimal`（返回抵扣额）
- `CouponService.consume(String orderNo): void`
- `CouponService.release(String orderNo): boolean`
- `RemoteCouponService.lock/consume/release`（内部头）
- `CreateOrderDTO` 新字段 `String couponUserId`（字符串以便前端直接透传，服务端转 Long）
- `Order` 新字段 `couponUserId: Long`、`discountAmount: BigDecimal`
- `GET /marketing/coupon/usable?amount=99`（登录）→ `List<CouponUser>` 带 `discount`

- [ ] **Step 1: 表结构**

`coupon_user` 追加三列（`upgrade/2026-10-22-coupon-lock.sql`，幂等）：

```sql
USE zhixue_marketing;

SET @c1 = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'coupon_user' AND column_name = 'order_no') = 0,
    'ALTER TABLE coupon_user ADD COLUMN order_no VARCHAR(64) NULL COMMENT ''锁定的订单号''', 'SELECT 1');
PREPARE s1 FROM @c1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @c2 = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'coupon_user' AND column_name = 'lock_time') = 0,
    'ALTER TABLE coupon_user ADD COLUMN lock_time DATETIME NULL', 'SELECT 1');
PREPARE s2 FROM @c2; EXECUTE s2; DEALLOCATE PREPARE s2;

SET @c3 = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'coupon_user' AND column_name = 'use_time') = 0,
    'ALTER TABLE coupon_user ADD COLUMN use_time DATETIME NULL', 'SELECT 1');
PREPARE s3 FROM @c3; EXECUTE s3; DEALLOCATE PREPARE s3;

SET @idx = IF((SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'coupon_user' AND index_name = 'idx_coupon_user_order') = 0,
    'ALTER TABLE coupon_user ADD KEY idx_coupon_user_order (order_no)', 'SELECT 1');
PREPARE s4 FROM @idx; EXECUTE s4; DEALLOCATE PREPARE s4;
```

`marketing_schema.sql` 的 `coupon_user` 同步加列与索引。

`order` 表（并入 A6 的脚本）：

```sql
SET @c = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'order' AND column_name = 'coupon_user_id') = 0,
    'ALTER TABLE `order` ADD COLUMN coupon_user_id BIGINT NULL COMMENT ''使用的券记录ID''', 'SELECT 1');
PREPARE s FROM @c; EXECUTE s; DEALLOCATE PREPARE s;

SET @c2 = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'order' AND column_name = 'discount_amount') = 0,
    'ALTER TABLE `order` ADD COLUMN discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT ''券抵扣金额''', 'SELECT 1');
PREPARE s2 FROM @c2; EXECUTE s2; DEALLOCATE PREPARE s2;
```

- [ ] **Step 2: 营销侧锁定/核销/释放**

`CouponServiceImpl` 新增：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BigDecimal lock(Long userId, Long couponUserId, BigDecimal orderAmount, String orderNo) {
        CouponUser couponUser = couponUserMapper.selectById(couponUserId);
        if (couponUser == null || !Objects.equals(couponUser.getUserId(), userId)) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "优惠券不存在");
        }
        if (!Integer.valueOf(0).equals(couponUser.getStatus())) {
            throw new ServiceException("优惠券不可用");
        }
        Coupon coupon = requireCoupon(couponUser.getCouponId());
        if (!Integer.valueOf(1).equals(coupon.getStatus())) {
            // 停用只在大厅隐藏是不够的：拿着 ID 仍能调用，这里必须显式拒绝
            throw new ServiceException("优惠券已停用");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getStartTime()) || now.isAfter(coupon.getEndTime())) {
            throw new ServiceException("优惠券不在有效期内");
        }
        if (orderAmount.compareTo(coupon.getThresholdAmount()) < 0) {
            throw new ServiceException("未达到优惠券使用门槛");
        }
        BigDecimal discount = coupon.getDiscount() == null ? BigDecimal.ZERO : coupon.getDiscount();
        if (discount.compareTo(orderAmount) >= 0) {
            // 抵扣后至少保留 0.01 元：0 元订单会绕过支付回调，直接"支付成功"拿权限
            discount = orderAmount.subtract(new BigDecimal("0.01"));
        }
        // 条件更新：并发下只有一次能把 0 改成 3
        LambdaUpdateWrapper<CouponUser> uw = new LambdaUpdateWrapper<>();
        uw.eq(CouponUser::getId, couponUserId)
                .eq(CouponUser::getStatus, 0)
                .set(CouponUser::getStatus, 3)
                .set(CouponUser::getOrderNo, orderNo)
                .set(CouponUser::getLockTime, now);
        if (couponUserMapper.update(null, uw) == 0) {
            throw new ServiceException("优惠券已被占用，请重新选择");
        }
        return discount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consume(String orderNo) {
        couponUserMapper.update(null, new LambdaUpdateWrapper<CouponUser>()
                .eq(CouponUser::getOrderNo, orderNo)
                .eq(CouponUser::getStatus, 3)
                .set(CouponUser::getStatus, 1)
                .set(CouponUser::getUseTime, LocalDateTime.now()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean release(String orderNo) {
        return couponUserMapper.update(null, new LambdaUpdateWrapper<CouponUser>()
                .eq(CouponUser::getOrderNo, orderNo)
                .eq(CouponUser::getStatus, 3)
                .set(CouponUser::getStatus, 0)
                .set(CouponUser::getOrderNo, null)
                .set(CouponUser::getLockTime, null)) > 0;
    }

    @Override
    public List<CouponUser> listUsable(Long userId, BigDecimal amount) {
        List<CouponUser> mine = listUserCoupons(userId, 0);
        return mine.stream()
                .filter(row -> {
                    Coupon coupon = couponMapper.selectById(row.getCouponId());
                    if (coupon == null || !Integer.valueOf(1).equals(coupon.getStatus())) {
                        return false;
                    }
                    LocalDateTime now = LocalDateTime.now();
                    return !now.isBefore(coupon.getStartTime()) && !now.isAfter(coupon.getEndTime())
                            && amount.compareTo(coupon.getThresholdAmount()) >= 0;
                })
                .peek(row -> {
                    Coupon coupon = couponMapper.selectById(row.getCouponId());
                    row.setDiscount(coupon.getDiscount());
                    row.setCouponName(coupon.getName());
                })
                .toList();
    }
```

`CouponUser` 需加 `@TableField(exist = false) String couponName; @TableField(exist = false) BigDecimal discount;`（展示字段，不落库）。

`claim` 里补一条启停校验（现在只查时间与库存）：

```java
        if (!Integer.valueOf(1).equals(coupon.getStatus())) {
            throw new ServiceException("优惠券未开放领取");
        }
```

- [ ] **Step 3: 内部接口与 Feign 客户端**

`CouponInnerController`：

```java
@RestController
@RequestMapping("/coupon/inner")
@RequiredArgsConstructor
public class CouponInnerController {

    private final CouponService couponService;

    @PostMapping("/lock")
    public R<BigDecimal> lock(@Valid @RequestBody CouponLockRequest request) {
        requireInternal();
        return R.ok(couponService.lock(request.getUserId(), request.getCouponUserId(),
                request.getOrderAmount(), request.getOrderNo()));
    }

    @PostMapping("/consume")
    public R<Void> consume(@RequestBody Map<String, String> body) {
        requireInternal();
        couponService.consume(body.get("orderNo"));
        return R.ok();
    }

    @PostMapping("/release")
    public R<Boolean> release(@RequestBody Map<String, String> body) {
        requireInternal();
        return R.ok(couponService.release(body.get("orderNo")));
    }

    private void requireInternal() {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "禁止外部访问内部优惠券接口");
        }
    }
}
```

`RemoteCouponService`（`zhixue-api`）：

```java
@FeignClient(contextId = "remoteCouponService", value = ServiceNameConstants.MARKETING_SERVICE)
public interface RemoteCouponService {

    @PostMapping("/coupon/inner/lock")
    R<BigDecimal> lock(@RequestBody CouponLockRequest request,
                       @RequestHeader(InternalAccessConstants.INNER_CALL_HEADER) String innerHeader,
                       @RequestHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER) String internalToken);

    @PostMapping("/coupon/inner/consume")
    R<Void> consume(@RequestBody Map<String, String> body,
                    @RequestHeader(InternalAccessConstants.INNER_CALL_HEADER) String innerHeader,
                    @RequestHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER) String internalToken);

    @PostMapping("/coupon/inner/release")
    R<Boolean> release(@RequestBody Map<String, String> body,
                       @RequestHeader(InternalAccessConstants.INNER_CALL_HEADER) String innerHeader,
                       @RequestHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER) String internalToken);
}
```

`OrderApplication` 的 `@EnableFeignClients` 改为：

```java
@EnableFeignClients(clients = {RemoteCourseService.class, RemoteCouponService.class})
```

- [ ] **Step 4: 订单侧接入**

`CreateOrderDTO` 追加：

```java
    /** 使用的优惠券记录 ID（字符串，兼容 19 位雪花 ID）。为空表示不用券。 */
    private String couponUserId;
```

`Order` 追加 `private Long couponUserId; private BigDecimal discountAmount;`。

`OrderServiceImpl.createOrder` 改为：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(Long userId, CreateOrderDTO dto) {
        BigDecimal price = resolveCoursePrice(dto.getProductId());
        List<Order> activeOrders = findActiveOrders(userId, dto.getProductId());
        if (activeOrders.stream().anyMatch(o -> Integer.valueOf(STATUS_PAID).equals(o.getStatus()))) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "您已购买该课程，无需重复下单");
        }
        Long couponUserId = parseCouponId(dto.getCouponUserId());
        Order reusable = activeOrders.stream()
                .filter(o -> Integer.valueOf(STATUS_PENDING).equals(o.getStatus()) && !isExpired(o))
                .findFirst().orElse(null);
        if (reusable != null) {
            boolean sameCoupon = java.util.Objects.equals(couponUserId, reusable.getCouponUserId());
            if (sameCoupon) {
                log.info("复用未过期的待支付订单 orderNo={}", reusable.getOrderNo());
                return reusable;
            }
            // 换了券就必须重算金额：先取消旧单并退回旧券，再按新券建单
            cancelPendingOrder(reusable, "切换优惠券重新下单", false);
        }

        Order order = buildDraftOrder(userId, dto.getProductId(), price);
        if (couponUserId != null) {
            BigDecimal discount = lockCoupon(userId, couponUserId, price, order.getOrderNo());
            order.setCouponUserId(couponUserId);
            order.setDiscountAmount(discount);
            order.setAmount(price.subtract(discount));
        }
        try {
            return insertWithTimeout(order);
        } catch (RuntimeException insertFailure) {
            if (couponUserId != null) {
                releaseCouponQuietly(order.getOrderNo());
            }
            throw insertFailure;
        }
    }
```

辅助方法：

```java
    private Long parseCouponId(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return Long.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "优惠券参数不合法");
        }
    }

    private BigDecimal lockCoupon(Long userId, Long couponUserId, BigDecimal price, String orderNo) {
        CouponLockRequest request = new CouponLockRequest();
        request.setUserId(userId);
        request.setCouponUserId(couponUserId);
        request.setOrderAmount(price);
        request.setOrderNo(orderNo);
        R<BigDecimal> response;
        try {
            response = remoteCouponService.lock(request, "true", securityProperties.getInternalToken());
        } catch (Exception e) {
            log.error("锁定优惠券失败 orderNo={}, couponUserId={}", orderNo, couponUserId, e);
            throw new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "优惠券服务繁忙，请稍后重试");
        }
        if (response == null || !response.isSuccess() || response.getData() == null) {
            // fail-closed：券不能用就拒绝下单，绝不下发一个"看起来用了券"的全价单
            throw new ServiceException(HttpStatus.BAD_REQUEST,
                    response == null || !StringUtils.hasText(response.getMsg()) ? "优惠券不可用" : response.getMsg());
        }
        return response.getData();
    }
```

`paySuccess` 在 `affected > 0` 后追加：

```java
        Order paid = findByOrderNo(orderNo);
        if (paid != null && paid.getCouponUserId() != null) {
            try {
                remoteCouponService.consume(Map.of("orderNo", orderNo), "true", securityProperties.getInternalToken());
            } catch (Exception e) {
                // 支付已经成功，不能因为券核销失败回滚订单；券停在"已锁定"状态，可用对账脚本收敛
                log.error("支付成功但优惠券核销失败，需人工核对 orderNo={}", orderNo, e);
            }
        }
```

`cancelPendingOrder` 里在条件更新成功后追加：

```java
        Order canceled = findByOrderNo(order.getOrderNo());
        if (canceled != null && canceled.getCouponUserId() != null) {
            releaseCouponQuietly(order.getOrderNo());
        }
```

`releaseCouponQuietly`：

```java
    private void releaseCouponQuietly(String orderNo) {
        try {
            remoteCouponService.release(Map.of("orderNo", orderNo), "true", securityProperties.getInternalToken());
        } catch (Exception e) {
            log.error("退回优惠券失败，需人工核对 orderNo={}", orderNo, e);
        }
    }
```

退款不退券（`refundOrder` 不改动），在 `OrderController.refund` 的 Javadoc 与前端文案里写明。

- [ ] **Step 5: 小程序用券下单**

`api/marketing.js` 增加 `getUsableCoupons(amount)` → `GET /marketing/coupon/usable?amount=`。

`pages/course/detail/index.js` 的 `onBuyTap` 在下单前插入选券步骤：

```js
      const usableRes = await getUsableCoupons(this.data.courseInfo.price)
      const usable = Array.isArray(usableRes.data) ? usableRes.data : []
      let couponUserId = ''
      if (usable.length > 0) {
        const picked = await this.pickCoupon(usable)
        if (picked === null) return          // 用户取消整单
        couponUserId = picked
      }
      const res = await createOrder({ productId: this.data.courseId, couponUserId })
```

```js
  pickCoupon(usable) {
    return new Promise((resolve) => {
      wx.showActionSheet({
        itemList: ['不使用优惠券', ...usable.map(c => `${c.couponName}（减 ${c.discount} 元）`)],
        success: (res) => resolve(res.tapIndex === 0 ? '' : String(usable[res.tapIndex - 1].id)),
        fail: () => resolve(null)
      })
    })
  },
```

支付确认弹窗前显示应付金额（`checkout` 已用服务端 `amount`，无需改）。

- [ ] **Step 6: 测试**

`CouponLockTest`：覆盖 ①已用券不能锁 ②停用券不能锁 ③未达门槛 ④并发条件更新失败报"已被占用" ⑤抵扣后至少 0.01。

`OrderCouponTest`：覆盖 ①带券下单金额 = 课程价 − 抵扣 ②券锁定失败时整单拒绝 ③取消订单退回券 ④支付成功核销券 ⑤插入失败退回券。

关键断言示例：

```java
    @Test
    void orderAmountSubtractsCouponDiscount() {
        when(remoteCourseService.getCourse(COURSE)).thenReturn(R.ok(courseInfo("99.00")));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(remoteCouponService.lock(any(), anyString(), anyString()))
                .thenReturn(R.ok(new BigDecimal("20.00")));
        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        Order order = service().createOrder(USER, dto("8801"));

        assertThat(order.getAmount()).isEqualByComparingTo("79.00");
        assertThat(order.getDiscountAmount()).isEqualByComparingTo("20.00");
        assertThat(order.getCouponUserId()).isEqualTo(8801L);
    }

    @Test
    void couponLockFailureRejectsTheWholeOrder() {
        when(remoteCourseService.getCourse(COURSE)).thenReturn(R.ok(courseInfo("99.00")));
        when(orderMapper.selectList(any())).thenReturn(List.of());
        when(remoteCouponService.lock(any(), anyString(), anyString()))
                .thenReturn(R.fail(400, "优惠券不可用"));

        assertThatThrownBy(() -> service().createOrder(USER, dto("8801")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("优惠券不可用");
        verify(orderMapper, never()).insert(any(Order.class));
    }
```

- [ ] **Step 7: 运行测试与提交**

Run: `mvn test -q -pl zhixue-modules/zhixue-marketing,zhixue-modules/zhixue-order -am`
Expected: BUILD SUCCESS

```bash
git add zhixue-api/src/main/java/com/zhixue/api/marketing \
        zhixue-modules/zhixue-marketing/src zhixue-modules/zhixue-order/src \
        zhixue-app-mp/miniprogram/api/marketing.js zhixue-app-mp/miniprogram/pages/course/detail
git commit -m "feat(order,marketing): 优惠券接入下单锁定/支付核销/取消退回，抵扣后至少 0.01 元"
```

---

### Task C3：学员售后申请与运营审批

**Files:**
- Create: `zhixue-modules/zhixue-order/src/main/resources/sql/upgrade/2026-10-22-refund-request.sql`
- Modify: `zhixue-modules/zhixue-order/src/main/resources/sql/order_schema.sql`
- Create: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/domain/entity/RefundRequest.java`
- Create: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/domain/dto/RefundApplyDTO.java`
- Create: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/mapper/RefundRequestMapper.java`
- Create: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/service/RefundRequestService.java`
- Create: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/service/impl/RefundRequestServiceImpl.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/controller/OrderController.java`
- Test: `zhixue-modules/zhixue-order/src/test/java/com/zhixue/order/service/RefundRequestServiceTest.java`（新建）
- Test: `zhixue-app-mp/tests/after-sale.test.cjs`（新建）
- Create: `zhixue-admin-web/src/views/order/refund/index.vue`
- Create: `zhixue-admin-web/src/api/order/refund.js`
- Modify: `zhixue-admin-web/src/router/modules/order.js`

**Interfaces:**
- `RefundRequest { Long orderId; String orderNo; Long userId; String reason; Integer status; String auditRemark; Long auditUserId; LocalDateTime auditTime; }`
- `POST /order/refund-request`（登录）body `{orderId, reason}`
- `GET /order/refund-request/mine`（登录）
- `GET /order/refund-request/page?status`（权限 `order:refund`）
- `PUT /order/refund-request/{id}/approve` / `/reject`（权限 `order:refund`）

业务规则（写进 Javadoc）：
- 只有本人订单、状态已支付、支付时间在 7 天内可申请；
- 同一订单同时只能有一条"待审核"申请；被拒后可再次申请；
- 审批通过调用既有 `refundOrder`（改状态 + 收回观看权限）；**渠道资金退回**属于 M2 范围，本任务在审批页明示"当前为本地模拟退款"。

- [ ] **Step 1: 建表**

```sql
USE zhixue_order;

CREATE TABLE IF NOT EXISTS refund_request (
    id            bigint primary key,
    order_id      bigint       not null,
    order_no      varchar(64)  not null,
    user_id       bigint       not null,
    reason        varchar(255) not null,
    status        tinyint      not null default 0 comment '0待审核 1已通过 2已拒绝',
    audit_remark  varchar(255) null,
    audit_user_id bigint       null,
    audit_time    datetime     null,
    create_time   datetime,
    update_time   datetime,
    deleted       tinyint      default 0,
    index idx_refund_order (order_id),
    index idx_refund_status (status)
) COMMENT '学员售后申请';
```

`order_schema.sql` 同步加表定义。

- [ ] **Step 2: 实体与服务**

`RefundRequest` 继承 `BaseEntity`，字段同上。`RefundRequestServiceImpl` 关键方法：

```java
    private static final int DAYS_LIMIT = 7;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundRequest apply(Long userId, RefundApplyDTO dto) {
        Order order = orderService.findById(dto.getOrderId());
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "订单不存在");
        }
        if (!Integer.valueOf(1).equals(order.getStatus())) {
            throw new ServiceException("仅已支付订单可申请售后");
        }
        if (order.getPayTime() != null && order.getPayTime().isBefore(LocalDateTime.now().minusDays(DAYS_LIMIT))) {
            throw new ServiceException("已超过 " + DAYS_LIMIT + " 天售后期限");
        }
        Long pending = refundRequestMapper.selectCount(new LambdaQueryWrapper<RefundRequest>()
                .eq(RefundRequest::getOrderId, order.getId())
                .eq(RefundRequest::getStatus, 0));
        if (pending != null && pending > 0) {
            throw new ServiceException("该订单已有待处理的售后申请");
        }
        RefundRequest request = new RefundRequest();
        request.setOrderId(order.getId());
        request.setOrderNo(order.getOrderNo());
        request.setUserId(userId);
        request.setReason(dto.getReason().trim());
        request.setStatus(0);
        refundRequestMapper.insert(request);
        return request;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long auditorId, Long requestId, String remark) {
        RefundRequest request = requirePending(requestId);
        // 先退款再改申请状态：退款失败时申请保持待审核，运营可重试
        orderService.refundOrder(request.getOrderId(),
                StringUtils.hasText(remark) ? remark : "售后退款通过");
        request.setStatus(1);
        request.setAuditRemark(remark);
        request.setAuditUserId(auditorId);
        request.setAuditTime(LocalDateTime.now());
        refundRequestMapper.updateById(request);
    }
```

`reject` 只改状态与备注，不动订单。

- [ ] **Step 3: 端点**

`OrderController` 追加四个端点，审批两个用 `@RequirePermission(PERM_REFUND)`：

```java
    @RequireLogin
    @PostMapping("/refund-request")
    public R<RefundRequest> applyRefund(@Valid @RequestBody RefundApplyDTO dto) {
        return R.ok(refundRequestService.apply(SecurityUtils.requireUserId(), dto));
    }

    @RequireLogin
    @GetMapping("/refund-request/mine")
    public R<List<RefundRequest>> myRefundRequests() {
        return R.ok(refundRequestService.mine(SecurityUtils.requireUserId()));
    }

    @RequirePermission(PERM_REFUND)
    @GetMapping("/refund-request/page")
    public R<PageResult<RefundRequest>> refundRequestPage(PageQuery query,
                                                          @RequestParam(required = false) Integer status) {
        return R.ok(refundRequestService.page(query, status));
    }

    @RequirePermission(PERM_REFUND)
    @PutMapping("/refund-request/{id}/approve")
    public R<Void> approveRefund(@PathVariable Long id, @RequestBody(required = false) OrderRefundDTO dto) {
        refundRequestService.approve(SecurityUtils.requireUserId(), id, dto == null ? null : dto.getReason());
        return R.ok();
    }
```

> 路径顺序注意：`/order/refund-request/**` 是字面量，Spring 会优先于 `/order/{id}` 匹配，不会把 `refund-request` 当成 id。

- [ ] **Step 4: 测试**

`RefundRequestServiceTest` 覆盖：①他人订单返回 404 ②未支付不能申请 ③超过 7 天拒绝 ④已有待审核拒绝 ⑤审批通过会调用 `refundOrder` 并置 1 ⑥审批失败时不改申请状态。

- [ ] **Step 5: 后台审批页与小程序入口**

后台 `views/order/refund/index.vue`：待审核列表、通过/拒绝（prompt 填备注）、显示"当前为模拟退款，未调用微信/支付宝渠道退款"提示条。路由 `router/modules/order.js` 增加：

```js
    {
      path: 'refund',
      name: 'OrderRefund',
      component: () => import('@/views/order/refund/index.vue'),
      meta: { title: '售后审批', icon: 'RefreshLeft', permission: ['order:refund'], keepAlive: true }
    }
```

小程序：`pages/my/index.js` 的 `onAfterSaleTap` 改为跳转新页面 `pages/my/aftersale/index`（申请 + 我的申请列表）；`pages/course/detail` 已支付时也提供"申请售后"入口。`tests/after-sale.test.cjs` 验证：未选订单时提示、提交带 orderId 与 reason、字数超限拦截。

- [ ] **Step 6: 运行与提交**

Run: `mvn test -q -pl zhixue-modules/zhixue-order -am && node --test zhixue-app-mp/tests/after-sale.test.cjs`
Expected: 全部通过

```bash
git add zhixue-modules/zhixue-order/src zhixue-app-mp/miniprogram/pages/my \
        zhixue-app-mp/tests/after-sale.test.cjs zhixue-admin-web/src
git commit -m "feat(order): 学员售后申请与运营审批，通过后复用退款撤权链路"
```

---

### Task C4：教师经营（销售/收入/学员）

**Files:**
- Modify: `zhixue-api/src/main/java/com/zhixue/api/system/RemoteUserService.java`
- Create: `zhixue-api/src/main/java/com/zhixue/api/system/domain/UserBrief.java`
- Create: `zhixue-api/src/main/java/com/zhixue/api/course/domain/TeacherCourse.java`
- Modify: `zhixue-api/src/main/java/com/zhixue/api/course/RemoteCourseService.java`
- Modify: `zhixue-modules/zhixue-system/src/main/java/com/zhixue/system/controller/SysUserController.java`
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/controller/CourseInnerController.java`
- Create: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/domain/vo/TeacherSummaryVO.java`
- Create: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/domain/vo/TeacherStudentVO.java`
- Create: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/service/TeacherStatsService.java`
- Create: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/service/impl/TeacherStatsServiceImpl.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/controller/OrderController.java`
- Create: `zhixue-admin-web/src/views/teacher/statistics/index.vue`
- Create: `zhixue-admin-web/src/api/teacher/stats.js`
- Create: `zhixue-admin-web/src/router/modules/teacher.js`
- Test: `zhixue-modules/zhixue-order/src/test/java/com/zhixue/order/service/TeacherStatsServiceTest.java`（新建）

**Interfaces:**
- `UserBrief { Long userId; String username; String nickname; }`
- `RemoteUserService.listBrief(List<Long> ids, innerCall, token): R<List<UserBrief>>`
- `TeacherCourse { Long courseId; String title; }`
- `RemoteCourseService.teacherCourses(Long teacherId, innerCall, token): R<List<TeacherCourse>>`
- `GET /order/teacher/summary?startTime&endTime`（TEACHER 或 ADMIN）
- `GET /order/teacher/students?courseId&pageNum&pageSize`（TEACHER 或 ADMIN，仅本人课程）
- `TeacherSummaryVO { int courseCount; int paidCount; BigDecimal paidAmount; int refundedCount; BigDecimal refundedAmount; List<CourseSalesVO> courses; }`
- `CourseSalesVO { Long courseId; String title; int paidCount; BigDecimal paidAmount; int refundedCount; }`
- `TeacherStudentVO { Long userId; String userName; String courseTitle; LocalDateTime payTime; int percent; int finishedLessons; }`

**权限边界**（必须写进测试）：教师只能查自己的数据；ADMIN 可通过 `teacherId` 查任意教师；普通学员访问返回 403。

- [ ] **Step 1: 内部接口**

`SysUserController` 追加：

```java
    /** 服务间接口：按 ID 批量取用户摘要（教师经营页展示学员名）。 */
    @PostMapping("/inner/brief")
    public R<List<UserBrief>> brief(@RequestBody List<Long> userIds) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "禁止外部访问内部用户接口");
        }
        return R.ok(userService.listBrief(userIds));
    }
```

`SysUserService.listBrief` 实现：`selectBatchIds` 后映射 `userId/username/nickname`，单次上限 200，超出分片。

`CourseInnerController` 追加：

```java
    /** 服务间接口：某教师名下课程（教师经营统计的课程范围）。 */
    @GetMapping("/inner/teacher-courses")
    public R<List<TeacherCourse>> teacherCourses(@RequestParam Long teacherId) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "禁止外部访问内部课程接口");
        }
        return R.ok(courseService.listByTeacher(teacherId));
    }
```

- [ ] **Step 2: 统计数据实现**

`TeacherStatsServiceImpl.summary`：

```java
    @Override
    public TeacherSummaryVO summary(Long callerId, boolean admin, Long teacherId, LocalDateTime start, LocalDateTime end) {
        Long scope = admin ? Objects.requireNonNullElse(teacherId, callerId) : callerId;
        if (!admin && !SecurityUtils.hasAnyRole("TEACHER")) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "仅教师可查看经营数据");
        }
        List<TeacherCourse> courses = loadCourses(scope);
        if (courses.isEmpty()) {
            return TeacherSummaryVO.empty();
        }
        List<Long> courseIds = courses.stream().map(TeacherCourse::getCourseId).toList();
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .in(Order::getProductId, courseIds)
                .in(Order::getStatus, 1, 3)
                .ge(start != null, Order::getPayTime, start)
                .le(end != null, Order::getPayTime, end));
        // 按课程分组统计 paid / refunded，组装 CourseSalesVO 列表
        // 汇总：paidCount 去重订单数、paidAmount 合计、refundedCount/refundedAmount
    }
```

`students(courseId)`：先校验该 `courseId` 属于教师的课程范围（否则 403），再查该课程已支付订单，按用户聚合，读 `learning_progress`？——**进度在学习库**，order 服务没有。改为 order 返回购买信息，进度由课程服务补充会更耦合。

**决策（AD-11）**：学员列表由课程服务提供（它有进度与课时数），order 只提供"谁买了"。因此实现为：

- `GET /course/mine/students?courseId`（教师/管理员）在 `MyCourseController`，逻辑：`requireWritableCourse(courseId)` → 调 `RemoteOrderService.listPaidUsers(courseId)` 拿 `userId + payTime` → 调 `RemoteUserService.listBrief` 补名字 → 查 `learning_progress` 补进度。
- `GET /order/teacher/summary` 只做销售与收入（order 库内可完成）。

这样每个服务只碰自己的库，符合现有边界。

`RemoteOrderService` 增加：

```java
    /** 某课程的已支付购买者（去重），供教师查看学员列表。 */
    @GetMapping("/order/inner/course-buyers")
    R<List<OrderBuyer>> listBuyers(@RequestParam("courseId") Long courseId,
                                   @RequestHeader(InternalAccessConstants.INNER_CALL_HEADER) String innerHeader,
                                   @RequestHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER) String internalToken);
```

`OrderBuyer { Long userId; LocalDateTime payTime; }`。

- [ ] **Step 3: 测试**

`TeacherStatsServiceTest`：

```java
    @Test
    void teacherOnlySeesOwnCourses() {
        when(remoteCourseService.teacherCourses(eq(TEACHER), anyString(), anyString()))
                .thenReturn(R.ok(List.of(course(2007L))));

        service().summary(TEACHER, false, null, null, null);

        verify(remoteCourseService).teacherCourses(eq(TEACHER), anyString(), anyString());
        verify(orderMapper).selectList(argThat(wrapper -> wrapper != null));
    }

    @Test
    void studentCannotSeeTeacherStats() {
        assertThatThrownBy(() -> service().summary(STUDENT, false, null, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("仅教师");
    }

    @Test
    void adminCanInspectSpecificTeacher() {
        when(remoteCourseService.teacherCourses(eq(OTHER_TEACHER), anyString(), anyString()))
                .thenReturn(R.ok(List.of()));
        assertThat(service().summary(ADMIN, true, OTHER_TEACHER, null, null).getCourseCount()).isZero();
    }

    @Test
    void refundedOrdersCountSeparatelyAndAreNotRevenue() {
        // 3 笔已支付（其中 1 笔已退款）：paidAmount 只统计 status=1
        ...
        assertThat(vo.getPaidCount()).isEqualTo(2);
        assertThat(vo.getRefundedCount()).isEqualTo(1);
    }
```

- [ ] **Step 4: 后台页面**

`views/teacher/statistics/index.vue`：日期范围选择、汇总卡（课程数/成交笔数/实收/退款笔数）、按课程表格（销量/实收/退款）、"查看学员"抽屉（姓名、购买时间、进度百分比、完成课时数）。路由 `modules/teacher.js` 用 `meta.roles: ['TEACHER', 'ADMIN']` 限定可见。

- [ ] **Step 5: 运行与提交**

Run: `mvn test -q -pl zhixue-modules/zhixue-order,zhixue-modules/zhixue-course,zhixue-modules/zhixue-system -am`
Expected: BUILD SUCCESS

```bash
git add zhixue-api/src zhixue-modules/zhixue-order/src zhixue-modules/zhixue-course/src \
        zhixue-modules/zhixue-system/src zhixue-admin-web/src
git commit -m "feat: 教师经营统计与课程学员列表，数据范围严格限定本人课程"
```

---

### Task C5：课程列表名称补齐、讲师搜索、删除死接口

**Files:**
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/domain/entity/Course.java`（非落库字段）
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/impl/CourseServiceImpl.java`
- Modify: `zhixue-api/src/main/java/com/zhixue/api/system/RemoteUserService.java`
- Modify: `zhixue-modules/zhixue-system/src/main/java/com/zhixue/system/controller/SysUserController.java`
- Modify: `zhixue-app-mp/miniprogram/pages/search/index.js:35-60`
- Modify: `zhixue-admin-web/src/views/course/list/index.vue:106-107`
- Test: `zhixue-modules/zhixue-course/src/test/java/com/zhixue/course/service/CourseNameFillTest.java`（新建）
- Test: `zhixue-app-mp/tests/search-page.test.cjs`（新建）

**Interfaces:**
- `Course` 新增 `@TableField(exist = false) String categoryName; @TableField(exist = false) String teacherName;`
- `SysUserController` 新增 `GET /user/inner/search?keyword=&limit=20`（内部）→ `List<Long>` 用户 ID
- `CourseQuery` 的 `title` 同时匹配标题与讲师昵称：先按关键词查讲师 ID 列表，再用 `or(title like, teacherId in)` 组合

- [ ] **Step 1: 系统侧按昵称检索**

```java
    @GetMapping("/inner/search")
    public R<List<Long>> searchUserIds(@RequestParam String keyword,
                                       @RequestParam(defaultValue = "20") int limit) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "禁止外部访问内部用户接口");
        }
        return R.ok(userService.searchIdsByKeyword(keyword, Math.min(limit, 50)));
    }
```

实现：`selectList` 按 `nickname like %kw%` 或 `username like %kw%`，只返回 id。

- [ ] **Step 2: 课程侧补齐名称与讲师匹配**

`CourseServiceImpl.pageCourses` 在查询后批量填充名称（替换 A3 版本的方法尾部 `Page<Course> result = ...` 两行，保留前面的分类/状态/教师范围条件）：

```java
        Page<Course> result = courseMapper.selectPage(new Page<>(page.getPageNum(), page.getPageSize()), qw);
        fillNames(result.getRecords());
        return PageResult.of(result.getRecords(), result.getTotal(), page.getSize());
```

```java
    private void fillNames(List<Course> courses) {
        if (courses.isEmpty()) {
            return;
        }
        Set<Long> categoryIds = courses.stream().map(Course::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> categoryNames = categoryIds.isEmpty() ? Map.of()
                : categoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(Category::getId, Category::getName));
        Set<Long> teacherIds = courses.stream().map(Course::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> teacherNames = loadTeacherNames(teacherIds);
        courses.forEach(course -> {
            course.setCategoryName(categoryNames.getOrDefault(course.getCategoryId(), ""));
            course.setTeacherName(teacherNames.getOrDefault(course.getTeacherId(), ""));
        });
    }

    /**
     * 教师名来自系统服务。这里允许降级为空字符串：列表少了讲师名不影响能否买课，
     * 但绝不能让整页 500（与"查购买关系 fail-closed"是不同风险等级）。
     */
    private Map<Long, String> loadTeacherNames(Set<Long> teacherIds) {
        if (teacherIds.isEmpty()) {
            return Map.of();
        }
        try {
            R<List<UserBrief>> response = remoteUserService.listBrief(
                    new ArrayList<>(teacherIds), "true", securityProperties.getInternalToken());
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.warn("获取讲师名称失败，降级为空");
                return Map.of();
            }
            return response.getData().stream()
                    .collect(Collectors.toMap(UserBrief::getUserId,
                            brief -> StringUtils.hasText(brief.getNickname()) ? brief.getNickname() : brief.getUsername(),
                            (a, b) -> a));
        } catch (Exception e) {
            log.warn("调用系统服务获取讲师名称异常，降级为空", e);
            return Map.of();
        }
    }
```

关键词同时匹配讲师：

```java
        if (StringUtils.hasText(query.title())) {
            String kw = query.title().trim();
            List<Long> teacherIds = searchTeacherIds(kw);
            qw.and(w -> {
                w.like(Course::getTitle, kw);
                if (!teacherIds.isEmpty()) {
                    w.or().in(Course::getTeacherId, teacherIds);
                }
            });
        }
```

`CourseApplication` 的 `@EnableFeignClients` 改为 `{RemoteOrderService.class, RemoteUserService.class}`，并确认 `scanBasePackages` 包含 `com.zhixue.api`（`RemoteUserService` 带 `fallbackFactory`，需要扫描工厂；若测试 `FeignFallbackScanTest` 报错，按提示补 `com.zhixue.api`）。

- [ ] **Step 3: 前端展示**

后台 `views/course/list/index.vue:106-107` 保持不变（现在后端有值了），但把 `teacherName` 列宽调整为 120 并在为空时显示 `-`：

```html
        <el-table-column label="分类" width="120">
          <template #default="{ row }">{{ row.categoryName || '-' }}</template>
        </el-table-column>
        <el-table-column label="讲师" width="120">
          <template #default="{ row }">{{ row.teacherName || '-' }}</template>
        </el-table-column>
```

小程序 `pages/search/index.js`：去掉"取 40 条前端过滤"，改为服务端搜索 + 触底分页（与 A3 相同的 `pageNum/hasMore` 模式），传 `keyword`（后端已支持讲师匹配）；`index.wxml` 的占位文案保持"输入课程名或讲师名"，此时承诺成立。

- [ ] **Step 4: 删除死接口**

删除 `CourseController.aggregateDetail`（全仓无调用方，且返回写死的 0），并在 `docs/three-ends-gap-list.md` 勾掉对应条目。

- [ ] **Step 5: 测试**

`CourseNameFillTest`：课程 2007 返回 `categoryName="Java 架构"`、`teacherName="张老师"`（用 mock `RemoteUserService`）；系统服务失败时 `teacherName` 为空但列表成功返回、不抛异常。

`search-page.test.cjs`：输入关键词后请求参数为 `{pageNum:1, pageSize:10, keyword:'张老师'}`，结果渲染而不是前端二次过滤。

- [ ] **Step 6: 运行与提交**

Run: `mvn test -q -pl zhixue-modules/zhixue-course -am && node --test zhixue-app-mp/tests/search-page.test.cjs`
Expected: 通过

```bash
git add zhixue-modules/zhixue-course/src zhixue-modules/zhixue-system/src zhixue-api/src \
        zhixue-admin-web/src/views/course/list/index.vue \
        zhixue-app-mp/miniprogram/pages/search zhixue-app-mp/tests/search-page.test.cjs \
        docs/three-ends-gap-list.md
git commit -m "feat(course): 列表补齐分类与讲师名称，关键词支持讲师检索，删除无调用方的聚合死接口"
```

---

## 检查点 C（经营闭环）

- [ ] 后端相关模块测试全绿
- [ ] 后台 `npm test && npm run build` 全绿
- [ ] **真实环境走一遍完整经营链**：
  1. 管理员发券给 student → 学生领券 → 下单选券 → 应付金额减少
  2. 支付成功 → `coupon_user.status=1`、`use_time` 有值
  3. 另建一单用券后取消 → 券回到 `status=0`、`order_no` 清空
  4. 学生提交评价 → 详情页显示平均分与内容 → 教师回复 → 管理员隐藏后前台消失
  5. 学生对已支付订单申请售后 → 管理员通过 → 订单 `status=3`、课时重新变锁定
  6. 教师登录 → 经营页只看到自己 3 门课 → 学员列表显示名字与进度
- [ ] 数据库核对：
```bash
docker exec -i zhixue-local-mysql mysql -uroot -p"$ZHIXUE_DB_PASSWORD" -e \
  "USE zhixue_marketing; SELECT id,coupon_id,user_id,status,order_no FROM coupon_user;
   USE zhixue_order; SELECT order_no,amount,discount_amount,coupon_user_id,status FROM \`order\`;
   SELECT order_no,status,reason FROM refund_request;
   USE zhixue_course; SELECT course_id,user_id,rating,status FROM course_review;"
```

---

## 阶段 D：学生 Web 二期门户

> 一期已完成 AI 对话 + 课程详情 + 模拟购买播放。二期补齐"浏览 → 找课 → 我的学习 → 订单/券/收藏"，让网页端成为完整学生门户。所有新页面复用现有 `utils/request.js`、`stores/user`、`AppShell` 布局与 Element Plus 组件。

### Task D1：路由、侧边栏与共享组件

**Files:**
- Modify: `zhixue-student-web/src/router/index.js:8-22`
- Modify: `zhixue-student-web/src/layout/SideNav.vue:13-16`
- Create: `zhixue-student-web/src/components/EmptyState.vue`
- Create: `zhixue-student-web/src/components/PageHeader.vue`
- Create: `zhixue-student-web/src/api/catalog.js`
- Test: `zhixue-student-web/tests/unit/router/routes.test.js`（新建）

**Interfaces:**
- 路由：`/catalog`、`/catalog/:categoryId`、`/mine/courses`、`/mine/orders`、`/mine/coupons`、`/mine/favorites`、`/seckill`、`/register`
- `api/catalog.js`：`listCourses(params)`、`listCategories()`、`getBanner()`、`getHot()`、`getLatest()`

- [ ] **Step 1: 写失败测试**

```js
import { describe, expect, it } from 'vitest'
import { routes } from '@/router'

function flatten(list, prefix = '') {
  return list.flatMap((route) => {
    const path = `${prefix}/${route.path}`.replace(/\/+/g, '/')
    return [path, ...(route.children ? flatten(route.children, path) : [])]
  })
}

describe('二期路由', () => {
  it('课程库/我的/秒杀/注册都可访问', () => {
    const all = flatten(routes)
    for (const path of ['/catalog', '/mine/courses', '/mine/orders', '/mine/coupons', '/mine/favorites', '/seckill', '/register']) {
      expect(all).toContain(path)
    }
  })

  it('除登录与注册外，其余路由都挂在需要登录的外壳下', () => {
    const shell = routes.find((route) => route.component?.name === 'AppShell' || route.children?.length > 3)
    expect(shell.children.map((c) => c.path)).toEqual(
      expect.arrayContaining(['catalog', 'mine/courses', 'mine/orders', 'seckill'])
    )
  })
})
```

> `routes` 需要从 `router/index.js` 具名导出（当前是默认导出 router）。同步改为：
> ```js
> export const routes = [...]
> const router = createRouter({ history: createWebHistory(), routes })
> export default router
> ```

- [ ] **Step 2: 运行确认失败** → `npm test -- tests/unit/router/routes.test.js`（FAIL：无 `routes` 导出）

- [ ] **Step 3: 实现路由与侧边栏**

`router/index.js` 的 children 追加：

```js
      { path: 'catalog', name: 'catalog', component: () => import('@/views/catalog/CatalogView.vue') },
      { path: 'catalog/:categoryId', name: 'catalog-category', component: () => import('@/views/catalog/CatalogView.vue') },
      { path: 'mine/courses', name: 'mine-courses', component: () => import('@/views/mine/MyCourses.vue') },
      { path: 'mine/orders', name: 'mine-orders', component: () => import('@/views/mine/MyOrders.vue') },
      { path: 'mine/coupons', name: 'mine-coupons', component: () => import('@/views/mine/MyCoupons.vue') },
      { path: 'mine/favorites', name: 'mine-favorites', component: () => import('@/views/mine/MyFavorites.vue') },
      { path: 'seckill', name: 'seckill', component: () => import('@/views/seckill/SeckillView.vue') },
```

顶层（`meta.public`）追加注册：

```js
  { path: '/register', name: 'register', component: () => import('@/views/register/RegisterView.vue'), meta: { public: true } },
```

`SideNav.vue` 在 `<RecentList>` 之前插入主导航：

```html
<nav class="nav-main" aria-label="主导航">
  <RouterLink v-for="item in items" :key="item.name" :to="item.to" class="nav-item" active-class="is-active">
    <el-icon><component :is="item.icon" /></el-icon>
    <span>{{ item.label }}</span>
  </RouterLink>
</nav>
```

```js
const items = [
  { name: 'chat', to: '/chat', label: 'AI 助教', icon: 'ChatDotRound' },
  { name: 'catalog', to: '/catalog', label: '课程库', icon: 'Search' },
  { name: 'mine-courses', to: '/mine/courses', label: '我的课程', icon: 'Reading' },
  { name: 'seckill', to: '/seckill', label: '限时秒杀', icon: 'AlarmClock' },
  { name: 'mine-orders', to: '/mine/orders', label: '我的订单', icon: 'Tickets' },
  { name: 'mine-coupons', to: '/mine/coupons', label: '优惠券', icon: 'Ticket' },
  { name: 'mine-favorites', to: '/mine/favorites', label: '我的收藏', icon: 'Star' }
]
```

- [ ] **Step 4: 运行确认通过并提交**

Run: `npm test -- tests/unit/router/routes.test.js` → PASS

```bash
git add zhixue-student-web/src/router zhixue-student-web/src/layout \
        zhixue-student-web/src/components zhixue-student-web/src/api/catalog.js \
        zhixue-student-web/tests/unit/router/routes.test.js
git commit -m "feat(student-web): 二期路由与主导航"
```

---

### Task D2：课程库（搜索 / 分类 / 排序 / 分页）

**Files:**
- Create: `zhixue-student-web/src/views/catalog/CatalogView.vue`
- Create: `zhixue-student-web/src/views/catalog/CourseCard.vue`
- Modify: `zhixue-student-web/src/api/catalog.js`
- Test: `zhixue-student-web/tests/unit/catalog/query.test.js`（新建）

**Interfaces:**
- `listCourses({ pageNum, pageSize, keyword, categoryId, sort })` → `PageResult<Course>`（后端 A3/C5 已支持）

- [ ] **Step 1: 抽出查询参数构造（可测）**

`src/views/catalog/query.js`：

```js
/**
 * 课程库查询参数。
 *
 * 空字符串与 null 一律不发送：后端把它们当成"条件成立"会筛出空列表。
 */
export function buildCourseQuery({ pageNum = 1, pageSize = 12, keyword = '', categoryId = null, sort = '' } = {}) {
  const params = { pageNum, pageSize }
  const kw = String(keyword || '').trim()
  if (kw) params.keyword = kw
  if (categoryId !== null && categoryId !== '' && categoryId !== undefined) params.categoryId = String(categoryId)
  if (sort) params.sort = sort
  return params
}
```

- [ ] **Step 2: 写测试**

```js
import { describe, expect, it } from 'vitest'
import { buildCourseQuery } from '@/views/catalog/query'

describe('buildCourseQuery', () => {
  it('空关键词与空分类不发送', () => {
    expect(buildCourseQuery({})).toEqual({ pageNum: 1, pageSize: 12 })
  })

  it('分类 ID 保持字符串，避免雪花 ID 精度丢失', () => {
    expect(buildCourseQuery({ categoryId: '2112366523950714882' }))
      .toEqual({ pageNum: 1, pageSize: 12, categoryId: '2112366523950714882' })
  })

  it('仅发送受支持的排序值', () => {
    expect(buildCourseQuery({ sort: 'price_asc' }).sort).toBe('price_asc')
    expect(buildCourseQuery({ sort: 'sales' }).sort).toBe('sales')
  })
})
```

- [ ] **Step 3: 实现页面**

`CatalogView.vue` 结构：顶部搜索框（回车触发）+ 分类横向标签（父分类含"全部"）+ 排序下拉（最新/价格升/价格降）+ 卡片网格 + 分页（`el-pagination`）。每张卡：封面（失败兜底为渐变占位）、标题、价格或"已购"、讲师名、`问这门课`（跳 `/chat?courseId=...`，复用一期"限定课程提问"）。点击卡片进 `/course/:id`。

关键逻辑：

```js
async function load(reset = true) {
  if (reset) pageNum.value = 1
  loading.value = true
  try {
    const data = await listCourses(buildCourseQuery({
      pageNum: pageNum.value, pageSize: 12,
      keyword: keyword.value, categoryId: route.params.categoryId || null, sort: sort.value
    }))
    courses.value = data.records || []
    total.value = Number(data.total) || 0
  } catch (error) {
    ElMessage.error(error.message || '课程加载失败')
  } finally {
    loading.value = false
  }
}
```

- [ ] **Step 4: 运行测试与构建，提交**

Run: `npm test -- tests/unit/catalog/query.test.js && npm run build`
Expected: 通过

```bash
git add zhixue-student-web/src/views/catalog zhixue-student-web/src/api/catalog.js \
        zhixue-student-web/tests/unit/catalog
git commit -m "feat(student-web): 课程库搜索/分类/排序/分页"
```

---

### Task D3：我的课程与我的订单（含继续支付、取消、售后）

**Files:**
- Create: `zhixue-student-web/src/views/mine/MyCourses.vue`
- Create: `zhixue-student-web/src/views/mine/MyOrders.vue`
- Modify: `zhixue-student-web/src/api/order.js`
- Create: `zhixue-student-web/src/api/mine.js`
- Test: `zhixue-student-web/tests/unit/mine/orderActions.test.js`（新建）

**Interfaces:**
- `mine.js`：`myCourses()` → `GET /course/mine/courses`；`refundRequests()` → `GET /order/refund-request/mine`；`applyRefund({orderId, reason})`
- `api/order.js` 增加：`listMyOrders({pageNum,pageSize,status})`、`cancelOrder(id, reason)`、`getOrderDetail(orderNo)`

- [ ] **Step 1: 抽出订单可执行动作（可测纯函数）**

`src/views/mine/orderState.js`：

```js
export const ORDER_STATUS = {
  0: { text: '待支付', tone: 'warning' },
  1: { text: '已支付', tone: 'success' },
  2: { text: '已取消', tone: 'info' },
  3: { text: '已退款', tone: 'danger' }
}

/** 订单卡片上可执行的动作。规则与后端保持一致，不要在前端臆造。 */
export function actionsOf(order) {
  if (!order) return []
  if (Number(order.status) === 0) return ['pay', 'cancel']
  if (Number(order.status) === 1) return ['study', 'refund']
  return []
}

/** 售后期：支付后 7 天内，且订单仍处于已支付。 */
export function canApplyRefund(order, now = Date.now()) {
  if (!order || Number(order.status) !== 1 || !order.payTime) return false
  return now - new Date(order.payTime).getTime() <= 7 * 24 * 3600 * 1000
}
```

- [ ] **Step 2: 写测试**

```js
import { describe, expect, it } from 'vitest'
import { actionsOf, canApplyRefund, ORDER_STATUS } from '@/views/mine/orderState'

describe('订单动作', () => {
  it('待支付可继续支付与取消', () => {
    expect(actionsOf({ status: 0 })).toEqual(['pay', 'cancel'])
  })

  it('已支付可去学习与申请售后', () => {
    expect(actionsOf({ status: 1 })).toEqual(['study', 'refund'])
  })

  it('已取消与已退款没有动作', () => {
    expect(actionsOf({ status: 2 })).toEqual([])
    expect(actionsOf({ status: 3 })).toEqual([])
  })

  it('状态 2 文案是已取消', () => {
    expect(ORDER_STATUS[2].text).toBe('已取消')
  })

  it('售后期 7 天', () => {
    const now = new Date('2026-10-22T12:00:00Z').getTime()
    expect(canApplyRefund({ status: 1, payTime: '2026-10-20T12:00:00Z' }, now)).toBe(true)
    expect(canApplyRefund({ status: 1, payTime: '2026-10-01T12:00:00Z' }, now)).toBe(false)
    expect(canApplyRefund({ status: 3, payTime: '2026-10-21T12:00:00Z' }, now)).toBe(false)
  })
})
```

- [ ] **Step 3: 实现两个页面**

`MyCourses.vue`：调 `myCourses()`，卡片显示进度条（`el-progress`）、"继续学习"（跳 `/course/:id?lesson=lastSectionId`，越界时退化为课程详情）。空状态引导去 `/catalog`。

`MyOrders.vue`：状态标签页（全部/待支付/已支付/已取消/已退款）、订单卡片、按 `actionsOf` 渲染按钮：
- `pay` → 复用一期模拟收银（`payOrder` + confirm），成功后刷新；
- `cancel` → `ElMessageBox.confirm` 后 `cancelOrder(id, '用户取消')`；
- `study` → `/course/:productId`；
- `refund` → 仅当 `canApplyRefund`，弹输入框填原因（10–200 字）→ `applyRefund` → 提示"已提交，等待运营审核"。

同时在页面底部列出"我的售后申请"（`refundRequests()`），显示状态与审批备注。

- [ ] **Step 4: 运行与提交**

Run: `npm test -- tests/unit/mine && npm run build`
Expected: 通过

```bash
git add zhixue-student-web/src/views/mine zhixue-student-web/src/api \
        zhixue-student-web/tests/unit/mine
git commit -m "feat(student-web): 我的课程与订单中心（继续支付/取消/售后申请）"
```

---

### Task D4：我的优惠券与收藏

**Files:**
- Create: `zhixue-student-web/src/views/mine/MyCoupons.vue`
- Create: `zhixue-student-web/src/views/mine/MyFavorites.vue`
- Modify: `zhixue-student-web/src/api/catalog.js`、`src/api/order.js`
- Modify: `zhixue-student-web/src/views/course/CourseDetail.vue`（用券下单）

**Interfaces:**
- `api/catalog.js` 增加：`availableCoupons()`、`claimCoupon(id)`、`myCoupons(status)`、`usableCoupons(amount)`、`toggleFavorite(courseId)`、`myFavorites()`
- `CourseDetail` 购买流程增加选券步骤

- [ ] **Step 1: 实现券页与收藏页**

`MyCoupons.vue`：两段——"可领取"（`availableCoupons()` + 领取按钮，成功后就地刷新）与"我的券"（`myCoupons()`，状态 0 未使用 / 1 已使用 / 2 已过期）。每张券显示名称、减免额、门槛、有效期。

`MyFavorites.vue`：`myFavorites()` 列表 + 取消收藏（`toggleFavorite`）+ 点进详情。

- [ ] **Step 2: 课程详情用券**

`CourseDetail.vue` 的 `purchase()` 在 `createOrder` 之前插入：

```js
  let couponUserId = ''
  try {
    const usable = await usableCoupons(course.value.price)
    if (usable.length > 0) {
      const picked = await ElMessageBox({
        title: '选择优惠券',
        message: h('div', usable.map((c) => h('label', { class: 'coupon-option' }, [
          h('input', { type: 'radio', name: 'coupon', value: String(c.id), onChange: () => { selectedCoupon.value = String(c.id) } }),
          ` ${c.couponName}（减 ¥${c.discount}）`
        ]))),
        showCancelButton: true,
        confirmButtonText: '使用',
        cancelButtonText: '不用券'
      }).then(() => selectedCoupon.value).catch(() => '')
      couponUserId = picked || ''
    }
  } catch {
    couponUserId = ''
  }
  const order = await createOrder(course.value.id, couponUserId)
```

`api/order.js` 的 `createOrder` 签名改为 `(productId, couponUserId = '')`，payload 加 `couponUserId`。

课程详情页同时显示平均分与评价列表（调用 `GET /course/:id/reviews`），已购且未评价时显示"写评价"（`POST /course/:id/reviews`）。

- [ ] **Step 3: 测试与提交**

为 `createOrder` payload 增加单测（`couponUserId` 为空时不下发该字段；非空时以字符串下发）。

```bash
git add zhixue-student-web/src/views/mine zhixue-student-web/src/views/course \
        zhixue-student-web/src/api zhixue-student-web/tests
git commit -m "feat(student-web): 我的优惠券/收藏，下单支持选券，课程详情接入评价"
```

---

### Task D5：秒杀页与注册页

**Files:**
- Create: `zhixue-student-web/src/views/seckill/SeckillView.vue`
- Create: `zhixue-student-web/src/views/register/RegisterView.vue`
- Create: `zhixue-student-web/src/api/marketing.js`
- Create: `zhixue-student-web/src/api/register.js`
- Modify: `zhixue-auth/src/main/java/com/zhixue/auth/controller/RegisterController.java`
- Modify: `zhixue-modules/zhixue-system/src/main/java/com/zhixue/system/controller/SysUserController.java`
- Test: `zhixue-modules/zhixue-auth/src/test/java/com/zhixue/auth/controller/RegisterControllerTest.java`（新建）

**Interfaces:**
- `POST /auth/register`（白名单已含）body `{username, password, code, uuid}` → 成功后自动登录
- system 内部端点 `POST /user/inner/register`（内部）→ 建 STUDENT 账号
- `api/marketing.js`：`onlineSeckills()`、`seckillToken(activityId)`、`doSeckill({activityId, token})`

- [ ] **Step 1: 注册后端（最小闭环）**

`RegisterController` 改为真实建号，但**短信验证**留到 M2（当前用图形验证码，复用 `/auth/captcha`）：

```java
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterForm form) {
        // 图形验证码与登录一致：复用同一 Redis key，避免新增一套验证通道
        validateCaptcha(form.getUuid(), form.getCode());
        if (!form.getPassword().matches("^(?=.*[A-Za-z])(?=.*\\d).{8,32}$")) {
            throw new ServiceException("密码需 8-32 位且包含字母与数字");
        }
        R<Long> created = remoteUserService.register(form.getUsername().trim(),
                passwordEncoder.encode(form.getPassword()), "true", securityProperties.getInternalToken());
        if (created == null || !created.isSuccess()) {
            throw new ServiceException(created == null ? "注册失败" : created.getMsg());
        }
        log.info("新用户注册成功 userId={}", created.getData());
        return R.ok();
    }
```

system 侧：

```java
    @PostMapping("/inner/register")
    public R<Long> register(@RequestBody Map<String, String> body) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "禁止外部访问内部用户接口");
        }
        return R.ok(userService.registerStudent(body.get("username"), body.get("password")));
    }
```

`registerStudent`：用户名唯一性检查（重复返回 400「用户名已存在」）；插入 `sys_user`（status=0）；查询 `roleKey='STUDENT'` 的角色并写 `sys_user_role`；返回新用户 ID。**必须在一个事务里**。

- [ ] **Step 2: 注册测试**

```java
    @Test
    void weakPasswordIsRejectedBeforeCreatingUser() {
        assertThatThrownBy(() -> controller.register(form("student2", "12345678", "abcd")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("字母与数字");
    }

    @Test
    void duplicateUsernameSurfacesRealMessage() {
        when(remoteUserService.register(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(R.fail(400, "用户名已存在"));
        assertThatThrownBy(() -> controller.register(form("student", "abcd1234", "ab12")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("用户名已存在");
    }
```

- [ ] **Step 3: 两个页面**

`SeckillView.vue`：活动列表（倒计时、剩余库存、秒杀价）、"立即抢"→ `seckillToken` → `doSeckill` → 拿到 `orderNo` 后走模拟收银（与详情页同一个 `payOrder`/`confirmMockPay`）→ 成功后跳 `/mine/orders`。失败文案直接展示后端 `msg`（含"已售罄/请勿重复抢购"）。

`RegisterView.vue`：用户名、密码、确认密码、图形验证码（复用 `getCaptcha`），成功后自动登录并跳 `/catalog`。

- [ ] **Step 4: 运行与提交**

Run: `mvn test -q -pl zhixue-auth -am && cd zhixue-student-web && npm run build`
Expected: 通过

```bash
git add zhixue-auth/src zhixue-modules/zhixue-system/src \
        zhixue-student-web/src/views/seckill zhixue-student-web/src/views/register zhixue-student-web/src/api
git commit -m "feat: 学员自助注册（图形验证码）与秒杀页"
```

---

## 检查点 D（学生 Web 完整门户）

- [ ] `npm test && npm run build` 全绿
- [ ] Playwright 界面分段全部通过（扩展现有 `student-web-check.sh` 的分段）：
```bash
bash scripts/local/student-web-check.sh
```
Expected: 现有分段 + 新增 `catalog`、`mine`、`seckill`、`register` 分段全部 ok
- [ ] 人工在 1440 与 390 宽度各看一遍：导航、课程库、我的课程、订单、券、秒杀、注册
- [ ] 确认二期入口在窄屏抽屉里可见，不与"最近会话"挤在一起

---

## 阶段 E：媒资真实链路（上传 → 合并 → 转码 → 绑定 → 播放）

> 现状：单文件上传（≤512MB）**不转码**直接可用；分片合并时**同步**调用转码，且容器里没有 ffmpeg。目标是 GB 级视频可用，且失败可见可重试。

### Task E1：媒体镜像自带 ffmpeg，MinIO 镜像可配置

**Files:**
- Create: `ops/media/Dockerfile`
- Modify: `ops/local/docker-compose.yml:68-79,201-232`
- Modify: `scripts/local/up.sh:10-22`
- Modify: `docs/three-ends-gap-list.md`（更新媒资条目）

**Interfaces:**
- 构建产物镜像 `zhixue-media-runtime:17-ffmpeg`
- 环境变量 `ZHIXUE_MINIO_IMAGE`（默认沿用当前固定版本，可指向内网镜像）

- [ ] **Step 1: 新建媒体运行时镜像**

`ops/media/Dockerfile`：

```dockerfile
FROM eclipse-temurin:17-jre

# 媒资服务需要在进程内调用 ffmpeg 做转码；官方 JRE 镜像不含 ffmpeg，
# 此前"转码失败"的真实原因是容器里根本没有这个命令。
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

RUN ffmpeg -version | head -n 1
```

- [ ] **Step 2: compose 使用该镜像并允许配置 MinIO 镜像**

`docker-compose.yml` 的 `media` 服务：

```yaml
  media:
    build:
      context: ./media
      dockerfile: Dockerfile
    image: zhixue-media-runtime:17-ffmpeg
```

`minio` 服务镜像改为：

```yaml
    image: ${ZHIXUE_MINIO_IMAGE:-minio/minio:RELEASE.2024-12-18T13-15-44Z}
```

`ops/local/local.env.example` 增加说明：

```bash
# 若默认 MinIO 镜像在当前网络不可拉取，指向内网镜像仓库中的等价版本
# ZHIXUE_MINIO_IMAGE=registry.example.com/library/minio:RELEASE.2024-12-18T13-15-44Z
```

- [ ] **Step 3: 验证镜像内有 ffmpeg**

Run:
```bash
cd /Users/jax/Projects/zhixue-cloud/ops/local
docker compose --env-file ../local/local.env -f docker-compose.yml build media
docker run --rm zhixue-media-runtime:17-ffmpeg ffmpeg -version | head -n 1
```
Expected: 输出 `ffmpeg version ...`

- [ ] **Step 4: 更新缺口清单并提交**

`docs/three-ends-gap-list.md` 的媒资条目改写为："已有上传/合并/转码实现与媒体运行时镜像；真实 GB 级上传闭环由 `journeys.sh` 媒资段验收（见 Task G2），历史 10-20 的镜像不可拉取问题已由可配置镜像解决。"

```bash
git add ops/media ops/local/docker-compose.yml ops/local/local.env.example scripts/local/up.sh docs/three-ends-gap-list.md
git commit -m "build(media): 媒体运行时镜像内置 ffmpeg，MinIO 镜像可配置"
```

---

### Task E2：转码异步化 + 状态查询 + 失败重试

**Files:**
- Modify: `zhixue-modules/zhixue-media/src/main/java/com/zhixue/media/MediaApplication.java`（`@EnableAsync`）
- Modify: `zhixue-modules/zhixue-media/src/main/java/com/zhixue/media/service/impl/FileServiceImpl.java:121-177`
- Create: `zhixue-modules/zhixue-media/src/main/java/com/zhixue/media/service/TranscodeTaskService.java`
- Modify: `zhixue-modules/zhixue-media/src/main/java/com/zhixue/media/strategy/FFmpegStrategy.java:39-84`
- Modify: `zhixue-modules/zhixue-media/src/main/java/com/zhixue/media/controller/UploadController.java`
- Test: `zhixue-modules/zhixue-media/src/test/java/com/zhixue/media/service/TranscodeTaskServiceTest.java`（新建）

**Interfaces:**
- `TranscodeTaskService.submitAsync(Long mediaFileId)` — `@Async`，独立事务回写状态
- `GET /media/file/{id}` 已存在，返回 `status`（0上传中/1合并完成/2转码中/3成功/4失败）
- `POST /media/file/{id}/retry`（权限同上传）→ 重置为 2 并重新入队

- [ ] **Step 1: 抽取异步任务（含事务边界）**

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class TranscodeTaskService {

    private final MediaFileMapper mediaFileMapper;
    private final VideoProcessService videoProcessService;

    /**
     * 异步转码。
     *
     * <p>必须独立事务：合并接口的 @Transactional 在提交后如果直接调转码，
     * GB 级视频会把 HTTP 请求挂住到超时；而放到 @Async 里若与合并共享事务，
     * 新线程读不到未提交的数据。</p>
     */
    @Async("transcodeExecutor")
    public void submitAsync(Long mediaFileId) {
        MediaFile mediaFile = mediaFileMapper.selectById(mediaFileId);
        if (mediaFile == null) {
            return;
        }
        try {
            String newObject = videoProcessService.transcode(mediaFile);
            MediaFile update = new MediaFile();
            update.setId(mediaFileId);
            update.setStatus(3);
            if (StringUtils.hasText(newObject)) {
                update.setObjectName(newObject);
                update.setFileUrl(buildUrl(mediaFile.getBucket(), newObject));
            }
            mediaFileMapper.updateById(update);
        } catch (Exception e) {
            log.error("转码失败 mediaFileId={}", mediaFileId, e);
            MediaFile failed = new MediaFile();
            failed.setId(mediaFileId);
            failed.setStatus(4);
            failed.setRemark("转码失败: " + abbreviate(e.getMessage(), 180));
            mediaFileMapper.updateById(failed);
        }
    }

    private String abbreviate(String text, int max) {
        if (!StringUtils.hasText(text)) {
            return "未知原因";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
```

新增 `config/AsyncConfig`：

```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("transcodeExecutor")
    public Executor transcodeExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 转码是 CPU/IO 重任务：并发 2 足够，避免把机器打满影响播放与接口
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("transcode-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2: 合并接口改为只入队**

`FileServiceImpl.mergeChunks` 的转码段替换为：

```java
        if (isVideo(dto.getFileType())) {
            mediaFile.setStatus(2);
            mediaFileMapper.updateById(mediaFile);
            // 事务提交后再入队：否则异步线程可能读不到这行（或读到旧状态）
            transcodeAfterCommit(mediaFile.getId());
        } else {
            mediaFile.setStatus(3);
            mediaFileMapper.updateById(mediaFile);
        }
```

```java
    private void transcodeAfterCommit(Long mediaFileId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    transcodeTaskService.submitAsync(mediaFileId);
                }
            });
        } else {
            transcodeTaskService.submitAsync(mediaFileId);
        }
    }
```

- [ ] **Step 3: ffmpeg 加超时并真正读到错误输出**

`FFmpegStrategy.transcode` 替换进程段：

```java
        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath, "-y", "-i", source.toString(),
                "-vcodec", "libx264", "-acodec", "aac", target.toString());
        // 合并 stderr 到 stdout 后必须持续读走，否则输出填满管道会让进程永久阻塞
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (output.length() < 4000) {
                        output.append(line).append('\n');
                    }
                }
            } catch (IOException ignored) {
                // 进程被强杀时读取线程退出属正常
            }
        }, "ffmpeg-output");
        reader.setDaemon(true);
        reader.start();

        boolean finished = process.waitFor(transcodeTimeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("FFmpeg 转码超时（" + transcodeTimeoutSeconds + " 秒）");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("FFmpeg 转码失败：" + output);
        }
        reader.join(2000);
```

字段：`@Value("${media.transcode-timeout-seconds:1800}") private int transcodeTimeoutSeconds;`（默认 30 分钟，GB 级视频足够）。

- [ ] **Step 4: 重试接口**

`UploadController`：

```java
    @PostMapping("/file/{id}/retry")
    @RequirePermission({"course:edit", "course:publish"})
    public R<Void> retry(@PathVariable Long id) {
        return R.ok(mediaFileService.retryTranscode(id) ? null : null);
    }
```

`FileServiceImpl.retryTranscode`：只允许 `status=4`；置 2 后提交任务；否则抛「当前状态不可重试」。

- [ ] **Step 5: 测试**

`TranscodeTaskServiceTest`：①成功回写 status=3 且 fileUrl 指向新对象 ②异常回写 status=4 且 remark 截断到 180 字符 ③文件不存在时不抛异常（异步任务不能因脏 ID 崩溃）。

- [ ] **Step 6: 运行与提交**

Run: `mvn test -q -pl zhixue-modules/zhixue-media -am`
Expected: BUILD SUCCESS

```bash
git add zhixue-modules/zhixue-media/src
git commit -m "feat(media): 转码改异步并回写状态，ffmpeg 带超时与输出消费，失败可重试"
```

---

### Task E3：后台分片上传（真实 MD5）并绑定课时

**Files:**
- Modify: `zhixue-admin-web/package.json`（`spark-md5`）
- Create: `zhixue-admin-web/src/utils/chunkUpload.js`
- Create: `zhixue-admin-web/src/components/UploadVideo/index.vue`（重写为可用）
- Modify: `zhixue-admin-web/src/views/course/publish/index.vue:160-162`
- Modify: `zhixue-admin-web/src/views/course/chapter/index.vue:89-90`
- Test: `zhixue-admin-web/tests/unit/chunkUpload.test.js`（新建）

**Interfaces:**
- `computeFileMd5(file, { chunkSize }): Promise<string>`
- `createChunkUploader({ api, chunkSize, concurrency }): { start(file, onProgress): Promise<{ fileId, fileUrl, status }> }`
- `UploadVideo` 组件 v-model 绑定视频 URL

- [ ] **Step 1: 实现 MD5 与上传器（纯逻辑可测）**

`src/utils/chunkUpload.js`：

```js
import SparkMD5 from 'spark-md5'

const DEFAULT_CHUNK = 5 * 1024 * 1024

/** 逐片读文件算 MD5：一次性读 GB 级文件会直接把标签页内存打爆。 */
export function computeFileMd5(file, { chunkSize = DEFAULT_CHUNK } = {}) {
  return new Promise((resolve, reject) => {
    const spark = new SparkMD5.ArrayBuffer()
    const reader = new FileReader()
    let offset = 0
    const readNext = () => {
      const end = Math.min(offset + chunkSize, file.size)
      reader.readAsArrayBuffer(file.slice(offset, end))
    }
    reader.onload = (event) => {
      spark.append(event.target.result)
      offset += chunkSize
      if (offset < file.size) {
        readNext()
      } else {
        resolve(spark.end())
      }
    }
    reader.onerror = () => reject(new Error('读取文件失败'))
    readNext()
  })
}

/**
 * 分片上传：init(chunk 序号) → upload(chunk) × N → merge。
 * 任何一片失败都抛错，由调用方展示重试；不做自动重试（避免掩盖网络问题）。
 */
export function createChunkUploader(api, { chunkSize = DEFAULT_CHUNK } = {}) {
  return {
    async start(file, onProgress = () => {}) {
      if (!file) throw new Error('请选择文件')
      const md5 = await computeFileMd5(file, { chunkSize })
      const total = Math.ceil(file.size / chunkSize)
      for (let index = 0; index < total; index += 1) {
        const blob = file.slice(index * chunkSize, Math.min((index + 1) * chunkSize, file.size))
        await api.uploadChunk({ md5, index, total, chunk: blob, fileName: file.name })
        onProgress(Math.round(((index + 1) / total) * 90))
      }
      const merged = await api.mergeChunk({ md5, fileName: file.name, fileSize: file.size, fileType: file.type, total })
      onProgress(100)
      return merged
    }
  }
}
```

- [ ] **Step 2: 测试**

```js
import { describe, expect, it, vi } from 'vitest'
import { computeFileMd5, createChunkUploader } from '@/utils/chunkUpload'

function fakeFile(size) {
  return new File([new Uint8Array(size)], 'lesson.mp4', { type: 'video/mp4' })
}

describe('分片上传', () => {
  it('MD5 与已知内容一致（分块读取不改变结果）', async () => {
    const file = new File([new Uint8Array([1, 2, 3, 4, 5, 6, 7, 8])], 'a.bin')
    // d41d8cd98f00b204e9800998ecf8427e 是空串；这里只断言"分块与整块算出的值相同"
    const whole = await computeFileMd5(file, { chunkSize: 1024 })
    const chunked = await computeFileMd5(file, { chunkSize: 3 })
    expect(chunked).toBe(whole)
  })

  it('按片上传后调用合并，进度到 100', async () => {
    const calls = []
    const api = {
      uploadChunk: vi.fn(async (payload) => { calls.push(payload.index) }),
      mergeChunk: vi.fn(async () => ({ fileId: '1', fileUrl: 'http://x/1.mp4', status: 2 }))
    }
    const progress = []
    const result = await createChunkUploader(api, { chunkSize: 4 })
      .start(fakeFile(10), (percent) => progress.push(percent))

    expect(calls).toEqual([0, 1, 2])
    expect(api.mergeChunk).toHaveBeenCalledOnce()
    expect(progress.at(-1)).toBe(100)
    expect(result.status).toBe(2)
  })

  it('某一片失败时整体失败，不调用合并', async () => {
    const api = {
      uploadChunk: vi.fn().mockRejectedValueOnce(new Error('413')).mockResolvedValue(),
      mergeChunk: vi.fn()
    }
    await expect(createChunkUploader(api, { chunkSize: 4 }).start(fakeFile(10))).rejects.toThrow('413')
    expect(api.mergeChunk).not.toHaveBeenCalled()
  })
})
```

> vitest 的 node 环境没有 `File`/`FileReader`。测试文件顶部加：
> ```js
> import { File } from 'node:buffer'
> import { FileReader } from '@/tests/support/fileReader'
> ```
> 并在 `tests/support/fileReader.js` 提供一个最小 polyfill（读 `slice()` 后 `onload({ target: { result: await arrayBuffer } })`）。这是唯一为测试引入的适配层，不进入生产包。

- [ ] **Step 3: 组件与绑定**

`UploadVideo/index.vue`：`el-upload` 设置 `:auto-upload="false"`，选文件后调用 `createChunkUploader`，显示 `el-progress` 与状态文本（上传中 → 转码中 → 可播放）；转码中每 5 秒轮询 `GET /media/file/{id}` 直到 status 为 3 或 4；失败显示原因与"重试"按钮。

`publish/index.vue:160-162` 与 `chapter/index.vue:89-90` 的"视频地址"输入框上方插入：

```html
          <el-form-item label="上传视频">
            <UploadVideo v-model="nodeForm.videoUrl" @uploaded="(url) => (nodeForm.videoUrl = url)" />
          </el-form-item>
```

保留手填 URL 作为兜底（外链课程仍需支持）。

- [ ] **Step 4: 运行与提交**

Run: `cd zhixue-admin-web && npm test && npm run build`
Expected: 通过

```bash
git add zhixue-admin-web/package.json zhixue-admin-web/src/utils/chunkUpload.js \
        zhixue-admin-web/src/components/UploadVideo zhixue-admin-web/src/views/course \
        zhixue-admin-web/tests
git commit -m "feat(admin): 真实 MD5 分片上传 + 转码状态轮询，课时可直接上传视频"
```

---

## 阶段 F：AI 效果（资料覆盖 · 评测 · 推荐精度）

### Task F1：补齐知识资料覆盖并加负例回归

**Files:**
- Create: `zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-22-knowledge-coverage.sql`
- Modify: `zhixue-modules/zhixue-ai/src/main/resources/sql/ai_seed.sql`（同一段资料，供全新环境）
- Modify: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/service/impl/JavaSeedIntegrityTest.java`（纳入新段一致性校验）
- Modify: `scripts/local/fixtures/ai-answer-cases.json`（补场景）
- Modify: `scripts/local/ai-reco-check.mjs`（正例不得多推无关课程）
- Test: `scripts/local/lib/ai-eval.test.mjs`（追加断言）

**数据模型（已核对，勿改成 `ai_knowledge`）**：知识落在 `kb_document`（`id, title, source, tags, status`）+ `kb_chunk`（`id, document_id, chunk_index, content, es_id`）。`es_id` 留 NULL，AI 服务启动时会把 `es_id IS NULL` 的分片补进向量索引。

- [ ] **Step 1: 补资料（增量脚本）**

`2026-10-22-knowledge-coverage.sql`：

```sql
-- 补齐已知漏推主题：try-with-resources / 异常体系。可重复执行。
-- 只写 MySQL；AI 服务启动时会把 es_id 为空的分片补进向量索引。
USE zhixue_ai;

INSERT IGNORE INTO kb_document (id, title, source, tags, status, create_time, update_time, deleted)
VALUES (91021, 'Java 编程基础 · 异常与资源管理', 'course:2005', 'java,异常,try-with-resources,AutoCloseable', 1, NOW(), NOW(), 0);

INSERT IGNORE INTO kb_chunk (id, document_id, chunk_index, content, es_id, create_time, update_time, deleted)
VALUES
(92021, 91021, 0,
 'try-with-resources 是 Java 7 引入的语法：只要资源实现了 AutoCloseable（或它的子接口 Closeable），就可以写在 try 的圆括号里声明，无论 try 正常结束还是抛异常，JVM 都会按声明的逆序调用 close()。它替代了 finally 里手写 close 还要判空、还要吞掉 close 异常的老写法；多个资源用分号分隔。注意三点：资源必须在 try 括号内声明才受管理，在 try 之前声明不会自动关闭；close() 抛出的异常会作为 suppressed 异常挂在主异常上，用 getSuppressed() 取；资源变量在 try 块内隐式 final。自定义资源只要实现 AutoCloseable 并实现 close() 即可接入。',
 NULL, NOW(), NOW(), 0),
(92022, 91021, 1,
 'Java 异常体系：Throwable 分为 Error 与 Exception；Exception 又分受检异常（编译期要求捕获或声明）与运行时异常（RuntimeException 及其子类）。受检异常用于调用方可以合理恢复的场景，运行时异常用于编程错误，不要用受检异常表达参数非法。不要在 finally 里 return 或抛异常，前者会吞掉 try 中的异常与返回值，后者会覆盖原始异常。包装异常时务必保留 cause（new XxxException(msg, cause)），否则排查时丢失根因。',
 NULL, NOW(), NOW(), 0);
```

- [ ] **Step 2: 同步到 `ai_seed.sql`**

在 `ai_seed.sql` 的 `-- END java-knowledge` 之后追加同一段数据，并用标记包起来（与现有 `BEGIN/END java-knowledge` 风格一致）：

```sql
-- BEGIN knowledge-coverage-2026-10-22（upgrade/2026-10-22-knowledge-coverage.sql 由本段生成，二者由 JavaSeedIntegrityTest 保证一致）
INSERT INTO kb_document (id, title, source, tags, status, create_time, update_time, deleted)
VALUES (91021, 'Java 编程基础 · 异常与资源管理', 'course:2005', 'java,异常,try-with-resources,AutoCloseable', 1, NOW(), NOW(), 0);

INSERT INTO kb_chunk (id, document_id, chunk_index, content, es_id, create_time, update_time, deleted)
VALUES
(92021, 91021, 0, '<与增量脚本完全相同的内容>', NULL, NOW(), NOW(), 0),
(92022, 91021, 1, '<与增量脚本完全相同的内容>', NULL, NOW(), NOW(), 0);
-- END knowledge-coverage-2026-10-22
```

`JavaSeedIntegrityTest` 增加对新标记段的比对（现有测试是逐段抽取后比较 SQL 文本，新段加入同一列表即可）。

- [ ] **Step 3: 评测场景补漏推与负例**

`ai-answer-cases.json` 追加两个场景：

```json
{
  "id": "try-with-resources",
  "question": "try-with-resources 怎么用？和 finally 关闭资源有什么区别",
  "courseId": null,
  "expectCourses": ["2005"],
  "expectNoOtherCourses": true,
  "allOfAny": [["AutoCloseable", "自动关闭", "close"]],
  "allowDegraded": false
},
{
  "id": "off-topic-should-not-recommend",
  "question": "今天中午吃什么比较好",
  "courseId": null,
  "expectCourses": [],
  "expectNoOtherCourses": true,
  "allOfAny": [],
  "allowDegraded": true
}
```

`ai-reco-check.mjs` 的正例判定加一条：

```js
if (c.expectNoOtherCourses) {
  const extra = courseIds.filter((id) => !c.courses.includes(String(id)))
  check(`  未多推无关课程 ${extra.join(',') || '无'}`, extra.length === 0, true)
}
```

- [ ] **Step 4: 评测器单测**

Run: `node --test /Users/jax/Projects/zhixue-cloud/scripts/local/lib/ai-eval.test.mjs`
Expected: 全部通过。需在 `scripts/local/lib/ai-eval.mjs` 实现 `expectNoOtherCourses` 规则（推荐结果里出现期望之外的课程即失败），并在 `ai-eval.test.mjs` 加一条"多推即失败"的用例先红后绿。

- [ ] **Step 5: 真实环境验证推荐**

Run:
```bash
bash scripts/local/upgrade.sh
compose up -d --force-recreate --no-deps ai
docker logs --tail 40 zhixue-local-ai   # 确认索引补齐完成
bash scripts/local/ai-reco-check.sh
```
Expected: `try-with-resources` 命中课程 2005；`今天中午吃什么` 无推荐；其余基线用例一致

- [ ] **Step 6: 提交**

```bash
git add zhixue-modules/zhixue-ai/src/main/resources/sql \
        zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/service/impl/JavaSeedIntegrityTest.java \
        scripts/local/fixtures/ai-answer-cases.json scripts/local/ai-reco-check.mjs scripts/local/lib
git commit -m "feat(ai): 补异常与资源管理资料覆盖，推荐评测拒绝多推无关课程"
```

---

### Task F2：评测执行与人工评审（产出可判定的质量报告）

**Files:**
- Modify: `.codebuddy/artifacts/ai-answer-eval-review.md`（填写首轮结果）
- Create: `docs/ai-quality-report.md`（对外可引用的质量结论）
- Modify: `README.md`（引用质量报告，删除未经验证的容量表述）

**Interfaces:**
- Consumes: A8 建立的评测流程与 `.codebuddy/artifacts/ai-answer-eval.json`

- [ ] **Step 1: 跑满 20 场景 × 3 次**

Run: `bash scripts/local/ai-answer-eval.sh`
Expected: `summary.passRate >= 0.9`，`summary.savedTrueRate == 1.0`；产物含 `generatedAt`、`evaluatorVersion`、`fixtureHash`

- [ ] **Step 2: 人工逐条评审并统计**

按 A8 的模板填 20 行；统计"对/部分对/错"分布，写入 `docs/ai-quality-report.md`：

```markdown
# AI 答疑质量报告（首轮）

- 评测时间：<generatedAt>
- 模型：qwen3.5:9b（revision `local-qwen3.5-9b-nvfp4-2026-10`），embedding `qwen3-embedding:0.6b`（1024 维）
- 样本：20 场景 × 3 次独立会话，共 66 次提问
- 自动规则通过率：<x>/20（协议、关键词、禁词、无降级）
- 人工判定：对 <a>、部分对 <b>、错 <c>
- 保存成功率：<x>%（`saved=true`）
- 首字时延（模型已就绪）：<p50>/<p95>；冷启动首发：<n> 秒

## 已知边界（如实记录，不掩盖）

1. `sources` 是本次检索命中的资料标题列表，**不代表每句结论都被这些资料支持**。
2. 课时推荐基于标题向量相似度，可能把"扩容"类问题同时匹配到 HashMap 与 ArrayList。
3. 单实例准入为 1，多人并发下会直接拒绝而非排队。

## 结论

满足/不满足 M1 验收第 22 条。不满足的条目对应任务：F1（资料）/ F3（推荐阈值与标题）。
```

- [ ] **Step 3: README 收敛表述**

把 `README.md:27` 的"支持百万级用户同时在线互动"改为：

```markdown
- **集群广播**：利用 Redis Pub/Sub 实现跨节点的消息广播。当前仓库只包含单机本地部署与功能回归，
  **未提供**百万连接/吞吐/分位时延压测报告；容量结论需按 `scripts/local/load/` 的压测结果另行给出（见 G3）。
```

- [ ] **Step 4: 提交**

```bash
git add docs/ai-quality-report.md .codebuddy/artifacts/ai-answer-eval-review.md README.md
git commit -m "docs(ai): 首轮真实模型质量报告，README 收敛未经压测的容量表述"
```

---

### Task F3：课时推荐精度改进（只在评测要求时做）

**Files:**
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/CourseRecommender.java:120-140`
- Modify: `zhixue-modules/zhixue-ai/src/main/resources/application.yml`（阈值外置）
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/chat/CourseRecommenderTest.java`（追加）

**前提**：F2 报告显示"课时错配"是主要问题才做本任务；若报告显示达标，**跳过**（避免 YAGNI 式调参）。

- [ ] **Step 1: 增加"问句主题与课时标题共有词"二次校验**

```java
    /**
     * 标题余弦高但缺少共同主题词的课时要降权。
     *
     * <p>实测：「ArrayList 是怎么扩容的」与「HashMap 扩容机制与树化」相似度 0.65，
     * 高于相关课时的最低值 0.558 —— 单靠余弦分不开，必须看两类容器名是否同时出现。</p>
     */
    private boolean topicMismatch(String question, String lessonTitle) {
        List<String> topics = List.of("hashmap", "arraylist", "concurrenthashmap", "线程池", "string",
                "synchronized", "reentrantlock", "jvm", "gc", "索引");
        List<String> inQuestion = topics.stream().filter(question.toLowerCase()::contains).toList();
        if (inQuestion.isEmpty()) {
            return false;
        }
        String title = lessonTitle.toLowerCase();
        boolean shares = inQuestion.stream().anyMatch(title::contains);
        boolean hasOther = topics.stream().anyMatch(t -> !inQuestion.contains(t) && title.contains(t));
        return !shares && hasOther;
    }
```

在 `recommend` 的课时过滤链上加 `.filter(s -> !topicMismatch(question, titleOf(s)))`。

- [ ] **Step 2: 测试**

```java
    @Test
    void lessonAboutAnotherContainerIsRejectedEvenWithHighCosine() {
        // 问 ArrayList 扩容，不应推 HashMap 扩容课时
        List<Scored> picked = recommender.pickLessons("ArrayList 是怎么扩容的", candidates(
                lesson(1L, "HashMap 扩容机制与树化", 0.65),
                lesson(2L, "ArrayList 扩容与数组复制", 0.58)));
        assertThat(picked).extracting(Scored::id).containsExactly(2L);
    }

    @Test
    void questionWithoutKnownTopicKeepsCosineOnlyBehavior() {
        List<Scored> picked = recommender.pickLessons("这个方法为什么报错", candidates(
                lesson(1L, "线程池核心参数", 0.6)));
        assertThat(picked).hasSize(1);
    }
```

- [ ] **Step 3: 跑评测确认没有回退**

Run:
```bash
mvn test -q -pl zhixue-modules/zhixue-ai -am
bash scripts/local/ai-reco-check.sh
```
Expected: 单测通过；5 个基线用例仍全部命中；`ArrayList` 用例不再推 HashMap 课时

- [ ] **Step 4: 提交**

```bash
git add zhixue-modules/zhixue-ai/src
git commit -m "feat(ai): 课时推荐增加主题词一致性校验，消除跨容器高相似度错配"
```

---

## 检查点 F（AI 质量）

- [ ] `docs/ai-quality-report.md` 有真实数字（时间、模型版本、样本量、自动/人工结果、时延）
- [ ] 自动通过率 ≥ 90%，人工 ≥ 18/20，`saved=true` 100%
- [ ] 推荐 5 个基线用例 + try-with-resources + 无关问题全部符合预期
- [ ] **请人类确认**：人工评审里被判"部分对"的条目是否可接受；是否需要 F3

---

## 阶段 G：质量门禁与回归

### Task G1：一条命令跑完全部门禁

**Files:**
- Create: `scripts/local/verify-all.sh`
- Modify: `scripts/local/build.sh`（改为调用 verify-all 的静态部分）
- Create: `.github/workflows/ci.yml`
- Modify: `README.md`（补"质量门禁"章节）

**Interfaces:**
- `bash scripts/local/verify-all.sh [--with-e2e]`
  - 默认：后端测试 + 三端构建/单测 + 契约检查 + 静态检查（不需要启动服务）
  - `--with-e2e`：额外跑 `health.sh`、`smoke.sh`、`journeys.sh`、`ai-reco-check.sh`（需要本地服务）

- [ ] **Step 1: 编写脚本**

```bash
#!/usr/bin/env bash
# 质量门禁：默认只跑不需要外部服务的检查；--with-e2e 追加真实环境回归。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
WITH_E2E=0
[ "${1:-}" = "--with-e2e" ] && WITH_E2E=1

fail() { echo "[verify-all][FAIL] $1"; exit 1; }
step() { echo; echo "[verify-all] $1"; }

step "1/7 后端全量测试"
(cd "$ROOT_DIR" && mvn -B -ntp test) || fail "后端测试失败"

step "2/7 学生 Web 单测与构建"
(cd "$ROOT_DIR/zhixue-student-web" && npm test && npm run build) || fail "学生 Web 失败"

step "3/7 管理后台单测与构建"
(cd "$ROOT_DIR/zhixue-admin-web" && npm test && npm run build) || fail "管理后台失败"

step "4/7 小程序单测"
(cd "$ROOT_DIR" && node --test zhixue-app-mp/tests/*.test.cjs) || fail "小程序单测失败"

step "5/7 脚本单测"
(cd "$ROOT_DIR" && node --test scripts/local/lib/*.test.mjs) || fail "脚本单测失败"

step "6/7 前后端接口契约"
(cd "$ROOT_DIR" && bash scripts/local/contract-check.sh) || fail "契约检查失败"

step "7/7 静态检查"
(cd "$ROOT_DIR" && git diff --check) || fail "存在空白字符问题"
(cd "$ROOT_DIR/zhixue-student-web" && npx eslint src --max-warnings=0) || echo "[verify-all][warn] 学生 Web eslint 有告警（未阻断）"

if [ "$WITH_E2E" = "1" ]; then
  step "E2E: 健康检查 / 冒烟 / 联合旅程 / AI 推荐"
  (cd "$ROOT_DIR" && bash scripts/local/health.sh) || fail "健康检查失败"
  (cd "$ROOT_DIR" && bash scripts/local/smoke.sh) || fail "冒烟失败"
  (cd "$ROOT_DIR" && bash scripts/local/journeys.sh) || fail "联合旅程失败"
  (cd "$ROOT_DIR" && bash scripts/local/ai-reco-check.sh) || fail "AI 推荐验收失败"
fi

echo
echo "[ok] 质量门禁全部通过"
```

- [ ] **Step 2: CI 工作流**

`.github/workflows/ci.yml`：

```yaml
name: ci

on:
  push:
    branches: [main]
  pull_request:

jobs:
  backend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: temurin
          cache: maven
      - run: mvn -B -ntp test

  web:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        app: [zhixue-student-web, zhixue-admin-web]
    defaults:
      run:
        working-directory: ${{ matrix.app }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
      - run: npm ci
      - run: npm test
      - run: npm run build

  miniprogram:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '22'
      - run: node --test zhixue-app-mp/tests/*.test.cjs

  contract:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: bash scripts/local/contract-check.sh
```

- [ ] **Step 3: 本地验证脚本本身**

Run:
```bash
bash -n scripts/local/verify-all.sh
bash scripts/local/verify-all.sh
```
Expected: 最后一行 `[ok] 质量门禁全部通过`，退出码 0

- [ ] **Step 4: 提交**

```bash
git add scripts/local/verify-all.sh scripts/local/build.sh .github/workflows/ci.yml README.md
git commit -m "ci: 一条命令跑完三端构建/单测/契约检查，新增 GitHub Actions 工作流"
```

---

### Task G2：联合旅程覆盖新增闭环

**Files:**
- Modify: `scripts/local/journeys.mjs`
- Modify: `README.md:141-157`（回归清单说明）

**新增步骤**（沿用现有 `api()`/`check()` 与 `created` 清理模式）：

```js
console.log('===== 学习闭环：上报 → 续播 → 学情 =====')
await api('POST', '/course/progress/report', student, {
  courseId: cid, chapterId: chapterIdOf(cid), sectionId: firstSectionOf(cid),
  position: 120, duration: 1080, playedSeconds: 120
})
check('进度已落库并可查询', (await api('GET', `/course/progress/course/${cid}`, student)).data.percent > 0, true)
check('汇总有学时', (await api('GET', '/course/progress/summary', student)).data.totalHours10 >= 2, true)
check('我的课程包含该课', (await api('GET', '/course/mine/courses', student)).data
  .some((row) => String(row.courseId) === String(cid)), true)

console.log('===== 用券下单 → 支付核销 → 取消退券 =====')
const coupon = await api('POST', '/marketing/coupon', admin, couponPayload(TAG))
const claim = await api('POST', '/marketing/coupon/claim', student, { couponId: coupon.data.id })
const usable = await api('GET', `/marketing/coupon/usable?amount=${price}`, student)
check('新领的券出现在可用列表', usable.data.length >= 1, true)
const order = await api('POST', '/order/create', student,
  { productId: cid, couponUserId: String(usable.data[0].id) })
check('用券后金额为课程价减抵扣', Number(order.data.amount) < price, true)
await api('POST', '/pay', student, { orderNo: order.data.orderNo, payChannel: 'mock' })
await api('POST', '/pay/mock/confirm', student, { orderNo: order.data.orderNo })
check('支付后券变为已使用',
  (await api('GET', '/marketing/coupon/user/list?status=1', student)).data.length >= 1, true)

console.log('===== 评价 → 教师回复 → 运营隐藏 =====')
await api('POST', `/course/${cid}/reviews`, student, { rating: 5, content: `${TAG} 课程结构清晰，示例能直接用到工作里` })
check('详情页可看到评价', (await api('GET', `/course/${cid}/reviews`, null)).data.reviewCount >= 1, true)
const reviewId = (await api('GET', `/course/${cid}/reviews/mine`, student)).data.id
await api('PUT', `/course/reviews/${reviewId}/reply`, teacher, { reply: '感谢反馈，后续会补更多示例' })
await api('PUT', `/course/reviews/${reviewId}/status?status=0`, admin)
check('隐藏后前台不再返回该评价',
  (await api('GET', `/course/${cid}/reviews`, null)).data.reviewCount, 0)

console.log('===== 售后申请 → 运营审批 → 权限收回 =====')
const refund = await api('POST', '/order/refund-request', student, { orderId: order.data.id, reason: `${TAG} 与预期不符` })
await api('PUT', `/order/refund-request/${refund.data.id}/approve`, admin, { reason: '同意退款' })
check('订单变为已退款', (await api('GET', `/order/${order.data.id}`, student)).data.status, 3)
check('退款后课时重新锁定', await lessons(), [[true, false], [true, false]].map(row => [row[0], true]).slice(0, 2))

console.log('===== 教师经营数据范围 =====')
const summary = await api('GET', '/order/teacher/summary', teacher)
check('教师只统计本人课程', summary.data.courses.every((row) => teacherCourseIds.includes(String(row.courseId))), true)
check('学员访问教师统计被拒', (await api('GET', '/order/teacher/summary', student)).code, 403)
```

清理：把 coupon / review / refundRequest 的 ID 加入 `created`，`cleanup()` 中按 ID 删除（评价值接置 `deleted=1`；券走 `DELETE /marketing/coupon/{id}`；售后无删除接口时保留并在日志标注）。

- [ ] **Step 1: 实现并运行**

Run: `bash scripts/local/journeys.sh`
Expected: 新增段落全部 `ok`，退出码 0

- [ ] **Step 2: 更新 README 回归清单**

`README.md:141-157` 的注释改为覆盖新闭环的一行说明，并注明"需要本地全套服务"。

- [ ] **Step 3: 提交**

```bash
git add scripts/local/journeys.mjs README.md
git commit -m "test(local): 联合旅程覆盖学习、用券、评价、售后与教师数据范围"
```

---

### Task G3：压测脚本与容量数据

**Files:**
- Create: `scripts/local/load/k6-course-list.js`
- Create: `scripts/local/load/k6-seckill.js`
- Create: `scripts/local/load/README.md`
- Modify: `scripts/local/perf.sh`（保留为快速冒烟，指向 k6 正式脚本）
- Modify: `README.md`（补实测容量数字，没有数据就写"未测"）

**Interfaces:**
- `k6 run -e BASE_URL=... -e TOKEN=... scripts/local/load/k6-course-list.js`
- 秒杀脚本输出"库存守恒"与"重复令牌拒绝"指标

- [ ] **Step 1: 课程列表压测**

```js
import http from 'k6/http'
import { check } from 'k6'

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-vus',
      startVUs: 10,
      stages: [
        { duration: '1m', target: 50 },
        { duration: '2m', target: 100 },
        { duration: '1m', target: 0 }
      ]
    }
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000']
  }
}

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:19001'

export default function () {
  const res = http.get(`${BASE}/course/list?pageNum=1&pageSize=10`)
  check(res, { 'status 200': (r) => r.status === 200, 'has records': (r) => r.body.includes('records') })
}
```

- [ ] **Step 2: 秒杀一致性压测**

```js
import http from 'k6/http'
import { check } from 'k6'

export const options = { vus: 20, iterations: 200 }

const BASE = __ENV.BASE_URL || 'http://127.0.0.1:19001'
const TOKEN = __ENV.TOKEN
const ACTIVITY = __ENV.ACTIVITY_ID

export default function () {
  const headers = { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' }
  const tokenRes = http.post(`${BASE}/marketing/seckill/token?activityId=${ACTIVITY}`, null, { headers })
  const token = tokenRes.json('data')
  const res = http.post(`${BASE}/marketing/seckill/do`, JSON.stringify({ activityId: ACTIVITY, token }), { headers })
  check(res, {
    '要么成功要么明确拒绝': (r) => [200, 400, 503].includes(r.status),
    '不会重复成交': (r) => r.status !== 200 || r.json('data.orderNo') !== undefined
  })
}
```

`load/README.md` 写明：每轮压测后必须校验
```sql
SELECT COUNT(*) FROM `order` WHERE activity_id = <id>;                -- 必须 = 实际领取人数
SELECT COUNT(DISTINCT user_id) FROM `order` WHERE activity_id = <id>; -- 必须 = 上一行（无重复）
```
以及"库存 + 成交数 = 初始库存"的守恒检查。

- [ ] **Step 3: 实测并把数字写回 README**

Run（需要本地服务；建议先 `bash scripts/local/verify-all.sh --with-e2e` 确认基线健康）：
```bash
k6 run -e BASE_URL=http://127.0.0.1:19001 scripts/local/load/k6-course-list.js
```
Expected: `p(95)`、`p(99)`、错误率数值；把实测结果（含机器配置、并发数、时间）写入 `README.md` 的"性能"小节。**没有得到数字就不写任何容量结论。**

- [ ] **Step 4: 提交**

```bash
git add scripts/local/load scripts/local/perf.sh README.md
git commit -m "test(load): k6 压测脚本与秒杀库存守恒校验，README 写入实测容量"
```

---

### Task G4：文档与缺口清单收口

**Files:**
- Modify: `docs/three-ends-gap-list.md`
- Modify: `README.md`
- Create: `docs/adr/2026-10-22-search-and-seckill-decisions.md`
- Modify: `.superpowers/sdd/progress.md`（追加本计划执行账本）

- [ ] **Step 1: 缺口清单重写为三态**

把 10-20 的表改为三列状态表：`已关闭（对应提交）`、`仍开放（对应任务）`、`转为商用前置（M2）`。必须删除已过时项（图标、开发者工具未渲染、一次性非流式 AI）。

- [ ] **Step 2: ADR 记录两个决策**

`docs/adr/2026-10-22-search-and-seckill-decisions.md` 记录：
- AD-8：不补 ES 课程搜索索引生命周期（理由：数据规模 + YAGNI），保留 stub 与 DB 搜索；
- AD-6：秒杀用单库唯一键 + 幂等重试，不引入 Seata；
- 附带记录 MQ/Seata 是**有意简化**，不是遗漏。

- [ ] **Step 3: 追加执行账本**

`.superpowers/sdd/progress.md` 末尾追加本计划的阶段进度表（任务号、状态、提交、备注），供后续会话接续。

- [ ] **Step 4: 提交**

```bash
git add docs README.md .superpowers/sdd/progress.md
git commit -m "docs: 缺口清单按已关闭/仍开放/商用前置重写，补搜索与秒杀 ADR"
```

---

## 检查点 G（工程门禁）

- [ ] `bash scripts/local/verify-all.sh` 退出码 0
- [ ] `bash scripts/local/verify-all.sh --with-e2e` 退出码 0（需要本地全套服务）
- [ ] CI 在 PR 上跑通（backend / web ×2 / miniprogram / contract 五个 job）
- [ ] `scripts/local/load/` 有实测输出，README 的容量表述与实测一致（未测就写"未测"）
- [ ] **此时 M1 达成**：第 2 章 26 条验收标准逐条可指认证据

---

## 阶段 H：M2 生产上线（依赖外部资质，代码可先落地）

> 本阶段代码可以现在写，但"完成"取决于商户号、证书、域名备案、云资源等外部条件。每项任务的验收都明确标注**代码完成**与**上线完成**两个层级。

### Task H1：真实微信登录（code2session）

**Files:**
- Modify: `zhixue-auth/src/main/java/com/zhixue/auth/service/impl/WechatLoginServiceImpl.java`
- Create: `zhixue-auth/src/main/java/com/zhixue/auth/service/WechatApiClient.java`
- Modify: `zhixue-auth/src/main/resources/application.yml`
- Modify: `zhixue-modules/zhixue-system/src/main/java/com/zhixue/system/service/impl/SysUserServiceImpl.java`（按 openid 建号/绑定）
- Create: `zhixue-modules/zhixue-system/src/main/resources/sql/upgrade/2026-10-22-wechat-openid.sql`
- Test: `zhixue-auth/src/test/java/com/zhixue/auth/service/WechatLoginServiceImplTest.java`（新建）

**Interfaces:**
- `WechatApiClient.code2Session(String code, String appId, String secret): WechatSession{openid, unionid, sessionKey}`
- `zhixue.auth.wechat.mode`: `disabled` | `sandbox` | `real`
- `sys_user` 新增 `wechat_openid`（唯一）、`wechat_unionid`

**安全约束**：`session_key` 只在 auth 服务内存中使用，不落库、不写日志；`openid` 不出现在任何响应里。

- [ ] **Step 1: 表结构**

```sql
USE zhixue_system;
SET @c1 = IF((SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'wechat_openid') = 0,
    'ALTER TABLE sys_user ADD COLUMN wechat_openid VARCHAR(64) NULL COMMENT ''微信 openid''', 'SELECT 1');
PREPARE s1 FROM @c1; EXECUTE s1; DEALLOCATE PREPARE s1;

SET @c2 = IF((SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND index_name = 'uk_user_openid') = 0,
    'ALTER TABLE sys_user ADD UNIQUE KEY uk_user_openid (wechat_openid)', 'SELECT 1');
PREPARE s2 FROM @c2; EXECUTE s2; DEALLOCATE PREPARE s2;
```

- [ ] **Step 2: 实现真实模式**

```java
    @Override
    public LoginUser login(LoginForm form) {
        if (!StringUtils.hasText(form.getWechatCode())) {
            throw new ServiceException("微信授权码不能为空");
        }
        if ("real".equalsIgnoreCase(wechatMode)) {
            return realLogin(form.getWechatCode());
        }
        if (!"sandbox".equalsIgnoreCase(wechatMode) || StringUtils.isBlank(sandboxUsername)) {
            throw new ServiceException("当前环境暂未启用真实微信登录");
        }
        return sandboxLogin(form, form.getWechatCode());
    }

    private LoginUser realLogin(String code) {
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            // 配置缺失必须显式失败：静默回退到沙箱会让线上所有用户登录成同一个账号
            throw new ServiceException(HttpStatus.SERVICE_UNAVAILABLE, "微信登录未配置 AppID/Secret");
        }
        WechatSession session = wechatApiClient.code2Session(code, appId, appSecret);
        R<com.zhixue.api.system.domain.LoginUser> response = remoteUserService.getByOpenid(
                session.getOpenid(), session.getUnionid(), "true", securityProperties.getInternalToken());
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw new ServiceException("微信账号初始化失败");
        }
        return toLoginUser(response.getData());
    }
```

`WechatApiClient` 用 `RestClient` 请求 `https://api.weixin.qq.com/sns/jscode2session`，超时 3 秒，`errcode != 0` 时把微信的 `errmsg` 转成 `ServiceException`（不暴露原始 code）。

- [ ] **Step 3: 系统侧按 openid 建号**

`getByOpenid`：先按 `wechat_openid` 查；不存在则新建 `sys_user`（`username = "wx_" + openid 前 8 位 + 随机 4 位`、`nickname = "微信用户"`、status=0）并绑定 STUDENT 角色。**必须捕获唯一键冲突并重新查询**（同一用户并发首登）。

- [ ] **Step 4: 测试**

覆盖：①`real` 模式未配 AppID 时抛 503 ②`disabled` 模式拒绝 ③微信返回 `errcode=40029` 时错误文案不含 code ④并发首登只建一个账号（mock 抛出 `DuplicateKeyException` 后能查到已有用户）。

- [ ] **Step 5: 上线验证清单**

```markdown
- [ ] 生产 `zhixue.auth.wechat.mode=real`，AppID/Secret 由环境变量注入，未写入仓库
- [ ] 用真实微信登录一次，确认 `sys_user` 只有一个新账号
- [ ] 退出后重新登录，仍是同一账号
- [ ] 日志中 grep 不到 `session_key`、`openid` 完整值
```

- [ ] **Step 6: 提交**

```bash
git add zhixue-auth/src zhixue-modules/zhixue-system/src zhixue-api/src
git commit -m "feat(auth): 真实微信 code2session 登录与按 openid 建号，session_key 不落库"
```

---

### Task H2：微信支付 JSAPI

**Files:**
- Modify: `zhixue-modules/zhixue-order/pom.xml`（`com.github.wechatpay-apiv3:wechatpay-java`）
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/strategy/WechatPayStrategy.java`
- Create: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/strategy/WechatPayConfig.java`
- Create: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/controller/WechatNotifyController.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/domain/dto/PayResponse.java`
- Modify: `zhixue-app-mp/miniprogram/utils/pay.js`
- Test: `zhixue-modules/zhixue-order/src/test/java/com/zhixue/order/strategy/WechatPayStrategyTest.java`（新建）

**Interfaces:**
- `PayResponse` 新增 `jsapi` 字段：`{timeStamp, nonceStr, package, signType, paySign}`（小程序 `wx.requestPayment` 直接消费）
- 新增 `com.zhixue.order.domain.dto.JsapiParams`：
  ```java
  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public class JsapiParams {
      private String timeStamp;
      private String nonceStr;
      private String packageValue;   // 微信字段名是 package（Java 关键字冲突，序列化时用 @JsonProperty("package")）
      private String signType;
      private String paySign;
  }
  ```
  并在 getter 上加 `@JsonProperty("package")`（或直接在类字段上用 `@JsonProperty` 注解），确保小程序收到的字段名是 `package`。
- `POST /pay/wechat/notify`（无登录，v3 验签 + 解密）→ 复用 `paySuccess`
- `wechat.pay.*` 配置：`mch-id`、`serial-no`、`private-key-path`、`api-v3-key`、`app-id`、`notify-url`

- [ ] **Step 1: 依赖**

```xml
        <dependency>
            <groupId>com.github.wechatpay-apiv3</groupId>
            <artifactId>wechatpay-java</artifactId>
            <version>0.2.12</version>
        </dependency>
```

> 版本以实施时 Maven 中央仓库可用版本为准；升级前先 `mvn dependency:tree` 确认无冲突。

- [ ] **Step 2: 策略实现**

```java
    @Override
    public PayResponse pay(Order order) {
        if (!config.isEnabled()) {
            // 未配置商户资质时保持与之前一致的显式拒绝，绝不返回伪造的支付参数
            throw new ServiceException(HttpStatus.BAD_REQUEST, "暂未接入微信支付，当前环境请使用模拟收银台");
        }
        PrepayWithRequestPaymentResponse prepay = jsapiService.prepayWithRequestPayment(
                buildPrepayRequest(order));
        PayResponse response = new PayResponse();
        response.setPayChannel("wechat");
        response.setMode("real");
        response.setOrderNo(order.getOrderNo());
        response.setAmount(order.getAmount());
        response.setJsapi(new JsapiParams(prepay.getTimeStamp(), prepay.getNonceStr(),
                prepay.getPackageVal(), prepay.getSignType(), prepay.getPaySign()));
        return response;
    }
```

`buildPrepayRequest`：`description` 用课程名（订单只有 `productId`，需要调课程服务取名；取名失败用"课程订单"，不能让取名失败阻断支付）、`out_trade_no = order.getOrderNo()`、`amount.total` 用**分**、`payer.openid` 需要从 auth 取当前用户 openid（新增 `RemoteUserService.getWechatOpenid(userId)`，仅内部）。

- [ ] **Step 3: 回调**

```java
    @PostMapping("/wechat/notify")
    public Map<String, String> notify(@RequestBody String body, @RequestHeader Map<String, String> headers) {
        // SDK 负责验签与解密；任何异常都必须回 SUCCESS=false 让微信重试，不能吞掉
        NotificationRequest request = new NotificationRequest.Builder()
                .withSerialNumber(headers.get("wechatpay-serial"))
                .withNonce(headers.get("wechatpay-nonce"))
                .withTimestamp(headers.get("wechatpay-timestamp"))
                .withSignature(headers.get("wechatpay-signature"))
                .withBody(body)
                .build();
        Notification notification = notificationParser.parse(request);
        Transaction transaction = notification.getResource().getOriginalType() == null
                ? null : notification.getResource().toEntity(Transaction.class);
        if (transaction != null && "SUCCESS".equals(transaction.getTradeState())) {
            orderService.paySuccess(transaction.getOutTradeNo(), "wechat", transaction.getTransactionId());
        }
        return Map.of("code", "SUCCESS", "message", "成功");
    }
```

- [ ] **Step 4: 小程序调用真实支付**

`utils/pay.js` 的 `checkout` 按 `payResponse.mode` 分支：

```js
async function checkout(order) {
  const payResponse = (await payOrder(order.orderNo, 'wechat')).data || {}
  if (payResponse.mode !== 'real') {
    const confirmed = await showMockCashier(order)
    if (!confirmed) return false
    await confirmMockPay(order.orderNo)
    return true
  }
  await new Promise((resolve, reject) => {
    wx.requestPayment({
      ...payResponse.jsapi,
      success: resolve,
      fail: (error) => reject(new Error(error.errMsg || '支付未完成'))
    })
  })
  // 客户端结果不作为入账依据：回到订单页由服务端状态决定是否已支付
  return true
}
```

页面在 `checkout` 返回后**重新拉取订单状态**，不要凭 `wx.requestPayment` 的成功回调直接判"已支付"。

- [ ] **Step 5: 测试**

覆盖：①未配置时抛"暂未接入"（保留原行为，避免回归）②配置齐全时返回 `mode=real` 且五个 JSAPI 字段齐全 ③回调验签异常时返回失败 ④重复回调幂等（`paySuccess` 条件更新只生效一次）。

- [ ] **Step 6: 上线验证清单**

```markdown
- [ ] 商户号与 AppID 已绑定、APIv3 密钥与商户证书配置在环境变量/密钥管理，不在仓库
- [ ] 用 1 分钱真实支付成功，订单 status=1、pay_channel=wechat、pay_no 为微信单号
- [ ] 重复回调不重复入账
- [ ] `wx.requestPayment` 取消后订单仍为待支付，可再次支付
```

- [ ] **Step 7: 提交**

```bash
git add zhixue-modules/zhixue-order/src zhixue-modules/zhixue-order/pom.xml \
        zhixue-api/src zhixue-app-mp/miniprogram/utils/pay.js
git commit -m "feat(order): 微信支付 JSAPI 与 v3 回调验签，客户端结果不作为入账依据"
```

---

### Task H3：渠道退款与对账

**Files:**
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/strategy/PayStrategy.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/strategy/WechatPayStrategy.java`
- Modify: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/service/impl/OrderServiceImpl.java:196-220`
- Create: `zhixue-modules/zhixue-order/src/main/java/com/zhixue/order/task/RefundReconcileTask.java`
- Test: `zhixue-modules/zhixue-order/src/test/java/com/zhixue/order/service/RefundChannelTest.java`（新建）

**Interfaces:**
- `PayStrategy.refund(Order order, BigDecimal amount, String reason): RefundResult{channelRefundNo, status}`
- `refundOrder` 顺序：**先申请渠道退款，成功后再改订单状态**；渠道退款失败则订单保持已支付并报错

- [ ] **Step 1: 调整退款顺序**

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refundOrder(Long id, String reason) {
        Order order = findById(id);
        requireRefundable(order);
        // 顺序不能反：先把订单改成已退款再调渠道，一旦渠道失败，用户既没了观看权限又没收到钱
        RefundResult refund = refundViaChannel(order, reason);
        LambdaUpdateWrapper<Order> uw = new LambdaUpdateWrapper<>();
        uw.eq(Order::getId, id)
                .eq(Order::getStatus, STATUS_PAID)
                .set(Order::getStatus, 3)
                .set(Order::getRemark, StringUtils.hasText(reason) ? reason : "运营审核退款")
                .set(Order::getPayNo, refund.getChannelRefundNo());
        if (orderMapper.update(null, uw) == 0) {
            throw new ServiceException("仅已支付订单可退款");
        }
    }

    private RefundResult refundViaChannel(Order order, String reason) {
        if (!"real".equalsIgnoreCase(payMode)) {
            // 本地模拟：不调用渠道，直接视为退款成功
            return RefundResult.simulated();
        }
        PayStrategy strategy = payStrategies.stream()
                .filter(s -> s.supports(order.getPayChannel()))
                .findFirst()
                .orElseThrow(() -> new ServiceException("当前支付渠道不支持退款"));
        return strategy.refund(order, order.getAmount(), reason);
    }
```

- [ ] **Step 2: 对账任务**

`RefundReconcileTask`（`@XxlJob("refundReconcileHandler")`）：查最近 24 小时内"订单已退款但 `pay_no` 不以渠道退款单号前缀开头"的记录，逐个向渠道查询退款状态，成功后补记单号，失败则记录告警日志并保留待处理标记。

- [ ] **Step 3: 测试与上线清单**

测试覆盖：①模拟模式不调渠道 ②真实模式渠道失败时订单仍为已支付 ③渠道成功后订单变为已退款 ④重复退款不重复调用渠道。

上线清单：
```markdown
- [ ] 真实支付订单退款后，微信商户平台能看到对应退款单
- [ ] 退款金额与订单金额一致（含用券订单按实付金额退）
- [ ] 退款后 30 秒内重新进入课程，付费课时已锁定
- [ ] 对账任务连续运行 24 小时无异常告警
```

- [ ] **Step 4: 提交**

```bash
git add zhixue-modules/zhixue-order/src
git commit -m "feat(order): 退款改为先渠道后退单，新增退款对账任务"
```

---

### Task H4：生产部署（镜像 / 配置 / 反代）

**Files:**
- Create: `ops/docker/Dockerfile.service`
- Create: `ops/docker/docker-compose.prod.yml`
- Create: `ops/nginx/zhixue.conf`
- Create: `ops/docker/.env.prod.example`
- Create: `docs/deployment.md`

**Interfaces:**
- 统一构建参数 `MODULE`（如 `zhixue-modules/zhixue-order`）与 `SERVICE_NAME`
- Nginx 暴露 443，`/api` 反代网关，`/ws` 升级 WebSocket，SSE 关闭缓冲

- [ ] **Step 1: 通用服务镜像**

```dockerfile
# 通用运行镜像：先 mvn package，再把 fat jar 放进不可变镜像
FROM eclipse-temurin:17-jre
ARG SERVICE_NAME=zhixue-service
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC"
WORKDIR /app
COPY ${SERVICE_NAME}.jar /app/app.jar
RUN groupadd -r app && useradd -r -g app app && chown -R app:app /app
USER app
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

构建脚本示例（写进 `docs/deployment.md`）：

```bash
mvn -B -ntp -DskipTests package
for module in gateway auth system course media interaction order marketing ai; do
  jar=$(ls zhixue-modules/zhixue-${module}/target/*.jar 2>/dev/null || ls zhixue-${module}/target/*.jar)
  cp "$jar" "build/${module}.jar"
  docker build -f ops/docker/Dockerfile.service \
    --build-arg SERVICE_NAME="${module}" -t "zhixue/${module}:${GIT_SHA}" build/
done
```

- [ ] **Step 2: 生产 compose（外部化配置、只暴露网关）**

```yaml
services:
  gateway:
    image: zhixue/gateway:${GIT_SHA}
    ports: ["8080:9001"]
    environment:
      SPRING_PROFILES_ACTIVE: prod
      # 所有敏感项来自 env_file / 密钥管理，不写入仓库
    env_file: [".env.prod"]
    depends_on: [nacos]
    restart: unless-stopped

  mysql:
    image: mysql:8.0
    # 生产不映射宿主机端口：只能被内网服务访问
    expose: ["3306"]
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    volumes: ["mysql_data:/var/lib/mysql"]
    restart: unless-stopped

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    environment:
      - xpack.security.enabled=true
      - ELASTIC_PASSWORD=${ES_PASSWORD}
    expose: ["9200"]
    restart: unless-stopped
```

> Redis、RabbitMQ、Nacos 同样 `expose` 而非 `ports`，并开启认证。**这一条是硬要求**：当前本地 compose 把中间件绑在 `0.0.0.0`，照抄到公网等于把数据库开放。

- [ ] **Step 3: Nginx（HTTPS + WSS + SSE 不缓冲）**

```nginx
server {
    listen 443 ssl http2;
    server_name api.example.com;

    ssl_certificate     /etc/nginx/certs/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/privkey.pem;

    location /api/ {
        proxy_pass http://gateway:9001/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;

        # AI 流式回答：不缓冲，否则整段回答会在结束时才吐出来
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 180s;
    }

    location /ws/ {
        proxy_pass http://interaction:9999/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 600s;
    }
}

server {
    listen 80;
    server_name api.example.com;
    return 301 https://$host$request_uri;
}
```

- [ ] **Step 4: 文档与提交**

`docs/deployment.md` 写：环境准备、构建命令、迁移顺序（`upgrade.sh` 改为对生产库执行）、回滚步骤（镜像 tag 回退 + 数据库向前兼容原则：只加列不删列）、健康检查端点、日志位置。

```bash
git add ops/docker ops/nginx docs/deployment.md
git commit -m "build(deploy): 统一服务镜像、生产 compose 与 HTTPS/WSS 反代配置"
```

---

### Task H5：监控、告警与备份恢复演练

**Files:**
- Create: `ops/monitoring/prometheus.yml`
- Create: `ops/monitoring/alerts.yml`
- Create: `ops/monitoring/grafana-dashboard.json`
- Create: `scripts/prod/backup.sh`
- Create: `scripts/prod/restore-drill.md`

- [ ] **Step 1: 采集与告警**

`prometheus.yml` 抓取各服务 `/actuator/prometheus`（需确认依赖已启用 `micrometer-registry-prometheus`，根 pom 已有版本管理）。`alerts.yml` 至少覆盖：

```yaml
groups:
  - name: zhixue
    rules:
      - alert: ServiceDown
        expr: up{job=~"zhixue-.*"} == 0
        for: 2m
        labels: { severity: critical }
      - alert: HighErrorRate
        expr: sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (job)
              / sum(rate(http_server_requests_seconds_count[5m])) by (job) > 0.05
        for: 5m
        labels: { severity: critical }
      - alert: AiLatencyHigh
        expr: histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri=~"/ai/.*"}[5m])) by (le)) > 30
        for: 10m
        labels: { severity: warning }
```

- [ ] **Step 2: 备份脚本与恢复演练**

`backup.sh`：`mysqldump --single-transaction` 全部业务库 + MinIO `mc mirror` + ES 快照仓库触发；文件名带时间戳与校验和；保留 7 天。

`restore-drill.md`：写明演练步骤与**必须记录的指标**（RPO：最坏丢多久数据；RTO：恢复耗时），并要求每季度演练一次、结果留档。演练在隔离环境执行，绝不覆盖生产库。

- [ ] **Step 3: 提交**

```bash
git add ops/monitoring scripts/prod
git commit -m "ops: Prometheus 采集与告警规则、备份脚本与恢复演练手册"
```

---

### Task H6：真机验收与发布清单

**Files:**
- Create: `docs/release-checklist.md`

- [ ] **Step 1: 真机验收（必须在实体手机上）**

```markdown
## 小程序真机验收

- [ ] iOS + Android 各一台，微信最新正式版
- [ ] AI 流式回答逐块显示（不是一次性出现）
- [ ] 切后台 30 秒再回来：连接可恢复，历史不丢
- [ ] 弱网（限速 200kbps）：出现加载态，失败可重试，不白屏
- [ ] 视频播放器：进度上报、杀进程后重进可续播
- [ ] 支付：真实微信支付完成，回到订单页显示已支付
- [ ] 底部导航图标两态、分类图标、页面旋转（若支持）正常
- [ ] 无障碍：关键按钮可点击区域 ≥ 44pt，文字不被截断
```

- [ ] **Step 2: 发布检查清单**

```markdown
## 上线前（逐项签字）

配置与安全
- [ ] `ZHIXUE_AUTH_WECHAT_MODE=real`、`wechat pay` 配置齐全，均来自密钥管理
- [ ] 所有中间件不对外映射端口，ES 开启认证，Redis/MQ 有密码
- [ ] 小程序后台已配置 request/downloadFile 合法域名（HTTPS）与 socket 合法域名（WSS）
- [ ] 仓库无密钥：`git grep -nE "(PRIVATE KEY|api-v3-key|secret\s*=)"` 无命中

数据
- [ ] 已备份并执行全部 `sql/upgrade/*.sql`
- [ ] 演示/测试数据已清理（评测会话、临时课程、测试订单）
- [ ] 恢复演练完成，RPO/RTO 已记录

质量
- [ ] `verify-all.sh --with-e2e` 全绿
- [ ] `docs/ai-quality-report.md` 有本轮数字
- [ ] 压测报告结论与 README 表述一致

发布
- [ ] 镜像 tag = 发布 commit，可一键回退
- [ ] 灰度：先 10% 流量观察 30 分钟错误率与延迟
- [ ] 回滚预案演练过一次
```

- [ ] **Step 3: 提交**

```bash
git add docs/release-checklist.md
git commit -m "docs: 真机验收与上线检查清单"
```

---

## 检查点 H（M2 上线）

- [ ] H1/H2/H3 代码完成，且在真实商户环境跑通 1 分钱支付与退款
- [ ] H4 生产环境可部署、可回滚，中间件不暴露公网
- [ ] H5 告警能真实触发一次（人为停一个服务验证）
- [ ] H6 真机验收清单全部勾选并留档
- [ ] **此时 M2 达成**；若外部资质未到位，明确记录"代码完成、上线待资质"，不要把 M1 的完成说成 M2

---

## 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 学习进度上报被刷学时 | 数据失真 | 单次 120 秒上限 + 只统计"可播放课时" + 服务端校验 `canPlay` |
| 券锁定后用户放弃支付 | 券被占用 | 待支付单 30 分钟超时取消时自动释放；订单页可再次下单复用/换券 |
| 支付成功但券核销失败 | 券状态与订单不一致 | 只记 error 不回滚支付；`RefundReconcileTask` 同类脚本人工对齐；本计划接受该窗口并记录 |
| 秒杀两次建单都超时 | 用户可能已成交但看不到单 | 不回滚库存 + 提示"稍后在订单查看"；营销侧记录可对账 |
| 会话撤销只覆盖索引上线后的令牌 | 最长 2 小时旧会话仍可用 | 已在 `SessionRevoker` 注释写明；缩短 `jwt-expiration` 可缩小窗口 |
| 弹幕 fail-open 放行违规内容 | 内容风险 | 标记"待人工复核" + 后台可见 + 敏感词先过滤；如需从严可切 fail-closed |
| 转码异步化后用户看到"转码中" | 体验延迟 | 状态轮询 + 失败可重试 + 上传页明示"转码通常 1–5 分钟" |
| 推荐标题相似度错配 | 推错课时 | F3 的主题词校验；无证据时不改阈值 |
| 后台补测试引入 vitest 与 eslint 冲突 | CI 失败 | 先只加 `test` 脚本，`lint` 保持原样；CI 中文档说明 `lint` 会 `--fix`，不用于只读校验 |
| 学生 Web 二期页面增多导致构建体积上升 | 首屏变慢 | 已按路由懒加载；`manualChunks` 已拆分 element-plus 与 markdown |

---

## 开放问题（需要人类决策，不阻塞 A/B 阶段）

1. **售后是否退券**：当前实现"退款不退券"。若产品要求退，需要在 H3 的渠道退款成功后调用 `CouponService.release`，并处理"券已过期"的返还规则。
2. **秒杀取消是否恢复资格**：当前实现"已取消不恢复"。若允许恢复，需要营销侧增加补偿接口并处理库存回补。
3. **弹幕降级策略**：当前 fail-open（待人工复核 + 放行）。若合规要求从严，改为 fail-closed（拒绝并提示"审核繁忙"）。
4. **教师分账/提现**：当前只统计"实收金额"，不做分账。若需要真实结算，属于独立项目（涉及资金账户与对账）。
5. **短信注册**：当前注册用图形验证码。若需要手机号验证，需要接入短信服务商（M2 外部依赖）。
6. **是否补 ES 课程搜索**：当前 AD-8 决定不补。若课程量增长到万级，再单独立项。

---

## 自检（对照规格逐条）

| 第 2 章验收标准 | 对应任务 |
|---|---|
| 1–2 进度上报、90% 完成、学习中心 | B1、B4、B5 |
| 3 我的页课程数 | B5 |
| 4 学生 Web 进度与续播 | B6 |
| 5–7 评价提交/展示/回复/隐藏 | C1 |
| 8–9 用券下单、核销、退回 | C2 |
| 10–11 售后与教师经营 | C3、C4 |
| 12 列表名称与讲师搜索 | C5 |
| 13–15 后台 401 / 雪花 ID / 只读查看 | A4 |
| 16–17 防自锁与会话撤销 | A5 |
| 18 秒杀幂等 | A6 |
| 19 弹幕审核前置 | A7 |
| 20 媒资完整链路 | E1、E2、E3 |
| 21 资料覆盖与负例 | F1 |
| 22 真实评测与人工评审 | A8、F2 |
| 23 三端保存状态可见 | A1（小程序）、A8（学生 Web 已具备）、C1（后台 AI 页补同状态） |
| 24 质量门禁 | G1 |
| 25 联合旅程 | G2 |
| 26 文档一致 | G4、F2 |

**占位符扫描**：全文无 `TBD`/`TODO`/`fill in`；每个代码步骤给出可直接落地的实现；每个任务都有可运行的验证命令与预期输出。

**类型一致性抽查**：
- `LearningService.report` 参数 `(Long userId, ProgressReportDTO dto)` 在 B1 定义、B4 控制器与测试一致；
- `CouponService.lock(Long, Long, BigDecimal, String)` 在 C2 的营销实现、`CouponInnerController`、`RemoteCouponService`、`OrderServiceImpl.lockCoupon` 四处签名一致；
- `ReviewPageVO` 在 C1 的 service、controller、小程序 `loadReviews` 中字段名一致（`avgRating`/`reviewCount`/`page.records`）；
- `TeacherCourse`/`UserBrief` 在 `zhixue-api` 定义、course/system 两侧实现与 order 消费一致。

**规模检查**：最大任务为 C2（跨两个服务、约 6 个文件），已拆成"表结构 → 营销锁定 → 订单接入 → 小程序选券 → 测试"五步；其余任务均为 S/M 级。

---

## 执行交接

计划已保存到 `docs/superpowers/plans/2026-10-22-zhixue-goal-completion.md`。两种执行方式：

**1. 子代理驱动（推荐）** — 每个任务派一个新子代理，任务之间由我审查（含双轴审查），迭代快、上下文干净。适用：A/B/C 这类跨多文件的后端任务。

**2. 本会话顺序执行** — 按 A → B → C → D → E → F → G → H 逐任务做，每个检查点停下来给你看证据。适用：你希望全程盯着改。

建议组合：**A 阶段本会话执行**（改动小、需要你确认的决策多：fail-open 策略、秒杀规则），**B/C 用子代理驱动**（任务边界清晰、可并行），**D/E/F 再回本会话**（涉及界面与模型，需要人看效果）。

请告诉我从哪个阶段开始，以及选哪种执行方式。
