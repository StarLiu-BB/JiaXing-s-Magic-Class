# 智学云 (Zhixue Cloud) - 高性能在线教育微服务平台

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023-blue.svg)](https://spring.io/projects/spring-cloud)

智学云是一个基于 Spring Cloud Alibaba 微服务架构构建的大型在线教育平台。项目集成了 **AI 智能助手 (RAG)**、**视频点播 (分片上传/自动转码)**、**高并发秒杀**、**实时弹幕互动** 等核心业务场景，旨在提供一套完整的企业级在线教育解决方案。

## 🌟 核心亮点 (Core Features)

### 1. 🤖 AI 智能助手 (RAG & Audit)
- **检索增强生成 (RAG)**：基于 Elasticsearch 向量检索课程知识库，结合大模型（通义千问/智谱 GLM-4）实现精准的课程答疑。
- **智能内容审核**：集成 AI 审核能力，对弹幕、评论进行实时违规检测，保障平台内容安全。

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
    ```

## 🤝 贡献 (Contributing)
欢迎提交 Issue 或 Pull Request 来完善项目！

1. Fork 本仓库
2. 新建 Feat_xxx 分支
3. 提交代码
4. 新建 Pull Request
