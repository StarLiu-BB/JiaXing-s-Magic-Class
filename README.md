# 智学云 (Zhixue Cloud) - 高性能在线教育微服务平台

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023-blue.svg)](https://spring.io/projects/spring-cloud)

智学云是一个基于 Spring Cloud Alibaba 微服务架构构建的大型在线教育平台。项目集成了 **AI 智能助手 (RAG)**、**视频点播 (分片上传/自动转码)**、**高并发秒杀**、**实时弹幕互动** 等核心业务场景，旨在提供一套完整的企业级在线教育解决方案。

## 🌟 核心亮点 (Core Features)

### 1. 🤖 AI 智能助手 (RAG & Audit)
- **检索增强生成 (RAG)**：基于 Elasticsearch 向量检索课程知识库（按课程隔离），结合本机部署的开源大模型（Ollama + Qwen3.5）实现课程答疑，无需任何外部 API Key。
- **智能内容审核**：弹幕落库前经本地模型实时审核（关键词兜底），违规弹幕不展示，管理端可人工复核。

### 2. 🎬 高性能媒资系统
- **大文件处理**：支持 GB 级视频文件的 **分片上传**、**断点续传** 和 **秒传**。
- **自动化转码**：文件上传合并后，自动触发 FFmpeg 转码任务，适配 HLS/MP4 等多种播放格式。

### 3. ⚡ 高并发营销秒杀
- **原子扣减**：**Redis + Lua 脚本** 在一次原子操作内完成"是否已抢 → 扣库存 → 记录用户"，杜绝超卖与一人多单；下单失败时由补偿脚本幂等回滚。
- **一次性令牌**：参与人取自登录态，令牌按"活动 + 用户"签发，并以 `GETDEL` 原子消费，不能替他人抢、也不能重放。
- **成交即下单**：抢到后同步调用订单服务，按秒杀价生成真实的待支付订单，直接进入收银台。**未使用 MQ**：Lua 已把能进入下单环节的请求限制在库存数以内，数据库写入量被 Redis 挡住，同步下单即可，客户端也无需轮询结果。
- **库存装载**：进行中的活动在首次访问时按"总库存 − 已成交"惰性装载（`SETNX`），重复预热不会把已售库存加回来。

### 4. 💬 实时互动系统
- **弹幕架构**：基于 **Netty + WebSocket** 构建高性能长连接服务。
- **集群广播**：利用 Redis Pub/Sub 实现跨节点的消息广播，支持百万级用户同时在线互动。

### 5. 💰 订单与支付
- **服务端核价与鉴权**：下单人取自登录态、金额由订单服务向课程服务核价，客户端传入的身份与金额一律忽略；已购课程不可重复下单。
- **购买即开课**：订单表是购买关系的唯一事实来源，课程服务按"免费 / 已购 / 本课教师 / 试看"决定是否下发课时视频地址，退款后权限自动失效。
- **支付回调安全**：HMAC-SHA256 验签 + 金额核对 + 条件更新幂等入账。
- **支付渠道**：策略模式封装。当前可用的是**模拟收银台**（本地联调，不产生真实扣款，需显式开启 `ZHIXUE_PAY_MOCK_ENABLED=true`）；支付宝、微信支付尚未接入商户资质，调用时明确拒绝，而不是返回伪造的支付链接。
- **事务**：订单创建只涉及本库，使用本地事务；未引入 Seata（此前的 `@GlobalTransactional` 缺少 `undo_log` 无法回滚，已移除）。

## 🛠 技术栈 (Tech Stack)

### 后端 (Backend)
- **基础框架**：Spring Boot 3.2, Spring Cloud 2023, Spring Cloud Alibaba
- **服务治理**：Nacos (注册/配置), Sentinel (熔断限流), Gateway (网关)
- **数据存储**：MySQL 8.0, Redis 7.0, Elasticsearch 8.x, MinIO (对象存储)
- **消息队列**：RabbitMQ (异步解耦/延时队列)
- **任务调度**：XXL-Job
- **ORM 框架**：MyBatis-Plus

### 前端 (Frontend)
- **管理后台**：Vue 3, Vite, Element Plus, Pinia
- **移动端**：微信小程序 (原生开发)

## 📂 模块结构 (Module Structure)

```text
zhixue-cloud
├── zhixue-gateway         # 网关服务 (9001)
├── zhixue-auth            # 认证中心 (9100)
├── zhixue-common          # 通用模块 (工具类/异常处理)
├── zhixue-api             # Feign 接口定义
├── zhixue-modules         # 业务微服务聚合
│   ├── zhixue-system      # 系统管理 (用户/角色/权限)
│   ├── zhixue-course      # 课程中心 (课程/章节/类目)
│   ├── zhixue-media       # 媒资中心 (视频上传/转码)
│   ├── zhixue-order       # 订单中心 (订单/支付/购买关系)
│   ├── zhixue-marketing   # 营销中心 (秒杀/优惠券)
│   ├── zhixue-interaction # 互动中心 (弹幕/点赞/评论)
│   └── zhixue-ai          # AI 中心 (RAG/LLM/审核)
├── zhixue-admin-web       # 后端管理前端 (Vue3)
├── zhixue-student-web     # 学生端网页版 (Vue3，AI 课程助教对话与视频课推荐)
└── zhixue-app-mp          # 微信小程序端
```

## 🚀 快速开始 (Quick Start)

### 环境准备
- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Redis 7.0+
- Nacos 2.x
- RabbitMQ
- MinIO
- Elasticsearch 8.x (可选，AI 功能依赖)
- Ollama (可选，AI 功能依赖，见下方「本地 AI」)

### 启动步骤

1.  **克隆项目**
    ```bash
    git clone https://github.com/StarLiu-BB/JiaXing-s-Magic-Class.git
    cd zhixue-cloud
    ```

2.  **初始化数据库**
    - 创建数据库 `zhixue_cloud`。
    - 导入 `sql` 目录下的初始化脚本（如有）。

3.  **配置 Nacos**
    - 启动 Nacos Server。
    - 将 `config` 目录下的配置文件导入 Nacos 配置中心，或修改各模块 `bootstrap.yml` 中的 Nacos 地址。

4.  **启动微服务**
    - 建议启动顺序：`zhixue-gateway` -> `zhixue-auth` -> `zhixue-system` -> 其他业务模块。

5.  **启动前端**
    ```bash
    # 管理后台
    cd zhixue-admin-web
    npm install
    npm run dev

    # 学生端网页版（AI 课程助教对话与视频课推荐，端口 3001）
    cd zhixue-student-web
    npm install
    npm run dev
    ```

### 本地 AI（Ollama）

AI 答疑、知识库向量化、弹幕审核全部由本机的 [Ollama](https://ollama.com) 提供，免费、离线、数据不出本机。

```bash
brew install ollama && brew services start ollama   # 只监听 127.0.0.1:11434
ollama pull qwen3.5:9b             # 对话与审核，约 6.6GB
ollama pull qwen3-embedding:0.6b   # 中文向量模型，1024 维，约 639MB
```

| 配置项（`ops/local/local.env`） | 默认值 | 说明 |
|---|---|---|
| `ZHIXUE_AI_BASE_URL` | `http://host.docker.internal:11434` | 容器内访问宿主机上的 Ollama |
| `ZHIXUE_AI_CHAT_MODEL` | `qwen3.5:9b` | 对话与审核模型 |
| `ZHIXUE_AI_EMBEDDING_MODEL` | `qwen3-embedding:0.6b` | 向量模型 |
| `ZHIXUE_EMBEDDING_DIMENSION` | `1024` | 必须与向量模型输出一致；变更后 AI 服务启动时自动重建 ES 索引并从数据库写回 |
| `ZHIXUE_AI_MODE` | `real` | `real` 失败如实报错；`sandbox` 检索退化为词法匹配；`stub` 不调用模型（测试用） |

在 Apple M5 Pro / 48GB 上实测：冷启动加载约 6s；答疑 3–11s（约 56 token/s）；流式回答首字 0.1–0.2s（模型已就绪时），
回答结束到推荐出现 0.01–0.12s；弹幕审核约 0.4s/条，12 条典型弹幕判断全部正确。
模型不可用时答疑返回「AI 服务暂不可用」，弹幕审核按通过处理（不阻塞发送）。

> 本机 Ollama 按块输出，块间隔可达 1 秒以上：模型刚被唤醒或冷启动时首字可达 1–1.5 秒，连续提问时 0.1–0.2 秒。
> 验收脚本先预热两次并对首字只断言「3 秒内出现」，精确时延随分数一起打印。

### 本地回归

```bash
bash scripts/local/smoke.sh        # 接口冒烟（45 项）
bash scripts/local/journeys.sh     # 三端联合旅程：老师建课发布 -> 管理员建秒杀 -> 学生抢购支付、学习、发弹幕
                                   #（本地模型审核）-> 学生 AI 对话（流式、推荐卡片、追问、并发 409）->
                                   # 管理员退款（需 Node.js 22+）
bash scripts/local/ai-reco-check.sh  # 推荐效果验收：打印每门课/每个课时的真实余弦分数，并校验 5 个验收
                                     # 用例与时延（首字 ≤ 3s、回答结束到推荐 ≤ 0.5s，需 Node.js 22+）
bash scripts/local/contract-check.sh # 前端接口路径与网关路由的契约检查
bash scripts/local/student-web-check.sh  # 学生端网页版界面验收（真实浏览器、真实网关、本机模型，
                                         # 需 playwright-cli；分段执行，见脚本开头的说明）
bash scripts/local/mp-api-check.sh   # 小程序端接口联调：按各页真实调用顺序经网关验证（17 项）
```

学生端在本地以微信沙箱登录（`ZHIXUE_AUTH_WECHAT_MODE=sandbox`，所有微信用户映射为 `student`，仅限联调）。

#### 会话撤销版本升级（A5）

账号停用/删除、修改密码或角色，以及角色授权变更会撤销旧会话；菜单权限修改/删除会使所有旧会话失效。HTTP 下一次请求和 WebSocket 下一次文字收发重新校验会话。Redis 故障拒绝授权变更，不静默放行。

- 本次版本首次启用时，旧的无版本令牌要求重新登录；不保留两小时的撤权窗口。
- 部署前备份，统一升级 `auth`、`gateway`、`system`、`interaction` 及它们打包的 `common`，不能只更新一个服务并继续发行旧格式令牌。本次 Git 更新不自动部署。
- 版本元数据与 token 使用现有单节点 Redis；不要单独清除版本键，否则会破坏撤销依据。Redis 数据恢复/切换时应强制重新登录。没有宣称 Redis Cluster 已验收。
- 授权读取和修改共用 MySQL 的 ADMIN 角色行锁；管理员不能删除/停用自己，不能移除自身超管角色，至少保留一个启用超管，恢复权限菜单不能停用/删除。锁定前提是数据库已有启用 ADMIN 角色，脏存量数据要在隔离环境核对修复。
- 由于 Redis 和 MySQL 不是分布式事务，数据库后续回滚时旧会话可能已被保守撤销，用户重新登录即可；不会因补偿失败恢复旧权限。

无外部服务的回归是 `mvn test`。本机安装 `redis-server` 后，可显式运行隔离 Redis 测试（随机本地端口、无持久化、自动关闭，不连接业务 Redis）：

```bash
mvn test -pl zhixue-common -Dtest=LoginSessionRedisTest -Dzhixue.test.isolated-redis=true
```

真实 MySQL 并发事务、多实例滚动升级及业务 Redis 故障演练仍需隔离环境验收，不能用单元测试替代。
已知功能缺口与遗留风险见 [docs/three-ends-gap-list.md](docs/three-ends-gap-list.md)。

#### 已有数据的环境升级（不要执行 `db-init.sh`，它会清空全库）

先备份已有数据库，并确认本地 MySQL 已启动、`ops/local/local.env` 已配置：

```bash
bash scripts/local/upgrade.sh --dry-run  # 只列出增量 SQL，不读取配置、不连接数据库
bash scripts/local/upgrade.sh            # 确认备份和 SQL 后执行；首个错误即停止
```

脚本按模块依赖顺序、模块内文件名顺序执行所有 `sql/upgrade/*.sql`，包括为后续 AI 可靠性版本准备的 `notice` / `trace` 升级；新增列不代表新版 AI 已部署。不重建库、不灌入种子数据，也不改写本地配置。认证使用 MySQL 容器已有的环境变量，密码不会出现在宿主机命令参数中。

现有增量脚本可以重复执行，但 MySQL DDL 不保证整批事务回滚；中途失败时先核对实际库结构，再重试。升级不自动构建或重启服务，需完成对应模块构建并确认迁移后再部署：

```bash
docker compose --env-file ops/local/local.env -f ops/local/docker-compose.yml up -d --force-recreate --no-deps course ai
bash scripts/local/ai-reco-check.sh
```

## 🤝 贡献 (Contributing)
欢迎提交 Issue 或 Pull Request 来完善项目！

1. Fork 本仓库
2. 新建 Feat_xxx 分支
3. 提交代码
4. 新建 Pull Request
