# 学生端 AI 对话与课程推荐 · 一期后端实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use incremental-implementation（模式 B 子代理控制器优先，否则模式 A 本会话执行）to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 AI 服务具备「会话 + 流式回答 + 课程推荐」，让课程服务提供按显式身份判定的课程大纲内部接口，为学生端网页版（另一份计划）提供稳定的后端契约。

**Architecture:** 三个垂直切片，每片结束都用真实网关、真实模型验证：
1. 种子与检索：3 门 Java 课与 8 篇知识资料（含可重复执行的增量脚本）、启动时补齐未入索引的分片、带余弦分数的检索；随后立即用真实模型校准推荐门槛（检查点 A）。
2. 推荐：课程服务新增 `/inner/outlines`（按请求体里的学生身份判定可播放），AI 服务按同一次检索结果选课、按课时标题向量选课时（检查点 B）。
3. 会话与流式对话：两张新表与 CRUD、真流式 `chatStream`（跨块过滤 `<think>`）、占位/检索/流式/推荐/落库编排、SSE 接口、验收脚本（检查点 C）。

**Tech Stack:** Java 17、Spring Boot 3.2.0、Spring Cloud 2023.0.0、MyBatis-Plus 3.5.7、Reactor（AI 模块已有 webflux）、Elasticsearch 8、Ollama（qwen3.5:9b、qwen3-embedding:0.6b）、JUnit 5 + Mockito + AssertJ、Node 22（脚本）。

**依据：** `docs/superpowers/specs/2026-10-20-student-web-ai-chat-design.md`（含评审修订）。

## Global Constraints

- 相似度门槛一律是**余弦值**。ES `_score` 必须换算 `cos = 2 × _score − 1` 后再比较（实测「今天中午吃什么」`_score` 0.64–0.66，余弦仅 0.28–0.32）。
- 推荐门槛：课程 ≥ `0.55`，课时 ≥ `0.50`；最多 `3` 门课，每门最多 `2` 个课时。回答检索门槛沿用 `ai.rag.min-similarity = 0.45`。
- 一次检索取 `8` 条命中：前 `4` 条进提示词，全部 `8` 条参与推荐；无课程归属（`source` 不是 `course:{id}`）的资料只参与回答。
- 问题 ≤ `500` 字；追问带最近 `3` 轮**已完成**的问答，每条截断到 `800` 字；会话标题取首问前 `20` 字；重命名 `1–64` 字。
- 同一用户同时只能有一个回答在生成：**写任何数据之前**原子占位，失败返回 `409`；占位后、流开始前的任何异常必须释放占位；流开始后在结束钩子里释放且只释放一次。
- 流前错误：业务错误 `HTTP 200` + `body.code`（400 / 404 / 409 / 500）；网关鉴权失败 `HTTP 401`。流开始后的错误用 `event: error`，`code` 为 `503` 或 `504`。
- SSE 响应头：`Cache-Control: no-cache`、`X-Accel-Buffering: no`。
- SSE 与 REST 响应里所有业务 ID 都是字符串。
- 他人会话一律 `404`（「会话不存在或已删除」），不返回 403。
- 内部接口必须先校验 `SecurityUtils.isTrustedInternalCall()`，否则 `403`。
- 推荐失败（课程服务、向量化）只记错误日志，推荐为空，回答照常完成；模型不可用时对外统一「AI 服务暂不可用，请稍后再试」，内部地址与模型名只进日志。
- 已有数据的环境**不得执行 `db-init.sh`**（会清空全库）；只用增量脚本（`CREATE TABLE IF NOT EXISTS`、`INSERT IGNORE`），可重复执行。
- 新增后端依赖只允许 `spring-cloud-starter-loadbalancer`（AI 模块调用 Feign 必需）。不引入 `reactor-test`，流式测试用 `block()` / `subscribe()`。
- 现有 `/ai/chat/course` 接口与其测试保持不变。
- 每个任务先写失败测试、确认失败、再实现；任务结束跑该模块全部测试再提交。
- 本机命令行是 zsh：凡是 `source scripts/local/common.sh && load_env` 的命令都要放进 bash 脚本里执行（`bash x.sh` 或 `bash <<'SH'`），且在 `load_env` 之后加 `set +e +o pipefail`——`common.sh` 开启了 `set -euo pipefail`，`grep -v` 无输出时整个脚本会静默退出。不要在 heredoc 驱动的 bash 里调用不带重定向的 `docker exec -i`，它会吞掉后续脚本。

## 文件结构

**课程服务 `zhixue-modules/zhixue-course`**

| 文件 | 职责 |
|---|---|
| `service/support/LessonAccessPolicy.java`（改） | 新增 `Viewer` 与显式身份重载；原方法委托 |
| `service/CourseOutlineService.java`、`service/impl/CourseOutlineServiceImpl.java`（新） | 组装课程大纲：已发布且上架、章节/课时序号、可播放、可试看 |
| `controller/CourseInnerController.java`（新） | `POST /inner/outlines`，仅限内部调用 |
| `resources/sql/course_seed.sql`（改） | 追加 `java-courses` 段：3 门课、8 章、15 课时 |
| `resources/sql/upgrade/2026-10-20-java-courses.sql`（新，由种子段生成） | 已有数据环境的增量版本 |

**API 模块 `zhixue-api`**

| 文件 | 职责 |
|---|---|
| `course/domain/CourseOutlineQuery.java`、`CourseOutline.java`、`LessonOutline.java`（新） | 内部接口 DTO |
| `course/RemoteCourseOutlineService.java`（新） | Feign 客户端，直连路径 `/inner/outlines`（无 `/course` 前缀），无 fallback |

**AI 服务 `zhixue-modules/zhixue-ai`**

| 文件 | 职责 |
|---|---|
| `service/VectorStoreService.java`、`impl/VectorStoreServiceImpl.java`（改） | `searchWithScores`：余弦分数 + 课程归属 |
| `service/KnowledgeBaseService.java`、`impl/KnowledgeBaseServiceImpl.java`（改） | `retrieveScored`、`indexPending` |
| `config/KnowledgeIndexInitializer.java`（改） | 索引非空时补齐 `es_id IS NULL` 的分片 |
| `config/RecommendConfig.java`（新） | `ai.recommend.*` |
| `chat/Viewer.java`（新） | 提问者身份，只能在请求线程上取 |
| `chat/LessonTitleVectors.java`（新） | 课时标题向量 LRU 缓存 |
| `chat/RecommendationCard.java`、`chat/StoredRecommendation.java`（新） | 实时卡片 / 落库形态（只有 ID 与分数） |
| `chat/CourseRecommender.java`（新） | 选课、选课时、历史卡片批量重解析 |
| `service/impl/ThinkFilter.java`（新，包内可见） | 跨块过滤 `<think>`，只被 `LlmServiceImpl` 使用 |
| `chat/MessageStatus.java`、`chat/ConversationStore.java`（新） | 会话/消息持久化、归属校验、历史问答 |
| `chat/GenerationSlots.java`（新） | 「每人一个进行中回答」的占位 |
| `chat/PromptAssembler.java`（新） | 系统指令 + 历史 + 资料 + 问题 |
| `chat/ChatEvents.java`、`chat/AskRequest.java`、`chat/Source.java`（新） | SSE 事件载荷与请求体 |
| `chat/ConversationChatService.java`（新） | 编排：校验 → 占位 → 建会话/存问题 → 检索 → 流式 → 推荐 → 落库 → done |
| `controller/ConversationController.java`（新） | 4 个会话接口：列表、历史、重命名、删除 |
| `controller/ConversationStreamController.java`（新） | 流式提问 `POST /conversations/messages` |
| `domain/entity/AiConversation.java`、`AiMessage.java`、`mapper/AiConversationMapper.java`、`AiMessageMapper.java`（新） | 两张新表 |
| `service/LlmService.java`、`impl/LlmServiceImpl.java`、`config/LlmConfig.java`（改） | `chatStream` 真流式、首块超时 |
| `resources/sql/ai_schema.sql`、`ai_seed.sql`（改） | 建表、`java-knowledge` 段 |
| `resources/sql/upgrade/2026-10-20-java-knowledge.sql`、`2026-10-20-ai-conversation.sql`（新） | 增量脚本 |
| `AiApplication.java`、`pom.xml`、`application.yml`（改） | `@EnableFeignClients`、loadbalancer、新配置 |

**脚本与文档**

| 文件 | 职责 |
|---|---|
| `scripts/local/lib/ai-sse.mjs`（新） | SSE 解析、提问（带事件时间戳）、学生微信沙箱登录的共用函数 |
| `scripts/local/ai-reco-check.sh`、`ai-reco-check.mjs`（新） | 推荐效果验收（5 个用例，打印实际余弦） |
| `scripts/local/journeys.mjs`（改） | 新增「流式对话 → 推荐 → 追问 → 他人不可读」 |
| `scripts/local/contract-check.sh`（改） | 追加扫描 `zhixue-student-web/src`（目录不存在时自动跳过） |
| `README.md`、设计文档（改） | 增量上线步骤；回填实测门槛与分数 |

## 任务总览

| # | 任务 | 依赖 | 规模 |
|---|---|---|---|
| 1 | Java 种子课 + 知识资料 + 增量脚本 | — | M |
| 2 | 启动时补齐未入索引的分片 | 1 | S |
| 3 | 带余弦分数的检索与资料解析 | — | M |
| 检查点 A | 增量上线 + 真实模型校准门槛 | 1–3 | — |
| 4 | `LessonAccessPolicy` 显式身份 | — | S |
| 5 | 课程大纲内部接口 + Feign 契约 | 4 | M |
| 6 | 推荐器 | 3、5 | M |
| 检查点 B | 内部接口权限 + 全量测试 | 4–6 | — |
| 7 | `chatStream` 真流式 + 跨块 `<think>` 过滤 | — | M |
| 8 | 会话两张表 + 存储 + 4 个 REST 接口 | 6 | M |
| 9 | 流式提问接口（编排 + SSE） | 7、8 | M |
| 10 | 验收脚本、三端旅程、文档回填 | 9 | M |
| 检查点 C | 全量回归 | 全部 | — |

任务 3、4、7 互不依赖，可以并行；其余按表中顺序。

---

### Task 1: Java 种子课 + 知识资料 + 增量脚本

**Files:**
- Modify: `zhixue-modules/zhixue-course/src/main/resources/sql/course_seed.sql`（文件末尾追加一段）
- Create（生成）: `zhixue-modules/zhixue-course/src/main/resources/sql/upgrade/2026-10-20-java-courses.sql`
- Modify: `zhixue-modules/zhixue-ai/src/main/resources/sql/ai_seed.sql`（文件末尾追加一段）
- Create（生成）: `zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-20-java-knowledge.sql`
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/config/JavaSeedIntegrityTest.java`

**Interfaces:**
- Produces: 课程 `2005/2006/2007`（老师 1002、分类 110、已发布已上架、试看前 2 节）；章节 `3011–3018`；课时 `4011–4025`；知识文档 `91011–91018`（`source=course:2005|2006|2007`）；分片 `92011–92018`（每篇一个，`es_id` 为 NULL）。
- 种子里用 `-- BEGIN java-courses` / `-- END java-courses`、`-- BEGIN java-knowledge` / `-- END java-knowledge` 标出新增段；增量脚本由这两段**生成**，测试保证两者逐字一致（只差 `INSERT IGNORE`）。

- [ ] **Step 1: 写失败测试**

`zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/config/JavaSeedIntegrityTest.java`：

```java
package com.zhixue.ai.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java 种子课与知识资料必须自洽，增量脚本只能「补缺」、且与种子逐字一致。
 *
 * <p>推荐完全依赖这批数据：课程、章节、课时或分片任何一处归属错位，表现都是
 * 「回答正常、推荐永远为空」，从日志很难看出来。</p>
 */
class JavaSeedIntegrityTest {

    /** surefire 的工作目录是模块目录 zhixue-modules/zhixue-ai */
    private static final Path COURSE_SEED = Path.of("../zhixue-course/src/main/resources/sql/course_seed.sql");
    private static final Path COURSE_UPGRADE =
            Path.of("../zhixue-course/src/main/resources/sql/upgrade/2026-10-20-java-courses.sql");
    private static final Path AI_SEED = Path.of("src/main/resources/sql/ai_seed.sql");
    private static final Path AI_UPGRADE = Path.of("src/main/resources/sql/upgrade/2026-10-20-java-knowledge.sql");

    @Test
    void javaCoursesArePublishedOnShelfWithTwoPreviewLessons() throws IOException {
        String seed = read(COURSE_SEED);
        for (long id = 2005; id <= 2007; id++) {
            List<String> f = fields(tuple(seed, id));
            assertThat(f.get(4)).as("课程 %s 的讲师", id).isEqualTo("1002");
            assertThat(f.get(5)).as("课程 %s 的分类", id).isEqualTo("110");
            assertThat(f.get(11)).as("课程 %s 允许试看", id).isEqualTo("1");
            assertThat(f.get(12)).as("课程 %s 试看课时数", id).isEqualTo("2");
            assertThat(f.get(16)).as("课程 %s 已发布", id).isEqualTo("1");
            assertThat(f.get(17)).as("课程 %s 已上架", id).isEqualTo("1");
        }
    }

    @Test
    void everyLessonBelongsToAChapterOfTheSameCourse() throws IOException {
        String seed = read(COURSE_SEED);
        Map<Long, Long> chapterCourse = new HashMap<>();
        for (long id = 3011; id <= 3018; id++) {
            chapterCourse.put(id, Long.parseLong(fields(tuple(seed, id)).get(1)));
        }
        assertThat(chapterCourse.values()).allMatch(c -> c >= 2005 && c <= 2007);

        Map<Long, Integer> lessonsPerCourse = new HashMap<>();
        for (long id = 4011; id <= 4025; id++) {
            List<String> f = fields(tuple(seed, id));
            long course = Long.parseLong(f.get(1));
            long chapter = Long.parseLong(f.get(2));
            assertThat(chapterCourse.get(chapter)).as("课时 %s 的章节属于同一门课", id).isEqualTo(course);
            assertThat(f.get(4)).as("课时 %s 的视频地址可播放", id).startsWith("https://");
            lessonsPerCourse.merge(course, 1, Integer::sum);
        }
        assertThat(lessonsPerCourse).containsEntry(2005L, 5).containsEntry(2006L, 5).containsEntry(2007L, 5);
    }

    @Test
    void knowledgeChunksBelongToJavaCoursesAndAreNotSplit() throws IOException {
        String seed = read(AI_SEED);
        Map<Long, String> docSource = new HashMap<>();
        for (long id = 91011; id <= 91018; id++) {
            List<String> f = fields(tuple(seed, id));
            assertThat(f.get(2)).as("文档 %s 归属 Java 课程", id).matches("course:200[5-7]");
            assertThat(f.get(4)).as("文档 %s 已启用", id).isEqualTo("1");
            docSource.put(id, f.get(2));
        }
        for (long id = 92011; id <= 92018; id++) {
            List<String> f = fields(tuple(seed, id));
            assertThat(docSource).as("分片 %s 的文档存在", id).containsKey(Long.parseLong(f.get(1)));
            // 不超过 TextSplitter 的 500 字阈值：与上传时的切分结果一致，「一篇资料 = 一个分片」
            assertThat(f.get(3).length()).as("分片 %s 的字数", id).isBetween(150, 500);
            assertThat(f.get(4)).as("分片 %s 的 es_id 留空，写入索引后由 AI 服务回填", id).isEqualTo("NULL");
        }
    }

    @Test
    void upgradeScriptsRepeatSeedBlocksAndNeverClearData() throws IOException {
        assertSameBlock(read(COURSE_SEED), read(COURSE_UPGRADE), "java-courses");
        assertSameBlock(read(AI_SEED), read(AI_UPGRADE), "java-knowledge");
        for (Path script : List.of(COURSE_UPGRADE, AI_UPGRADE)) {
            String sql = read(script).toLowerCase();
            assertThat(sql).as("%s 不得清库", script)
                    .doesNotContain("drop table").doesNotContain("truncate").doesNotContain("delete from");
        }
    }

    private static void assertSameBlock(String seed, String upgrade, String name) {
        Matcher m = Pattern.compile("-- BEGIN " + name + "[^\\n]*\\n(.*?)-- END " + name, Pattern.DOTALL).matcher(seed);
        assertThat(m.find()).as("种子中有 %s 段", name).isTrue();
        String expected = m.group(1).replaceAll("(?im)^insert into", "INSERT IGNORE INTO").strip();
        int use = upgrade.indexOf("USE ");
        assertThat(use).as("增量脚本含 USE 语句").isGreaterThanOrEqualTo(0);
        String actual = upgrade.substring(upgrade.indexOf(';', use) + 1).strip();
        assertThat(actual).as("增量脚本与种子 %s 段一致", name).isEqualTo(expected);
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** 取出以 "(id," 开头的整条元组（不含两端括号），正确跳过引号内的逗号与 now() 之类的括号 */
    static String tuple(String sql, long id) {
        Matcher m = Pattern.compile("\\(\\s*" + id + "\\s*,").matcher(sql);
        assertThat(m.find()).as("找到 id=%s 的种子行", id).isTrue();
        int depth = 0;
        boolean quoted = false;
        for (int i = m.start(); i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                if (quoted && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')' && --depth == 0) {
                return sql.substring(m.start() + 1, i);
            }
        }
        throw new AssertionError("id=" + id + " 的种子行没有闭合");
    }

    /** 按顶层逗号切分字段，去掉字符串两端的引号 */
    static List<String> fields(String tuple) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < tuple.length(); i++) {
            char c = tuple.charAt(i);
            if (c == '\'') {
                if (quoted && i + 1 < tuple.length() && tuple.charAt(i + 1) == '\'') {
                    current.append('\'');
                    i++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')') {
                depth--;
            }
            if (!quoted && depth == 0 && c == ',') {
                out.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        out.add(current.toString().trim());
        return out;
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
cd /Users/jax/Projects/zhixue-cloud
mvn test -q -pl zhixue-modules/zhixue-ai -Dtest=JavaSeedIntegrityTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "Tests run:|JavaSeedIntegrityTest\.[a-zA-Z]+" | sort -u | head
```

Expected: 4 项失败（找不到 id=2005 的种子行、增量脚本不存在等）。

- [ ] **Step 3: 在 `course_seed.sql` 末尾追加 `java-courses` 段**

```sql

-- BEGIN java-courses（upgrade/2026-10-20-java-courses.sql 由本段生成，二者由 JavaSeedIntegrityTest 保证一致）
insert into course (
    id, title, subtitle, description, teacher_id, category_id, cover_url, poster_url,
    price, original_price, package_type, allow_preview, preview_lesson_count, validity_type, materials, faq,
    status, shelf_status, publish_time, create_time, update_time, deleted
) values
    (2005, 'Java 编程基础', '从语法到面向对象，打牢 Java 根基', '覆盖数据类型、面向对象三大特性、异常体系与常用类。', 1002, 110,
     'https://dummyimage.com/600x340/1d4ed8/ffffff&text=Java+Basic', 'https://dummyimage.com/1200x480/1d4ed8/ffffff&text=Java+Basic',
     39.00, 79.00, 1, 1, 2, 1, '示例代码, 课后练习', '零基础或从其他语言转 Java 的同学', 1, 1, now(), now(), now(), 0),
    (2006, 'Java 集合与泛型', 'HashMap 从底层结构到扩容与树化', '讲透 List、Set、Map 的实现差异，重点剖析 HashMap，并解释泛型与类型擦除。', 1002, 110,
     'https://dummyimage.com/600x340/0f766e/ffffff&text=Java+Collections', 'https://dummyimage.com/1200x480/0f766e/ffffff&text=Java+Collections',
     59.00, 99.00, 1, 1, 2, 1, '源码注释, 面试题清单', '学过 Java 基础、需要深入集合实现的同学', 1, 1, now(), now(), now(), 0),
    (2007, 'Java 并发编程实战', '线程安全、锁、并发容器与线程池', '从线程安全三要素讲到锁、ConcurrentHashMap 原理与线程池参数配置。', 1002, 110,
     'https://dummyimage.com/600x340/b91c1c/ffffff&text=Java+Concurrency', 'https://dummyimage.com/1200x480/b91c1c/ffffff&text=Java+Concurrency',
     99.00, 159.00, 1, 1, 2, 1, '并发案例代码, 调优清单', '有 Java 基础、需要处理并发的后端工程师', 1, 1, now(), now(), now(), 0);

insert into chapter (id, course_id, title, order_num, create_time, update_time, deleted) values
    (3011, 2005, '语法与面向对象', 1, now(), now(), 0),
    (3012, 2005, '异常与常用类', 2, now(), now(), 0),
    (3013, 2006, '集合框架', 1, now(), now(), 0),
    (3014, 2006, 'HashMap 深入', 2, now(), now(), 0),
    (3015, 2006, '泛型', 3, now(), now(), 0),
    (3016, 2007, '线程基础', 1, now(), now(), 0),
    (3017, 2007, '锁与并发容器', 2, now(), now(), 0),
    (3018, 2007, '线程池', 3, now(), now(), 0);

insert into section (id, course_id, chapter_id, title, video_url, duration, order_num, status, create_time, update_time, deleted) values
    (4011, 2005, 3011, '数据类型与变量', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 720, 1, 1, now(), now(), 0),
    (4012, 2005, 3011, '类、对象与封装', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 1080, 2, 1, now(), now(), 0),
    (4013, 2005, 3011, '继承、多态与接口', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 1200, 3, 1, now(), now(), 0),
    (4014, 2005, 3012, '异常体系与 try-with-resources', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 900, 1, 1, now(), now(), 0),
    (4015, 2005, 3012, 'String、StringBuilder 与不可变性', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 840, 2, 1, now(), now(), 0),
    (4016, 2006, 3013, '集合框架总览：List、Set、Map', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 840, 1, 1, now(), now(), 0),
    (4017, 2006, 3013, 'ArrayList 与 LinkedList 的取舍', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 960, 2, 1, now(), now(), 0),
    (4018, 2006, 3014, 'HashMap 底层结构与 hash 计算', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 1200, 1, 1, now(), now(), 0),
    (4019, 2006, 3014, 'HashMap 扩容机制与树化', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 1080, 2, 1, now(), now(), 0),
    (4020, 2006, 3015, '泛型与类型擦除', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 1020, 1, 1, now(), now(), 0),
    (4021, 2007, 3016, '线程的创建与生命周期', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 900, 1, 1, now(), now(), 0),
    (4022, 2007, 3016, '线程安全问题的根源：可见性、原子性、有序性', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 1140, 2, 1, now(), now(), 0),
    (4023, 2007, 3017, 'synchronized 与 ReentrantLock', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 1260, 1, 1, now(), now(), 0),
    (4024, 2007, 3017, 'ConcurrentHashMap 原理', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 1080, 2, 1, now(), now(), 0),
    (4025, 2007, 3018, '线程池核心参数与拒绝策略', 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4', 1320, 1, 1, now(), now(), 0);
-- END java-courses
```

- [ ] **Step 4: 在 `ai_seed.sql` 末尾追加 `java-knowledge` 段**

```sql

-- BEGIN java-knowledge（upgrade/2026-10-20-java-knowledge.sql 由本段生成，二者由 JavaSeedIntegrityTest 保证一致）
INSERT INTO kb_document
    (id, title, source, tags, status, create_time, update_time, deleted)
VALUES
    (91011, 'Java 编程基础 · 语法与面向对象', 'course:2005', 'java,基础,面向对象', 1, NOW(), NOW(), 0),
    (91012, 'Java 编程基础 · 异常与常用类', 'course:2005', 'java,异常,String', 1, NOW(), NOW(), 0),
    (91013, 'Java 集合与泛型 · 集合框架', 'course:2006', 'java,集合,ArrayList', 1, NOW(), NOW(), 0),
    (91014, 'Java 集合与泛型 · HashMap 深入', 'course:2006', 'java,HashMap,扩容', 1, NOW(), NOW(), 0),
    (91015, 'Java 集合与泛型 · 泛型', 'course:2006', 'java,泛型,类型擦除', 1, NOW(), NOW(), 0),
    (91016, 'Java 并发编程实战 · 线程基础', 'course:2007', 'java,线程,线程安全', 1, NOW(), NOW(), 0),
    (91017, 'Java 并发编程实战 · 锁与并发容器', 'course:2007', 'java,锁,ConcurrentHashMap', 1, NOW(), NOW(), 0),
    (91018, 'Java 并发编程实战 · 线程池', 'course:2007', 'java,线程池,拒绝策略', 1, NOW(), NOW(), 0);

INSERT INTO kb_chunk
    (id, document_id, chunk_index, content, es_id, create_time, update_time, deleted)
VALUES
    (92011, 91011, 0, 'Java 是面向对象的静态类型语言。基本类型（int、long、double、boolean 等）直接存值，包装类型（Integer 等）是对象，二者之间的自动装箱拆箱由编译器完成。类用 class 定义，字段私有、通过方法访问称为封装；继承用 extends，子类可以重写父类方法；多态指父类引用指向子类对象，运行时按实际类型调用方法；接口用 interface 定义行为约定，一个类可以实现多个接口。方法重载看参数列表，重写要求方法签名相同。构造方法不会被继承，子类构造方法默认先调用父类的无参构造。', NULL, NOW(), NOW(), 0),
    (92012, 91012, 0, 'String 是不可变的：String 类被 final 修饰，不能被继承改写；存放字符的数组（JDK 9 起为 private final byte[] value，JDK 8 为 char[]）私有且不对外暴露，concat、replace、substring 等方法不修改原串，结果不同时返回新对象。final 只保证引用不变，不可变靠不暴露数组、不提供修改方法。因此 hashCode 可以缓存，适合作 HashMap 的键；可在线程间安全共享；常量池可复用同一对象。频繁拼接用 StringBuilder。受检异常（如 IOException）必须捕获或声明抛出，运行时异常（如 NullPointerException）不强制处理，Error 不应捕获。finally 在 try 或 catch 正常结束、return 或抛异常时都会执行，但 System.exit()、JVM 崩溃或进程被强制终止时不会执行。try-with-resources 自动关闭 AutoCloseable 资源；若 try 块已抛异常，关闭时的异常作为 suppressed 附加在主异常上。', NULL, NOW(), NOW(), 0),
    (92013, 91013, 0, 'Java 集合分为 Collection 和 Map 两大支。Collection 下有 List（有序可重复，如 ArrayList、LinkedList）和 Set（不重复，如基于 HashMap 的 HashSet、有序的 TreeSet）；Map 存放键值对。ArrayList 底层是动态数组，随机访问 O(1)，默认容量 10，扩容为原来的 1.5 倍；LinkedList 是双向链表，头尾增删 O(1)，随机访问需要遍历，绝大多数场景选 ArrayList 更合适。用 for-each 或迭代器遍历时直接调用集合的 add 或 remove，通常会抛 ConcurrentModificationException（fail-fast 机制），应改用迭代器自己的 remove 方法。', NULL, NOW(), NOW(), 0),
    (92014, 91014, 0, 'HashMap 底层是数组加链表加红黑树。put 时先把 key 的 hashCode 高 16 位与低 16 位异或得到 hash，再用 (n-1) & hash 定位桶；容量总是 2 的幂，这等价于取模但更快。链表长度超过 8 且数组容量不小于 64 时转为红黑树，容量不足 64 时优先扩容。默认容量 16、负载因子 0.75，元素个数超过阈值就扩容为原来的 2 倍，节点按高低位拆分到新数组。HashMap 不是线程安全的：两个线程同时判断某个桶为空后先后写入，后写的会覆盖先写的，导致数据丢失；++size 不是原子操作，元素个数会算错；JDK 7 用头插法，并发扩容时还可能形成环形链表导致死循环，JDK 8 改为尾插解决了成环，但丢数据的问题依然存在。多线程场景应使用 ConcurrentHashMap。作为键的对象必须正确重写 equals 和 hashCode。', NULL, NOW(), NOW(), 0),
    (92015, 91015, 0, '泛型让集合在编译期完成类型检查，避免运行时的强制转换错误。Java 泛型通过类型擦除实现：编译后类型参数被替换为上界（无界时为 Object），因此运行时无法从对象本身拿到真实的类型参数（类、字段和方法签名上声明的泛型可以通过反射读取），不能 new T()、不能创建泛型数组，也不能用 instanceof 判断泛型类型。通配符 extends 表示类型的上界，适合只读取的生产者；super 表示下界，适合只写入的消费者，口诀是 PECS。泛型方法在返回类型前声明类型参数，静态方法不能使用类上声明的类型参数。', NULL, NOW(), NOW(), 0),
    (92016, 91016, 0, '创建线程有三种方式：继承 Thread 并重写 run、实现 Runnable 交给 Thread、实现 Callable 配合 FutureTask 获取返回值；实际项目应使用线程池。sleep 不释放锁；wait 必须在持有该对象监视器时调用，会释放锁，直到被 notify/notifyAll 唤醒、等待超时或被中断；wait 应放在 while 循环里检查条件，防止虚假唤醒。线程安全问题有三个根源：原子性（i++ 实际是读、改、写三步，会被打断）、可见性（一个线程的修改另一个线程不一定立即看到）、有序性（编译器和处理器会重排指令）。volatile 保证可见性并禁止重排，但不保证原子性；synchronized、Lock 和 AtomicInteger 才能保证原子性。', NULL, NOW(), NOW(), 0),
    (92017, 91017, 0, 'synchronized 是 JVM 内置的可重入锁，修饰实例方法时锁当前对象，修饰静态方法时锁 Class 对象。JDK 6 引入了偏向锁、轻量级锁到重量级锁的锁升级，JDK 15 起偏向锁默认关闭并已废弃。ReentrantLock 需要在 finally 中手动 unlock，额外提供公平锁、tryLock 超时、可中断加锁和多个 Condition。HashMap 不是线程安全的，多线程场景应改用 ConcurrentHashMap：JDK 7 用分段锁；JDK 8 改为数组加链表加红黑树，空桶用 CAS 写入，非空桶只锁桶的头节点，锁粒度细到单个桶，扩容时多个线程可以协助迁移。它不允许 null 键和 null 值，读操作基本不加锁；「先判断再写入」这类复合操作要用 putIfAbsent、computeIfAbsent 等原子方法，否则依然有并发问题。', NULL, NOW(), NOW(), 0),
    (92018, 91018, 0, 'ThreadPoolExecutor 有 7 个参数：corePoolSize 核心线程数、maximumPoolSize 最大线程数、keepAliveTime 与 unit 决定非核心线程空闲多久被回收、workQueue 任务队列、threadFactory 线程工厂（用来给线程命名，便于排查）、handler 拒绝策略。任务提交顺序：核心线程未满就新建核心线程；满了放进队列；队列满了再新建非核心线程，直到最大线程数；仍然满就执行拒绝策略。四种拒绝策略：AbortPolicy 抛异常（默认）、CallerRunsPolicy 由提交任务的线程执行、DiscardPolicy 静默丢弃、DiscardOldestPolicy 丢弃最早的任务。参数怎么设：CPU 密集型线程数约为核数加 1，IO 密集型可按核数乘以（1 + 等待时间/计算时间）估算；使用有界队列，再按压测结果调整。newFixedThreadPool 用无界队列，任务堆积会导致内存溢出，生产环境应直接构造 ThreadPoolExecutor。', NULL, NOW(), NOW(), 0);
-- END java-knowledge
```

- [ ] **Step 5: 由种子段生成两份增量脚本**

```bash
cd /Users/jax/Projects/zhixue-cloud
python3 - <<'PY'
import pathlib, re

def block(seed_path, name):
    text = pathlib.Path(seed_path).read_text(encoding="utf-8")
    m = re.search(r"-- BEGIN %s[^\n]*\n(.*?)-- END %s" % (name, name), text, re.S)
    assert m, f"{seed_path} 缺少 {name} 段"
    return re.sub(r"(?im)^insert into", "INSERT IGNORE INTO", m.group(1)).strip() + "\n"

def write(path, header, use, body):
    p = pathlib.Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(header + use + "\n\n" + body, encoding="utf-8")

course = "zhixue-modules/zhixue-course/src/main/resources/sql/"
ai = "zhixue-modules/zhixue-ai/src/main/resources/sql/"
write(course + "upgrade/2026-10-20-java-courses.sql",
      "-- 已有数据的环境追加 3 门 Java 种子课。可重复执行：只插入缺失的行。\n"
      "-- 不得用 db-init.sh（会清空全库）。本文件由 course_seed.sql 的 java-courses 段生成，勿手改。\n",
      "USE zhixue_course;", block(course + "course_seed.sql", "java-courses"))
write(ai + "upgrade/2026-10-20-java-knowledge.sql",
      "-- 已有数据的环境追加 8 篇 Java 知识。可重复执行：只插入缺失的行。\n"
      "-- 只写 MySQL；AI 服务启动时会把 es_id 为空的分片补进向量索引。\n"
      "-- 本文件由 ai_seed.sql 的 java-knowledge 段生成，勿手改。\n",
      "USE zhixue_ai;", block(ai + "ai_seed.sql", "java-knowledge"))
print("ok")
PY
```

Expected: 输出 `ok`，生成两个文件。

- [ ] **Step 6: 运行确认通过**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -Dtest=JavaSeedIntegrityTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -3
```

Expected: `BUILD SUCCESS`。若分片字数断言失败，删减该分片中的举例句，保持在 500 字以内，然后重新执行 Step 5 再运行测试。

- [ ] **Step 7: 在本机用增量脚本上线，并验证可重复执行**

```bash
cd /Users/jax/Projects/zhixue-cloud && source scripts/local/common.sh && load_env
for i in 1 2; do
  docker exec -i zhixue-local-mysql mysql --default-character-set=utf8mb4 -uroot -p"$ZHIXUE_DB_PASSWORD" \
    < zhixue-modules/zhixue-course/src/main/resources/sql/upgrade/2026-10-20-java-courses.sql 2>&1 | grep -v Warning
  docker exec -i zhixue-local-mysql mysql --default-character-set=utf8mb4 -uroot -p"$ZHIXUE_DB_PASSWORD" \
    < zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-20-java-knowledge.sql 2>&1 | grep -v Warning
done
docker exec zhixue-local-mysql mysql --default-character-set=utf8mb4 -uroot -p"$ZHIXUE_DB_PASSWORD" -N -e "
  SELECT COUNT(*) FROM zhixue_course.course WHERE id BETWEEN 2005 AND 2007;
  SELECT COUNT(*) FROM zhixue_course.section WHERE id BETWEEN 4011 AND 4025;
  SELECT COUNT(*) FROM zhixue_ai.kb_chunk WHERE id BETWEEN 92011 AND 92018;
  SELECT COUNT(*) FROM zhixue_course.course;" 2>&1 | grep -v Warning
```

Expected: 两次执行都不报错；依次输出 `3`、`15`、`8`，以及课程总数（原有课程未受影响）。

- [ ] **Step 8: 提交**

```bash
git add zhixue-modules/zhixue-course/src/main/resources/sql zhixue-modules/zhixue-ai/src
git commit -m "feat(course,ai): 补 3 门 Java 种子课与 8 篇知识资料，附由种子生成、可重复执行的增量脚本"
```

> **执行记录（任务审查后修订）**：知识资料按审查意见修正了事实错误（finally 并非总会执行、JDK 15 起偏向锁默认关闭、链表长度超过 8 才树化、String 类被 final 修饰且 JDK 9 起为 `byte[]` 等），上方 Step 4 已同步为修订后的文本；测试另增 3 项守护：每篇资料恰好一个分片、新 ID 唯一、增量脚本只允许 `USE` 与 `INSERT IGNORE`（拒绝 `REPLACE`、`ON DUPLICATE KEY`、同行多语句）。

---

### Task 2: 启动时补齐未入索引的分片

**Files:**
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/service/KnowledgeBaseService.java`
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/service/impl/KnowledgeBaseServiceImpl.java`
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/config/KnowledgeIndexInitializer.java`（`ensureIndex()` 中维度一致的分支）
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/service/impl/KnowledgeBackfillTest.java`
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/config/KnowledgeIndexInitializerTest.java`（追加 1 项）

**Interfaces:**
- Produces: `int KnowledgeBaseService.indexPending()`：把 `es_id IS NULL` 且所属文档启用的分片写入索引并回填 `es_id`，返回写入数。

- [ ] **Step 1: 写失败测试（服务层）**

`KnowledgeBackfillTest.java`：

```java
package com.zhixue.ai.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.ai.domain.entity.KnowledgeChunk;
import com.zhixue.ai.domain.entity.KnowledgeDocument;
import com.zhixue.ai.mapper.KnowledgeChunkMapper;
import com.zhixue.ai.mapper.KnowledgeDocumentMapper;
import com.zhixue.ai.rag.TextSplitter;
import com.zhixue.ai.service.VectorStoreService;
import com.zhixue.ai.service.VectorStoreService.ChunkDoc;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 增量脚本只写 MySQL；索引已存在且维度一致时启动流程原本直接跳过，
 * 新资料于是永远进不了向量索引、永远检索不到。
 */
class KnowledgeBackfillTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KnowledgeChunk.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
    }

    private final KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
    private final KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
    private final VectorStoreService vectorStoreService = mock(VectorStoreService.class);
    private final KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
            documentMapper, chunkMapper, mock(TextSplitter.class), vectorStoreService);

    @Test
    @SuppressWarnings("unchecked")
    void indexesOnlyChunksOfEnabledDocumentsAndMarksThem() {
        when(chunkMapper.selectList(any())).thenReturn(List.of(
                chunk(92014L, 91014L, "HashMap 扩容"), chunk(92099L, 91099L, "停用文档的分片")));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of(
                document(91014L, "course:2006", 1), document(91099L, "course:2006", 0)));
        when(vectorStoreService.indexChunks(anyList())).thenReturn(1);

        assertThat(service.indexPending()).isEqualTo(1);

        ArgumentCaptor<List<ChunkDoc>> batch = ArgumentCaptor.forClass(List.class);
        verify(vectorStoreService).indexChunks(batch.capture());
        assertThat(batch.getValue()).extracting(ChunkDoc::chunkId).containsExactly(92014L);
        assertThat(batch.getValue().get(0).courseId()).isEqualTo(2006L);
        ArgumentCaptor<KnowledgeChunk> marked = ArgumentCaptor.forClass(KnowledgeChunk.class);
        verify(chunkMapper).updateById(marked.capture());
        assertThat(marked.getValue().getEsId()).isEqualTo("92014");
    }

    @Test
    void doesNothingWhenEveryChunkIsIndexed() {
        when(chunkMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.indexPending()).isZero();
        verify(vectorStoreService, never()).indexChunks(anyList());
    }

    private static KnowledgeChunk chunk(long id, long documentId, String content) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setChunkIndex(0);
        chunk.setContent(content);
        return chunk;
    }

    private static KnowledgeDocument document(long id, String source, int status) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(id);
        doc.setTitle("资料 " + id);
        doc.setSource(source);
        doc.setStatus(status);
        return doc;
    }
}
```

- [ ] **Step 2: 在 `KnowledgeIndexInitializerTest` 追加一项**

放在 `keepsExistingIndexWithMatchingDimension()` 之后：

```java
    @Test
    void existingNonEmptyIndexBackfillsChunksMissingFromIt() throws Exception {
        es(Map.of(
                "GET /zhixue_kb/_mapping", response(200, mapping(1024)),
                "GET /zhixue_kb/_count", response(200, "{\"count\":9}")));

        initializer.ensureIndex();

        // 不重建（避免每次重启都清空），但要把增量脚本新写入 MySQL 的分片补进索引
        verify(knowledgeBaseService, never()).reindexAll();
        verify(knowledgeBaseService).indexPending();
    }
```

- [ ] **Step 3: 运行确认失败**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -Dtest='KnowledgeBackfillTest,KnowledgeIndexInitializerTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "cannot find symbol|找不到符号" | head -3
```

Expected: 编译失败（`indexPending` 不存在）。

- [ ] **Step 4: 实现**

`KnowledgeBaseService` 追加：

```java
    /**
     * 把数据库中存在、但还没写入向量索引（{@code es_id} 为空）且所属文档启用的分片补写进索引。
     *
     * <p>增量脚本只写 MySQL；索引已存在时启动流程不重建，不补齐的话新资料永远检索不到。
     * 分片以 chunkId 作 ES 文档 ID，重复执行没有副作用。</p>
     *
     * @return 本次写入向量索引的分片数
     */
    int indexPending();
```

`KnowledgeBaseServiceImpl` 追加：

```java
    @Override
    public int indexPending() {
        List<KnowledgeChunk> pending = chunkMapper.selectList(new LambdaQueryWrapper<KnowledgeChunk>()
                .isNull(KnowledgeChunk::getEsId)
                .orderByAsc(KnowledgeChunk::getDocumentId)
                .orderByAsc(KnowledgeChunk::getChunkIndex));
        if (pending.isEmpty()) {
            return 0;
        }
        Map<Long, KnowledgeDocument> enabled = documentMapper
                .selectBatchIds(pending.stream().map(KnowledgeChunk::getDocumentId).distinct().toList())
                .stream()
                .filter(doc -> Integer.valueOf(1).equals(doc.getStatus()))
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        List<ChunkDoc> docs = pending.stream()
                .filter(c -> enabled.containsKey(c.getDocumentId()))
                .map(c -> new ChunkDoc(c.getId(), c.getDocumentId(),
                        KnowledgeBaseService.courseIdOf(enabled.get(c.getDocumentId()).getSource()), c.getContent()))
                .toList();
        int indexed = 0;
        for (int from = 0; from < docs.size(); from += REINDEX_BATCH) {
            List<ChunkDoc> batch = docs.subList(from, Math.min(from + REINDEX_BATCH, docs.size()));
            int n = vectorStoreService.indexChunks(batch);
            markIndexed(batch, n);
            indexed += n;
        }
        log.info("补齐向量索引：待补分片 {} 个（启用文档 {} 个），写入 {} 个", pending.size(), docs.size(), indexed);
        return indexed;
    }
```

`KnowledgeIndexInitializer.ensureIndex()` 中把维度一致的分支替换为：

```java
            if (existing == dimension) {
                if (documentCount() == 0) {
                    log.info("知识库索引为空，从数据库写入分片 index={}", INDEX);
                    knowledgeBaseService.reindexAll();
                } else {
                    // 已有数据的环境只能跑增量脚本（只写 MySQL），这里把新分片补进索引
                    int filled = knowledgeBaseService.indexPending();
                    log.info("知识库索引已存在且维度一致 index={} dims={}，补齐分片 {} 个", INDEX, dimension, filled);
                }
                return;
            }
```

- [ ] **Step 5: 运行确认通过**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -3
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 6: 提交**

```bash
git add zhixue-modules/zhixue-ai/src
git commit -m "fix(ai): 索引已存在时补齐 es_id 为空的分片，否则增量资料永远检索不到"
```

---

### Task 3: 带余弦分数的检索与资料解析

**Files:**
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/service/VectorStoreService.java`
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/service/impl/VectorStoreServiceImpl.java`
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/service/KnowledgeBaseService.java`
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/service/impl/KnowledgeBaseServiceImpl.java`
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/service/impl/ScoredSearchTest.java`

**Interfaces:**
- Produces:
  - `record VectorStoreService.ScoredChunk(Long chunkId, Long courseId, double cosine)`
  - `List<ScoredChunk> VectorStoreService.searchWithScores(String query, List<Float> queryVector, int topK, Long courseId)`：`queryVector` 为空时退回词法匹配，`cosine` 记 0
  - `record KnowledgeBaseService.RetrievedChunk(Long chunkId, Long courseId, double cosine, String title, String content)`：`courseId` 以数据库中文档的 `source` 为准
  - `List<RetrievedChunk> KnowledgeBaseService.retrieveScored(String query, List<Float> queryVector, int topK, Long courseId)`：保持命中顺序，剔除停用/删除文档

- [ ] **Step 1: 写失败测试**

`ScoredSearchTest.java`：

```java
package com.zhixue.ai.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.ai.domain.entity.KnowledgeChunk;
import com.zhixue.ai.domain.entity.KnowledgeDocument;
import com.zhixue.ai.mapper.KnowledgeChunkMapper;
import com.zhixue.ai.mapper.KnowledgeDocumentMapper;
import com.zhixue.ai.rag.TextSplitter;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.ai.service.VectorStoreService;
import com.zhixue.ai.service.VectorStoreService.ChunkDoc;
import com.zhixue.ai.service.VectorStoreService.ScoredChunk;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 检索分数必须与门槛同一刻度：索引按 cosine 建立时 ES 的 _score = (1 + cos) / 2，
 * 而 kNN 的 similarity 参数与推荐门槛都是原始余弦。直接拿 _score 比 0.55，
 * 「今天中午吃什么」（_score 0.64–0.66，余弦 0.28–0.32）也会推荐出课程。
 */
class ScoredSearchTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, KnowledgeChunk.class);
        TableInfoHelper.initTableInfo(assistant, KnowledgeDocument.class);
    }

    private final RestClient restClient = mock(RestClient.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);

    private VectorStoreServiceImpl vectorStore(String mode) {
        VectorStoreServiceImpl service = new VectorStoreServiceImpl(restClient, embeddingService);
        ReflectionTestUtils.setField(service, "aiMode", mode);
        ReflectionTestUtils.setField(service, "minSimilarity", 0.45);
        return service;
    }

    @Test
    void convertsEsScoreToCosineAndKeepsCourseOfEachHit() throws Exception {
        Response response = mock(Response.class);
        when(response.getEntity()).thenReturn(new StringEntity("""
                {"hits":{"hits":[
                  {"_score":0.66,"_source":{"chunkId":92018,"courseId":2007}},
                  {"_score":0.80,"_source":{"chunkId":92009}},
                  {"_score":0.70,"_source":{"chunkId":92018,"courseId":2007}}
                ]}}""", ContentType.APPLICATION_JSON));
        when(restClient.performRequest(any(Request.class))).thenReturn(response);

        List<ScoredChunk> hits = vectorStore("real").searchWithScores("线程池", List.of(1f, 0f), 8, 2007L);

        assertThat(hits).extracting(ScoredChunk::chunkId).containsExactly(92018L, 92009L); // 同一分片只保留一次
        assertThat(hits.get(0).courseId()).isEqualTo(2007L);
        assertThat(hits.get(0).cosine()).isCloseTo(0.32, within(1e-9));
        assertThat(hits.get(1).courseId()).isNull();
        assertThat(hits.get(1).cosine()).isCloseTo(0.60, within(1e-9));

        ArgumentCaptor<Request> request = ArgumentCaptor.forClass(Request.class);
        verify(restClient).performRequest(request.capture());
        String body = new String(request.getValue().getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(body).contains("\"similarity\":0.45").contains("\"query_vector\":[1.0,0.0]")
                .contains("\"term\":{\"courseId\":2007}").contains("\"courseId\"");
        // 向量由调用方算好传入，这里不能再向量化一次
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void emptyVectorFallsBackToLexicalWithZeroCosine() {
        when(embeddingService.embedBatch(any())).thenReturn(List.of());
        VectorStoreServiceImpl service = vectorStore("sandbox");
        service.indexChunks(List.of(new ChunkDoc(1L, 10L, 2006L, "HashMap 为什么线程不安全")));

        List<ScoredChunk> hits = service.searchWithScores("HashMap", List.of(), 5, null);

        assertThat(hits).containsExactly(new ScoredChunk(1L, 2006L, 0.0));
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void retrieveScoredKeepsOrderUsesDatabaseCourseAndDropsDisabledDocuments() {
        KnowledgeDocumentMapper documentMapper = mock(KnowledgeDocumentMapper.class);
        KnowledgeChunkMapper chunkMapper = mock(KnowledgeChunkMapper.class);
        VectorStoreService vectorStore = mock(VectorStoreService.class);
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
                documentMapper, chunkMapper, mock(TextSplitter.class), vectorStore);
        when(vectorStore.searchWithScores(eq("HashMap"), any(), eq(8), isNull())).thenReturn(List.of(
                new ScoredChunk(92014L, 2006L, 0.71),
                new ScoredChunk(92099L, 2006L, 0.66),
                new ScoredChunk(92018L, 2007L, 0.58)));
        when(chunkMapper.selectBatchIds(any())).thenReturn(List.of(
                chunk(92014L, 91014L), chunk(92099L, 91099L), chunk(92018L, 91018L)));
        when(documentMapper.selectBatchIds(any())).thenReturn(List.of(
                document(91014L, "Java 集合与泛型 · HashMap 深入", "course:2006", 1),
                document(91099L, "已停用资料", "course:2006", 0),
                document(91018L, "Java 并发编程实战 · 线程池", "course:2007", 1)));

        List<RetrievedChunk> hits = service.retrieveScored("HashMap", List.of(1f), 8, null);

        assertThat(hits).extracting(RetrievedChunk::chunkId).containsExactly(92014L, 92018L);
        assertThat(hits).extracting(RetrievedChunk::courseId).containsExactly(2006L, 2007L);
        assertThat(hits).extracting(RetrievedChunk::cosine).containsExactly(0.71, 0.58);
        assertThat(hits.get(0).title()).isEqualTo("Java 集合与泛型 · HashMap 深入");
        assertThat(hits.get(0).content()).isEqualTo("内容 92014");
    }

    private static KnowledgeChunk chunk(long id, long documentId) {
        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setContent("内容 " + id);
        return chunk;
    }

    private static KnowledgeDocument document(long id, String title, String source, int status) {
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(id);
        doc.setTitle(title);
        doc.setSource(source);
        doc.setStatus(status);
        return doc;
    }
}
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -Dtest=ScoredSearchTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "cannot find symbol|找不到符号" | head -3
```

Expected: 编译失败。

- [ ] **Step 3: 实现向量存储**

`VectorStoreService` 追加：

```java
    /**
     * 带余弦分数的命中。
     *
     * @param courseId 分片所属课程（ES 中的值）；null 表示通用资料
     * @param cosine   余弦相似度，与 ai.rag.min-similarity、推荐门槛同一刻度
     */
    record ScoredChunk(Long chunkId, Long courseId, double cosine) {
    }

    /**
     * 用已算好的问句向量检索，返回余弦分数。问句只向量化一次，供回答与课时推荐复用。
     *
     * <p>索引按 cosine 建立时 ES 的 {@code _score} = (1 + cos) / 2，这里换算回 cos = 2 × _score − 1。</p>
     *
     * @param query       问句原文，仅在 queryVector 为空（sandbox 取不到向量）时用于词法降级
     * @param queryVector 问句向量
     * @return 按相关度降序，同一分片只出现一次；词法降级时 cosine 为 0
     */
    List<ScoredChunk> searchWithScores(String query, List<Float> queryVector, int topK, Long courseId);
```

`VectorStoreServiceImpl`：先把 `search(...)` 中构造 kNN 子句的代码抽成私有方法，`search` 与新方法共用（`search` 行为不变）：

```java
    private Map<String, Object> knnClause(List<Float> vector, int topK, Long courseId) {
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", "vector");
        knn.put("query_vector", vector);
        knn.put("k", topK);
        knn.put("num_candidates", Math.max(50, topK * 10));
        knn.put("similarity", minSimilarity);
        if (courseId != null) {
            // 本课程的资料 + 不属于任何课程的通用资料
            knn.put("filter", Map.of("bool", Map.of(
                    "should", List.of(
                            Map.of("term", Map.of("courseId", courseId)),
                            Map.of("bool", Map.of("must_not", Map.of("exists", Map.of("field", "courseId"))))),
                    "minimum_should_match", 1)));
        }
        return knn;
    }
```

`search(...)` 里原先构造 `knn` 的 13 行替换为 `Map<String, Object> knn = knnClause(vec, topK, courseId);`。然后追加：

```java
    @Override
    public List<ScoredChunk> searchWithScores(String query, List<Float> queryVector, int topK, Long courseId) {
        if (!StringUtils.hasText(query) || topK <= 0) {
            return List.of();
        }
        if (isStubMode() || queryVector == null || queryVector.isEmpty()) {
            return lexicalScored(query, topK, courseId);
        }
        try {
            Request req = new Request("POST", "/" + INDEX + "/_search");
            req.setJsonEntity(objectMapper.writeValueAsString(Map.of(
                    "knn", knnClause(queryVector, topK, courseId),
                    "_source", List.of("chunkId", "courseId"))));
            JsonNode hits = objectMapper.readTree(restClient.performRequest(req).getEntity().getContent())
                    .path("hits").path("hits");
            Map<Long, ScoredChunk> unique = new LinkedHashMap<>();
            for (JsonNode hit : hits) {
                JsonNode source = hit.path("_source");
                long chunkId = source.path("chunkId").asLong();
                Long hitCourse = source.hasNonNull("courseId") ? source.path("courseId").asLong() : null;
                unique.putIfAbsent(chunkId, new ScoredChunk(chunkId, hitCourse, 2 * hit.path("_score").asDouble() - 1));
            }
            return new ArrayList<>(unique.values());
        } catch (Exception e) {
            return fail("知识库检索失败", e, lexicalScored(query, topK, courseId));
        }
    }

    /** 词法降级：没有可比的相似度，cosine 记 0，调用方据此不会出推荐 */
    private List<ScoredChunk> lexicalScored(String query, int topK, Long courseId) {
        return localSearch(query, topK, courseId).stream()
                .map(id -> new ScoredChunk(id, localChunks.get(id).courseId(), 0.0))
                .toList();
    }
```

- [ ] **Step 4: 实现资料解析**

`KnowledgeBaseService` 追加：

```java
    /**
     * 一条检索命中。
     *
     * @param courseId 以数据库中文档的 source 为准（course:{id}）；null 表示通用资料
     * @param title    文档标题，即回答里「参考资料」显示的文字
     */
    record RetrievedChunk(Long chunkId, Long courseId, double cosine, String title, String content) {
    }

    /**
     * 用已算好的问句向量检索资料并带上余弦分数；回答与推荐共用这一次结果。
     * 已停用或已删除文档的分片会被剔除，顺序与命中顺序一致。
     */
    List<RetrievedChunk> retrieveScored(String query, List<Float> queryVector, int topK, Long courseId);
```

`KnowledgeBaseServiceImpl` 追加（`import com.zhixue.ai.service.VectorStoreService.ScoredChunk;`）：

```java
    @Override
    public List<RetrievedChunk> retrieveScored(String query, List<Float> queryVector, int topK, Long courseId) {
        List<ScoredChunk> hits = vectorStoreService.searchWithScores(query, queryVector, topK, courseId);
        if (hits.isEmpty()) {
            return List.of();
        }
        Map<Long, KnowledgeChunk> chunks = chunkMapper.selectBatchIds(hits.stream().map(ScoredChunk::chunkId).toList())
                .stream()
                .collect(Collectors.toMap(KnowledgeChunk::getId, Function.identity()));
        Map<Long, KnowledgeDocument> docs = chunks.isEmpty() ? Map.of() : documentMapper
                .selectBatchIds(chunks.values().stream().map(KnowledgeChunk::getDocumentId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, Function.identity()));
        List<RetrievedChunk> result = new ArrayList<>();
        for (ScoredChunk hit : hits) {
            KnowledgeChunk chunk = chunks.get(hit.chunkId());
            KnowledgeDocument doc = chunk == null ? null : docs.get(chunk.getDocumentId());
            if (doc != null && Integer.valueOf(1).equals(doc.getStatus())) {
                result.add(new RetrievedChunk(hit.chunkId(), KnowledgeBaseService.courseIdOf(doc.getSource()),
                        hit.cosine(), doc.getTitle(), chunk.getContent()));
            }
        }
        return result;
    }
```

- [ ] **Step 5: 运行确认通过**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -3
```

Expected: `BUILD SUCCESS`（包括原有 `VectorStoreServiceImplTest`，证明 `search` 抽取方法后行为不变）。

- [ ] **Step 6: 提交**

```bash
git add zhixue-modules/zhixue-ai/src
git commit -m "feat(ai): 检索返回余弦分数与课程归属（_score 换算回余弦），回答与推荐共用一次检索"
```

---

## 检查点 A（任务 1–3 之后）：增量上线 + 校准门槛

推荐与对话代码都建立在「资料能被检索到、门槛能区分相关与无关」之上。这两点先用真实数据验证，不成立就停下来调整，不往下做。

- [ ] AI 模块测试全绿：`mvn test -q -pl zhixue-modules/zhixue-ai -Dsurefire.failIfNoSpecifiedTests=false`
- [ ] 部署 AI 服务（增量 SQL 已在任务 1 Step 7 执行），确认新分片已补进索引：

```bash
cd /Users/jax/Projects/zhixue-cloud && source scripts/local/common.sh && load_env
mvn -q -DskipTests install -pl zhixue-modules/zhixue-ai -am
compose up -d --force-recreate --no-deps ai
for i in $(seq 1 40); do docker logs zhixue-local-ai --since 2m 2>&1 | grep -q "Started AiApplication" && break; sleep 3; done
docker logs zhixue-local-ai --since 2m 2>&1 | grep -E "补齐分片|补齐向量索引"
docker exec zhixue-local-mysql mysql -uroot -p"$ZHIXUE_DB_PASSWORD" -N -e \
  "SELECT COUNT(*) FROM zhixue_ai.kb_chunk WHERE id BETWEEN 92011 AND 92018 AND es_id IS NULL;" 2>&1 | grep -v Warning
```

Expected: 日志有「补齐分片 8 个」；最后一行输出 `0`。

- [ ] 用真实模型与真实 ES 打分（与任务 6 的算法一致：课程分 = 命中片段的最高余弦；课时分 = 问句与课时标题的余弦）：

```bash
cd /Users/jax/Projects/zhixue-cloud && source scripts/local/common.sh && load_env
ES_PORT="$ZHIXUE_ES_PORT" python3 - <<'PY'
import json, math, os, urllib.request
ES = "http://127.0.0.1:" + os.environ["ES_PORT"]
OLLAMA = "http://127.0.0.1:11434"
def post(url, body):
    req = urllib.request.Request(url, data=json.dumps(body).encode(), headers={"Content-Type": "application/json"})
    return json.load(urllib.request.urlopen(req, timeout=120))
def embed(texts):
    return post(OLLAMA + "/api/embed", {"model": "qwen3-embedding:0.6b", "input": texts})["embeddings"]
def cos(a, b):
    return sum(x * y for x, y in zip(a, b)) / math.sqrt(sum(x * x for x in a) * sum(y * y for y in b))
lessons = {
    2005: [(4011, "数据类型与变量"), (4012, "类、对象与封装"), (4013, "继承、多态与接口"),
           (4014, "异常体系与 try-with-resources"), (4015, "String、StringBuilder 与不可变性")],
    2006: [(4016, "集合框架总览：List、Set、Map"), (4017, "ArrayList 与 LinkedList 的取舍"),
           (4018, "HashMap 底层结构与 hash 计算"), (4019, "HashMap 扩容机制与树化"), (4020, "泛型与类型擦除")],
    2007: [(4021, "线程的创建与生命周期"), (4022, "线程安全问题的根源：可见性、原子性、有序性"),
           (4023, "synchronized 与 ReentrantLock"), (4024, "ConcurrentHashMap 原理"), (4025, "线程池核心参数与拒绝策略")],
}
titles = [t for v in lessons.values() for _, t in v]
title_vec = dict(zip(titles, embed(titles)))
cases = [("HashMap 为什么线程不安全", "推荐 2007、2006"), ("线程池参数怎么设", "推荐 2007"),
         ("String 为什么是不可变的", "推荐 2005"), ("Vue 3 组合式 API 怎么组织页面逻辑", "2002 下架，不推荐"),
         ("今天中午吃什么", "不推荐")]
for q, expect in cases:
    v = embed([q])[0]
    hits = post(ES + "/zhixue_kb/_search", {"knn": {"field": "vector", "query_vector": v, "k": 8,
                "num_candidates": 80, "similarity": 0.45}, "_source": ["chunkId", "courseId"]})["hits"]["hits"]
    best = {}
    for h in hits:
        c = h["_source"].get("courseId")
        if c is not None:
            best[c] = max(best.get(c, -1), 2 * h["_score"] - 1)
    print(f"\n{q}（期望：{expect}）")
    for c, s in sorted(best.items(), key=lambda kv: -kv[1]):
        top = sorted(((cos(v, title_vec[t]), lid, t) for lid, t in lessons.get(c, [])), reverse=True)[:2]
        print(f"  课程 {c}: {s:.3f}  " + "  ".join(f"{lid}:{sc:.3f}" for sc, lid, _ in top))
PY
```

判定（每条都要满足）：
1. 前 3 个用例：期望课程的课程分 ≥ 0.55；标注的课时（课时分 ≥ 0.50）落在 4024/4022、4018/4019、4025、4015 中。
2. 「今天中午吃什么」：所有课程分都 < 0.55。
3. 「Vue 3 组合式 API」：2002 的课程分应 ≥ 0.55（这样才测得到下架过滤），最终由大纲接口按下架过滤（检查点 C 验证）。

> **检查点 A 执行结果（2026-10-20）**：
> - 增量上线：AI 服务启动后约 3.4 秒补齐 8 个分片（ES 9 → 17），`es_id` 全部回填；再次重启补齐 0 个、文档数不变；索引映射为 `dense_vector dims=1024 similarity=cosine`。
> - 判定 1、2 满足：三个正例的期望课程为 0.689–0.785，标注课时落在期望集合内；「今天中午吃什么」无片段达到 0.45。
> - 判定 3 原用例「Pinia」只有 0.512，测不到下架过滤，已改为「Vue 3 组合式 API 怎么组织页面逻辑」（0.744）。
> - 门槛维持 0.55 / 0.50。实测分数、扩大样本与已知局限（try-with-resources 漏推、课时门槛分不开、2005 余量 0.008）见设计文档 4.4。

- [ ] 若不满足：把门槛调到「正例最低分」与「反例最高分」的中点（课程、课时分别调），写进本计划任务 6 的 `application.yml` 默认值与设计文档 4.4，并在提交说明里附上实测分数；若正反例没有间隔，停下来汇报，不要继续。

---

### Task 4: `LessonAccessPolicy` 支持显式身份

**Files:**
- Modify: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/support/LessonAccessPolicy.java`
- Test: `zhixue-modules/zhixue-course/src/test/java/com/zhixue/course/service/LessonAccessPolicyTest.java`（追加 4 项）

**Interfaces:**
- Produces:
  - `record LessonAccessPolicy.Viewer(Long userId, boolean admin)`，`static Viewer current()`（从当前请求头取；无请求上下文时为 `(null, false)`）
  - `boolean canStudyAll(Course course, Viewer viewer)`
  - `boolean mask(Course course, Collection<Section> lessons, Viewer viewer)`：就地标注课时，返回 viewer 能否观看全部课时
  - 原有 `canStudyAll(Course)`、`mask(Course, Collection<Section>)` 行为不变（委托给新重载）

- [ ] **Step 1: 追加失败测试**

在 `LessonAccessPolicyTest` 中追加（文件已有 `paidCourse()`、`section(id, chapterId, order)`、`BUYER`、`TOKEN` 与 `remoteOrderService` mock；需要补充 `import java.util.ArrayList;` 与 `import static org.mockito.ArgumentMatchers.eq;`）：

```java
    /**
     * AI 服务为推荐卡片查询大纲时没有学生的请求头：若仍按请求上下文取身份，userId 是 null，
     * 所有付费课都会被判成「未购买」，买了课的学生看到的推荐卡片仍是锁定（不报错，只是结果错）。
     */
    @Test
    void explicitBuyerWithoutRequestContextCanStudyAll() {
        when(remoteOrderService.hasPurchased(eq(BUYER), eq(2001L), eq("true"), eq(TOKEN))).thenReturn(R.ok(true));
        List<Section> lessons = new ArrayList<>(List.of(
                section(4001L, 3001L, 1), section(4002L, 3002L, 1), section(4003L, 3002L, 2)));

        boolean full = policy.mask(paidCourse(), lessons, new LessonAccessPolicy.Viewer(BUYER, false));

        assertThat(full).isTrue();
        assertThat(lessons).allSatisfy(lesson -> assertThat(lesson.getLocked()).isFalse());
    }

    @Test
    void explicitNonBuyerOnlyGetsPreviewLessons() {
        when(remoteOrderService.hasPurchased(eq(1004L), eq(2001L), eq("true"), eq(TOKEN))).thenReturn(R.ok(false));
        List<Section> lessons = new ArrayList<>(List.of(
                section(4001L, 3001L, 1), section(4002L, 3002L, 1), section(4003L, 3002L, 2)));

        boolean full = policy.mask(paidCourse(), lessons, new LessonAccessPolicy.Viewer(1004L, false));

        assertThat(full).isFalse();
        // 试看前 2 节：章节序 1 的 4001、章节序 2 的第 1 课 4002
        assertThat(lessons).filteredOn(Section::getLocked).extracting(Section::getId).containsExactly(4003L);
        assertThat(lessons).filteredOn(s -> s.getVideoUrl() == null).extracting(Section::getId).containsExactly(4003L);
    }

    @Test
    void explicitAdminSkipsPurchaseQuery() {
        assertThat(policy.canStudyAll(paidCourse(), new LessonAccessPolicy.Viewer(1001L, true))).isTrue();
        verify(remoteOrderService, never()).hasPurchased(anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void anonymousViewerCannotStudyPaidCourse() {
        assertThat(policy.canStudyAll(paidCourse(), new LessonAccessPolicy.Viewer(null, false))).isFalse();
        verify(remoteOrderService, never()).hasPurchased(anyLong(), anyLong(), anyString(), anyString());
    }
```

- [ ] **Step 2: 运行确认失败**

```bash
mvn test -q -pl zhixue-modules/zhixue-course -Dtest=LessonAccessPolicyTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "cannot find symbol|找不到符号" | head -3
```

Expected: 编译失败（`Viewer` 不存在）。

- [ ] **Step 3: 实现**

在 `LessonAccessPolicy` 中，把现有 `canStudyAll(Course)` 与 `mask(Course, Collection<Section>)` 两个方法整体替换为：

```java
    /**
     * 观看者身份。
     *
     * <p>服务间调用（例如 AI 服务为推荐卡片查询课程大纲）没有学生的请求头，
     * 必须把身份显式传进来；按当前请求上下文取，userId 恒为 null，所有付费课都会被判为未购买。</p>
     */
    public record Viewer(Long userId, boolean admin) {

        /** 当前请求的用户（来自网关注入的请求头）；没有请求上下文时为匿名 */
        public static Viewer current() {
            return new Viewer(SecurityUtils.getUserId(), SecurityUtils.hasAnyRole("ADMIN"));
        }
    }

    /**
     * 当前用户能否观看该课程的全部课时。
     */
    public boolean canStudyAll(Course course) {
        return canStudyAll(course, Viewer.current());
    }

    /**
     * 指定观看者能否观看该课程的全部课时：免费课、管理员、本课教师、已购买用户。
     */
    public boolean canStudyAll(Course course, Viewer viewer) {
        if (course.getPrice() == null || course.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        if (viewer == null || viewer.userId() == null) {
            return false;
        }
        // 只有本课教师豁免；持有课程管理权限的其他教师不能借此白看
        if (viewer.admin() || Objects.equals(course.getTeacherId(), viewer.userId())) {
            return true;
        }
        return hasPurchased(viewer.userId(), course.getId());
    }

    /**
     * 就地处理即将返回给客户端的课时：标注试看，并清除无权观看课时的视频地址。
     */
    public void mask(Course course, Collection<Section> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            return;
        }
        mask(course, lessons, Viewer.current());
    }

    /**
     * 指定观看者的版本。
     *
     * @return viewer 能否观看全部课时（调用方据此显示「已购 / 可学习」）
     */
    public boolean mask(Course course, Collection<Section> lessons, Viewer viewer) {
        boolean full = canStudyAll(course, viewer);
        if (lessons == null || lessons.isEmpty()) {
            return full;
        }
        Set<Long> previewIds = previewLessonIds(course);
        for (Section lesson : lessons) {
            boolean preview = previewIds.contains(lesson.getId());
            boolean playable = full || preview;
            lesson.setPreview(preview);
            lesson.setLocked(!playable);
            if (!playable) {
                lesson.setVideoUrl(null);
            }
        }
        return full;
    }
```

- [ ] **Step 4: 运行确认通过（含原有用例，证明旧方法行为不变）**

```bash
mvn test -q -pl zhixue-modules/zhixue-course -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -3
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 5: 提交**

```bash
git add zhixue-modules/zhixue-course/src
git commit -m "feat(course): LessonAccessPolicy 支持显式传入观看者身份，供服务间调用按学生判定"
```

---

### Task 5: 课程大纲内部接口 + Feign 契约

**Files:**
- Create: `zhixue-api/src/main/java/com/zhixue/api/course/domain/CourseOutlineQuery.java`
- Create: `zhixue-api/src/main/java/com/zhixue/api/course/domain/CourseOutline.java`
- Create: `zhixue-api/src/main/java/com/zhixue/api/course/domain/LessonOutline.java`
- Create: `zhixue-api/src/main/java/com/zhixue/api/course/RemoteCourseOutlineService.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/CourseOutlineService.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/service/impl/CourseOutlineServiceImpl.java`
- Create: `zhixue-modules/zhixue-course/src/main/java/com/zhixue/course/controller/CourseInnerController.java`
- Test: `zhixue-modules/zhixue-course/src/test/java/com/zhixue/course/service/CourseOutlineServiceTest.java`
- Test: `zhixue-modules/zhixue-course/src/test/java/com/zhixue/course/controller/CourseInnerControllerTest.java`

**Interfaces:**
- Consumes: `LessonAccessPolicy.mask(Course, Collection<Section>, Viewer)`（Task 4）
- Produces:
  - `R<List<CourseOutline>> RemoteCourseOutlineService.outlines(CourseOutlineQuery query, String innerHeader, String internalToken)`；服务直连路径 `POST /inner/outlines`（网关路径 `/course/inner/outlines`，外部访问返回 403）
  - `CourseOutlineQuery{Long userId; boolean admin; List<Long> courseIds}`
  - `CourseOutline{Long courseId; String title; String coverUrl; BigDecimal price; boolean canStudyAll; boolean previewable; List<LessonOutline> lessons}`
  - `LessonOutline{Long lessonId; Long chapterId; String title; Integer duration; int chapterIndex; int lessonIndex; boolean playable}`
  - 语义：只返回已发布且上架的课程，顺序与 `courseIds` 一致，最多 20 门；`lessons` 按章节序、课时序展开，`chapterIndex` / `lessonIndex` 从 1 开始（前端据此显示「2.2」）。

- [ ] **Step 1: 写失败测试（服务层）**

`CourseOutlineServiceTest.java`：

```java
package com.zhixue.course.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.api.course.domain.LessonOutline;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.mapper.SectionMapper;
import com.zhixue.course.service.impl.CourseOutlineServiceImpl;
import com.zhixue.course.service.support.LessonAccessPolicy;
import com.zhixue.course.service.support.LessonAccessPolicy.Viewer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 推荐卡片的数据源：按请求体里的学生身份判定可播放；只给学生买得到的课；
 * 顺序与请求一致（推荐按相关度排序，这里不能打乱）。
 */
class CourseOutlineServiceTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Course.class);
        TableInfoHelper.initTableInfo(assistant, Chapter.class);
        TableInfoHelper.initTableInfo(assistant, Section.class);
    }

    private final CourseMapper courseMapper = mock(CourseMapper.class);
    private final ChapterMapper chapterMapper = mock(ChapterMapper.class);
    private final SectionMapper sectionMapper = mock(SectionMapper.class);
    private final LessonAccessPolicy policy = mock(LessonAccessPolicy.class);
    private final CourseOutlineServiceImpl service =
            new CourseOutlineServiceImpl(courseMapper, chapterMapper, sectionMapper, policy);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void keepsRequestOrderAndOnlyQueriesPublishedCoursesOnShelf() {
        when(courseMapper.selectList(any())).thenReturn(List.of(course(2006L), course(2007L)));
        when(chapterMapper.selectList(any())).thenReturn(List.of());
        when(sectionMapper.selectList(any())).thenReturn(List.of());
        when(policy.mask(any(Course.class), anyCollection(), any(Viewer.class))).thenReturn(false);

        List<CourseOutline> outlines = service.outlines(new CourseOutlineQuery(1003L, false, List.of(2007L, 2002L, 2006L)));

        // 2002 下架：查询条件把它排除了，数据库不返回，这里也不出现
        assertThat(outlines).extracting(CourseOutline::getCourseId).containsExactly(2007L, 2006L);
        ArgumentCaptor<Wrapper> query = ArgumentCaptor.forClass(Wrapper.class);
        verify(courseMapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("shelf_status").contains("status");
    }

    @Test
    void numbersLessonsByChapterOrderAndUsesExplicitViewer() {
        when(courseMapper.selectList(any())).thenReturn(List.of(course(2007L)));
        when(chapterMapper.selectList(any())).thenReturn(List.of(chapter(3017L, 2), chapter(3016L, 1)));
        when(sectionMapper.selectList(any())).thenReturn(List.of(
                section(4024L, 3017L, 2), section(4021L, 3016L, 1), section(4023L, 3017L, 1), section(4022L, 3016L, 2)));
        Viewer student = new Viewer(1003L, false);
        // 未购买：试看前 2 节（4021、4022）可播放，其余锁定
        when(policy.mask(any(Course.class), anyCollection(), any(Viewer.class))).thenAnswer(invocation -> {
            Collection<Section> lessons = invocation.getArgument(1);
            lessons.forEach(l -> {
                boolean preview = Set.of(4021L, 4022L).contains(l.getId());
                l.setPreview(preview);
                l.setLocked(!preview);
            });
            return false;
        });

        CourseOutline outline = service.outlines(new CourseOutlineQuery(1003L, false, List.of(2007L))).get(0);

        assertThat(outline.getLessons()).extracting(LessonOutline::getLessonId)
                .containsExactly(4021L, 4022L, 4023L, 4024L);
        assertThat(outline.getLessons()).extracting(l -> l.getChapterIndex() + "." + l.getLessonIndex())
                .containsExactly("1.1", "1.2", "2.1", "2.2");
        assertThat(outline.getLessons()).extracting(LessonOutline::isPlayable)
                .containsExactly(true, true, false, false);
        assertThat(outline.isCanStudyAll()).isFalse();
        assertThat(outline.isPreviewable()).isTrue();
        assertThat(outline.getPrice()).isEqualByComparingTo("99.00");
        ArgumentCaptor<Viewer> viewer = ArgumentCaptor.forClass(Viewer.class);
        verify(policy).mask(any(Course.class), anyCollection(), viewer.capture());
        assertThat(viewer.getValue()).isEqualTo(student);
    }

    @Test
    void emptyRequestDoesNotTouchDatabase() {
        assertThat(service.outlines(new CourseOutlineQuery(1003L, false, List.of()))).isEmpty();
        verify(courseMapper, never()).selectList(any());
    }

    private static Course course(long id) {
        Course course = new Course();
        course.setId(id);
        course.setTitle("课程 " + id);
        course.setTeacherId(1002L);
        course.setCoverUrl("https://dummyimage.com/600x340.png");
        course.setPrice(new BigDecimal("99.00"));
        course.setAllowPreview(1);
        course.setPreviewLessonCount(2);
        course.setStatus(1);
        course.setShelfStatus(1);
        return course;
    }

    private static Chapter chapter(long id, int order) {
        Chapter chapter = new Chapter();
        chapter.setId(id);
        chapter.setCourseId(2007L);
        chapter.setOrderNum(order);
        return chapter;
    }

    private static Section section(long id, long chapterId, int order) {
        Section section = new Section();
        section.setId(id);
        section.setCourseId(2007L);
        section.setChapterId(chapterId);
        section.setTitle("课时 " + id);
        section.setDuration(900);
        section.setOrderNum(order);
        section.setVideoUrl("https://example.com/" + id + ".mp4");
        return section;
    }
}
```

- [ ] **Step 2: 写失败测试（权限与 Feign 路径契约）**

`CourseInnerControllerTest.java`：

```java
package com.zhixue.course.controller;

import com.zhixue.api.course.RemoteCourseOutlineService;
import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.course.service.CourseOutlineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 身份由请求体给出，所以必须严格限定为内部调用：否则任何人带上别人的 userId，
 * 就能查出别人买过哪些课。网关从不设置 X-Inner-Call，外部请求一律 403。
 */
class CourseInnerControllerTest {

    private static final String BODY = "{\"userId\":1003,\"admin\":false,\"courseIds\":[2007]}";

    private final CourseOutlineService service = mock(CourseOutlineService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CourseOutline outline = new CourseOutline();
        outline.setCourseId(2007L);
        when(service.outlines(any())).thenReturn(List.of(outline));
        mockMvc = MockMvcBuilders.standaloneSetup(new CourseInnerController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void externalRequestIsForbidden() throws Exception {
        mockMvc.perform(post("/inner/outlines").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.code").value(403));
        verify(service, never()).outlines(any());
    }

    /** Feign 声明的路径必须命中真实控制器（同类故障：RemoteCourseService 曾误带 /course 前缀，一直 500） */
    @Test
    void feignPathHitsControllerForTrustedInternalCall() throws Exception {
        String path = RemoteCourseOutlineService.class
                .getMethod("outlines", CourseOutlineQuery.class, String.class, String.class)
                .getAnnotation(PostMapping.class).value()[0];

        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                        .header(InternalAccessConstants.INNER_CALL_HEADER, "true"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].courseId").value(2007));
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
mvn test -q -pl zhixue-modules/zhixue-course -am -Dtest='CourseOutlineServiceTest,CourseInnerControllerTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "cannot find symbol|找不到符号" | head -3
```

Expected: 编译失败。

- [ ] **Step 4: 写 DTO 与 Feign 客户端（`zhixue-api`）**

`CourseOutlineQuery.java`：

```java
package com.zhixue.api.course.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 查询课程大纲：按谁的身份判定可播放，以及要哪几门课 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseOutlineQuery {

    /** 学生 ID；为空按匿名判定 */
    private Long userId;
    /** 是否管理员（可观看全部课时） */
    private boolean admin;
    private List<Long> courseIds;
}
```

`LessonOutline.java`：

```java
package com.zhixue.api.course.domain;

import lombok.Data;

/** 推荐卡片里一个课时的最小信息 */
@Data
public class LessonOutline {

    private Long lessonId;
    private Long chapterId;
    private String title;
    /** 秒 */
    private Integer duration;
    /** 章节序号，从 1 开始 */
    private int chapterIndex;
    /** 本章内的课时序号，从 1 开始 */
    private int lessonIndex;
    /** 已购、免费、管理员/本课教师，或处于试看范围 */
    private boolean playable;
}
```

`CourseOutline.java`：

```java
package com.zhixue.api.course.domain;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 推荐卡片所需的课程概况与课时列表（已按指定学生判定可播放） */
@Data
public class CourseOutline {

    private Long courseId;
    private String title;
    private String coverUrl;
    private BigDecimal price;
    /** 可观看全部课时：已购、免费、管理员或本课教师 */
    private boolean canStudyAll;
    /** 不能看全部、但有试看课时 */
    private boolean previewable;
    private List<LessonOutline> lessons = new ArrayList<>();
}
```

`RemoteCourseOutlineService.java`：

```java
package com.zhixue.api.course;

import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.common.core.constant.ServiceNameConstants;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.InternalAccessConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * 课程大纲（服务间）。目前只供 AI 服务的推荐卡片使用。
 *
 * <p>Feign 按服务名直连、不经网关：/course 只是网关前缀（StripPrefix=1），这里不能带。
 * 不配置 fallback：失败时调用方必须当作「本次没有推荐」，不能拿默认值当真实数据。
 * 路径契约由 zhixue-course 的 CourseInnerControllerTest 守护。</p>
 */
@FeignClient(contextId = "remoteCourseOutlineService", value = ServiceNameConstants.COURSE_SERVICE)
public interface RemoteCourseOutlineService {

    @PostMapping("/inner/outlines")
    R<List<CourseOutline>> outlines(@RequestBody CourseOutlineQuery query,
                                    @RequestHeader(InternalAccessConstants.INNER_CALL_HEADER) String innerHeader,
                                    @RequestHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER) String internalToken);
}
```

- [ ] **Step 5: 写课程服务实现**

`CourseOutlineService.java`：

```java
package com.zhixue.course.service;

import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;

import java.util.List;

public interface CourseOutlineService {

    /** 一次最多查询的课程数：推荐最多 3 门，历史会话的卡片也不会更多 */
    int MAX_COURSES = 20;

    /**
     * 按请求中的身份组装课程大纲。只返回已发布且上架的课程，顺序与 courseIds 一致。
     */
    List<CourseOutline> outlines(CourseOutlineQuery query);
}
```

`CourseOutlineServiceImpl.java`：

```java
package com.zhixue.course.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.api.course.domain.LessonOutline;
import com.zhixue.course.domain.entity.Chapter;
import com.zhixue.course.domain.entity.Course;
import com.zhixue.course.domain.entity.Section;
import com.zhixue.course.mapper.ChapterMapper;
import com.zhixue.course.mapper.CourseMapper;
import com.zhixue.course.mapper.SectionMapper;
import com.zhixue.course.service.CourseOutlineService;
import com.zhixue.course.service.support.LessonAccessPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 组装课程大纲。观看权限完全交给 {@link LessonAccessPolicy}（试看与购买规则只维护一份），
 * 这里只负责查询、排序与编号。
 */
@Service
@RequiredArgsConstructor
public class CourseOutlineServiceImpl implements CourseOutlineService {

    private static final Comparator<Chapter> CHAPTER_ORDER = Comparator
            .comparing((Chapter c) -> c.getOrderNum() == null ? Integer.MAX_VALUE : c.getOrderNum())
            .thenComparing(Chapter::getId);
    private static final Comparator<Section> LESSON_ORDER = Comparator
            .comparing((Section s) -> s.getOrderNum() == null ? Integer.MAX_VALUE : s.getOrderNum())
            .thenComparing(Section::getId);

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final SectionMapper sectionMapper;
    private final LessonAccessPolicy lessonAccessPolicy;

    @Override
    public List<CourseOutline> outlines(CourseOutlineQuery query) {
        List<Long> ids = query == null || query.getCourseIds() == null ? List.of() : query.getCourseIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .limit(MAX_COURSES)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Course> courses = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                        .in(Course::getId, ids)
                        // 只给学生买得到的课：已发布且上架（逻辑删除由 @TableLogic 自动排除）
                        .eq(Course::getStatus, 1)
                        .eq(Course::getShelfStatus, 1))
                .stream()
                .collect(Collectors.toMap(Course::getId, Function.identity()));
        if (courses.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Chapter>> chaptersByCourse = chapterMapper.selectList(new LambdaQueryWrapper<Chapter>()
                        .in(Chapter::getCourseId, courses.keySet()))
                .stream()
                .collect(Collectors.groupingBy(Chapter::getCourseId));
        Map<Long, List<Section>> lessonsByCourse = sectionMapper.selectList(new LambdaQueryWrapper<Section>()
                        .in(Section::getCourseId, courses.keySet()))
                .stream()
                .collect(Collectors.groupingBy(Section::getCourseId));

        LessonAccessPolicy.Viewer viewer = new LessonAccessPolicy.Viewer(query.getUserId(), query.isAdmin());
        List<CourseOutline> result = new ArrayList<>();
        for (Long id : ids) {
            Course course = courses.get(id);
            if (course == null) {
                continue;
            }
            List<Section> lessons = new ArrayList<>(lessonsByCourse.getOrDefault(id, List.of()));
            boolean full = lessonAccessPolicy.mask(course, lessons, viewer);
            result.add(toOutline(course, chaptersByCourse.getOrDefault(id, List.of()), lessons, full));
        }
        return result;
    }

    private CourseOutline toOutline(Course course, List<Chapter> chapters, List<Section> lessons, boolean full) {
        Map<Long, List<Section>> byChapter = lessons.stream().collect(Collectors.groupingBy(Section::getChapterId));
        List<Chapter> orderedChapters = chapters.stream().sorted(CHAPTER_ORDER).toList();
        List<LessonOutline> outlineLessons = new ArrayList<>();
        for (int c = 0; c < orderedChapters.size(); c++) {
            Chapter chapter = orderedChapters.get(c);
            List<Section> chapterLessons = byChapter.getOrDefault(chapter.getId(), List.of()).stream()
                    .sorted(LESSON_ORDER).toList();
            for (int l = 0; l < chapterLessons.size(); l++) {
                Section lesson = chapterLessons.get(l);
                LessonOutline item = new LessonOutline();
                item.setLessonId(lesson.getId());
                item.setChapterId(chapter.getId());
                item.setTitle(lesson.getTitle());
                item.setDuration(lesson.getDuration());
                item.setChapterIndex(c + 1);
                item.setLessonIndex(l + 1);
                item.setPlayable(!Boolean.TRUE.equals(lesson.getLocked()));
                outlineLessons.add(item);
            }
        }
        CourseOutline outline = new CourseOutline();
        outline.setCourseId(course.getId());
        outline.setTitle(course.getTitle());
        outline.setCoverUrl(course.getCoverUrl());
        outline.setPrice(course.getPrice());
        outline.setCanStudyAll(full);
        outline.setPreviewable(!full && lessons.stream().anyMatch(s -> Boolean.TRUE.equals(s.getPreview())));
        outline.setLessons(outlineLessons);
        return outline;
    }
}
```

`CourseInnerController.java`：

```java
package com.zhixue.course.controller;

import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.utils.SecurityUtils;
import com.zhixue.course.service.CourseOutlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务间接口：按请求体里的学生身份返回课程大纲。
 *
 * <p>身份由调用方给出，因此只能由可信内部调用访问；网关从不设置 X-Inner-Call，外部请求一律 403。</p>
 */
@RestController
@RequiredArgsConstructor
public class CourseInnerController {

    private final CourseOutlineService courseOutlineService;

    @PostMapping("/inner/outlines")
    public R<List<CourseOutline>> outlines(@RequestBody CourseOutlineQuery query) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "禁止外部访问内部课程接口");
        }
        return R.ok(courseOutlineService.outlines(query));
    }
}
```

- [ ] **Step 6: 运行确认通过**

```bash
mvn test -q -pl zhixue-modules/zhixue-course -am -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -3
```

Expected: `BUILD SUCCESS`（含 `RemoteCourseContractTest` 等原有用例）。

- [ ] **Step 7: 提交**

```bash
git add zhixue-api/src zhixue-modules/zhixue-course/src
git commit -m "feat(course,api): 课程大纲内部接口与 Feign 客户端，按请求中的学生身份判定可播放"
```

---

### Task 6: 推荐器

**Files:**
- Modify: `zhixue-modules/zhixue-ai/pom.xml`
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/AiApplication.java`
- Modify: `zhixue-modules/zhixue-ai/src/main/resources/application.yml`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/config/RecommendConfig.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/Viewer.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/LessonTitleVectors.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/RecommendationCard.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/StoredRecommendation.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/CourseRecommender.java`
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/chat/CourseRecommenderTest.java`

**Interfaces:**
- Consumes: `KnowledgeBaseService.RetrievedChunk`（Task 3）；`RemoteCourseOutlineService.outlines`、`CourseOutline`、`LessonOutline`（Task 5）
- Produces:
  - `record Viewer(Long userId, boolean admin)`，`static Viewer current()`（要求已登录，取不到 userId 时抛 401）
  - `record RecommendationCard(String courseId, String title, String coverUrl, BigDecimal price, boolean purchased, boolean previewable, List<LessonCard> lessons)`；`record LessonCard(String lessonId, String chapterId, String title, Integer duration, int chapterIndex, int lessonIndex, boolean playable)`
  - `record StoredRecommendation(String courseId, double score, List<String> lessonIds)`
  - `CourseRecommender.Result recommend(List<RetrievedChunk> hits, List<Float> questionVector, Viewer viewer)`，`record Result(List<StoredRecommendation> stored, List<RecommendationCard> cards)`，`Result.empty()`
  - `List<List<RecommendationCard>> resolve(List<List<StoredRecommendation>> perMessage, Viewer viewer)`：结果与输入等长，一次批量查询

- [ ] **Step 1: 依赖与配置**

`pom.xml` 在 `zhixue-api` 依赖之后加（版本由父工程的 Spring Cloud BOM 管理，与课程服务一致）：

```xml
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-loadbalancer</artifactId>
        </dependency>
```

`AiApplication.java` 加注解与导入：

```java
import com.zhixue.api.course.RemoteCourseOutlineService;
import org.springframework.cloud.openfeign.EnableFeignClients;

// ……
@SpringBootApplication(scanBasePackages = {"com.zhixue.ai", "com.zhixue.common"})
@EnableDiscoveryClient
// 只注册推荐卡片需要的课程大纲客户端；失败时推荐为空，不影响回答
@EnableFeignClients(clients = RemoteCourseOutlineService.class)
public class AiApplication {
```

`application.yml` 的 `ai:` 下（与 `rag:` 同级）追加：

```yaml
  recommend:
    # 均为余弦值（ES _score 已换算），由 scripts/local/ai-reco-check.sh 实测校准，见设计文档 4.4
    course-min-cos: ${ZHIXUE_AI_RECO_COURSE_MIN_COS:0.55}
    lesson-min-cos: ${ZHIXUE_AI_RECO_LESSON_MIN_COS:0.50}
    max-courses: 3
    max-lessons: 2
    lesson-cache-size: 1000
```

- [ ] **Step 2: 写失败测试**

`CourseRecommenderTest.java`：

```java
package com.zhixue.ai.chat;

import com.zhixue.ai.config.RecommendConfig;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.api.course.RemoteCourseOutlineService;
import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.api.course.domain.LessonOutline;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 推荐：课程门槛 0.55、课时门槛 0.50（余弦）；最多 3 门、每门 2 个课时；
 * 课程服务失败只是「没有推荐」，不能抛异常连累回答。
 *
 * <p>课时向量用二维单位向量，问句向量为 [1, 0]，于是课时向量 [c, √(1−c²)] 与问句的余弦恰好是 c。</p>
 */
class CourseRecommenderTest {

    private static final String TOKEN = "unit-test-internal-token-0123456789";
    private static final Viewer STUDENT = new Viewer(1003L, false);
    private static final List<Float> QUESTION = List.of(1f, 0f);

    private final RemoteCourseOutlineService outlineService = mock(RemoteCourseOutlineService.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private CourseRecommender recommender;

    @BeforeEach
    void setUp() {
        RecommendConfig config = new RecommendConfig();
        SecurityProperties security = new SecurityProperties();
        security.setInternalToken(TOKEN);
        recommender = new CourseRecommender(outlineService, new LessonTitleVectors(embeddingService, config),
                config, security);
    }

    @Test
    void ranksCoursesByBestHitAboveThresholdAndCapsAtThree() {
        when(outlineService.outlines(any(), any(), any())).thenReturn(R.ok(List.of(
                outline(2004L), outline(2006L), outline(2007L))));

        CourseRecommender.Result result = recommender.recommend(List.of(
                hit(2006L, 0.62), hit(2006L, 0.50), hit(2007L, 0.58), hit(2005L, 0.56),
                hit(2004L, 0.70), hit(2009L, 0.40), hit(null, 0.90)), QUESTION, STUDENT);

        ArgumentCaptor<CourseOutlineQuery> query = ArgumentCaptor.forClass(CourseOutlineQuery.class);
        verify(outlineService).outlines(query.capture(), eq("true"), eq(TOKEN));
        // 2009 低于门槛、通用资料（courseId=null）不参与；按最高分排序后只取 3 门
        assertThat(query.getValue().getCourseIds()).containsExactly(2004L, 2006L, 2007L);
        assertThat(query.getValue().getUserId()).isEqualTo(1003L);
        assertThat(query.getValue().isAdmin()).isFalse();
        assertThat(result.cards()).extracting(RecommendationCard::courseId).containsExactly("2004", "2006", "2007");
        assertThat(result.stored()).extracting(StoredRecommendation::score).containsExactly(0.7, 0.62, 0.58);
    }

    @Test
    void coursesMissingFromOutlinesAreDropped() {
        // 2002 有资料但已下架：大纲接口不返回它
        when(outlineService.outlines(any(), any(), any())).thenReturn(R.ok(List.of()));

        CourseRecommender.Result result = recommender.recommend(List.of(hit(2002L, 0.80)), QUESTION, STUDENT);

        assertThat(result.cards()).isEmpty();
        assertThat(result.stored()).isEmpty();
    }

    @Test
    void picksTopTwoLessonsAboveLessonThreshold() {
        when(outlineService.outlines(any(), any(), any())).thenReturn(R.ok(List.of(outline(2007L,
                lesson(4021L, "线程的创建与生命周期"), lesson(4022L, "线程安全问题的根源"),
                lesson(4023L, "synchronized 与 ReentrantLock"), lesson(4024L, "ConcurrentHashMap 原理")))));
        Map<String, Double> cosByTitle = Map.of(
                "线程的创建与生命周期", 0.30, "线程安全问题的根源", 0.61,
                "synchronized 与 ReentrantLock", 0.52, "ConcurrentHashMap 原理", 0.74);
        when(embeddingService.embedBatch(anyList())).thenAnswer(invocation -> {
            List<String> titles = invocation.getArgument(0);
            return titles.stream().map(t -> unit(cosByTitle.get(t))).toList();
        });

        CourseRecommender.Result result = recommender.recommend(List.of(hit(2007L, 0.66)), QUESTION, STUDENT);

        assertThat(result.cards().get(0).lessons()).extracting(RecommendationCard.LessonCard::lessonId)
                .containsExactly("4024", "4022");
        assertThat(result.stored().get(0).lessonIds()).containsExactly("4024", "4022");
    }

    @Test
    void noLessonAboveThresholdStillRecommendsCourse() {
        when(outlineService.outlines(any(), any(), any())).thenReturn(R.ok(List.of(outline(2005L,
                lesson(4011L, "数据类型与变量")))));
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(unit(0.20)));

        CourseRecommender.Result result = recommender.recommend(List.of(hit(2005L, 0.60)), QUESTION, STUDENT);

        assertThat(result.cards()).hasSize(1);
        assertThat(result.cards().get(0).lessons()).isEmpty();
    }

    @Test
    void lessonTitleVectorsAreCached() {
        when(outlineService.outlines(any(), any(), any())).thenReturn(R.ok(List.of(outline(2007L,
                lesson(4024L, "ConcurrentHashMap 原理")))));
        when(embeddingService.embedBatch(anyList())).thenReturn(List.of(unit(0.74)));

        recommender.recommend(List.of(hit(2007L, 0.66)), QUESTION, STUDENT);
        recommender.recommend(List.of(hit(2007L, 0.66)), QUESTION, STUDENT);

        verify(embeddingService, times(1)).embedBatch(anyList());
    }

    @Test
    void courseServiceFailureMeansNoRecommendationNotAnError() {
        when(outlineService.outlines(any(), any(), any())).thenThrow(new IllegalStateException("课程服务不可用"));

        assertThat(recommender.recommend(List.of(hit(2007L, 0.80)), QUESTION, STUDENT).cards()).isEmpty();
    }

    @Test
    void nothingAboveThresholdNeverCallsCourseService() {
        assertThat(recommender.recommend(List.of(hit(2007L, 0.32)), QUESTION, STUDENT).cards()).isEmpty();
        verify(outlineService, never()).outlines(any(), any(), any());
    }

    @Test
    void resolveRefreshesCardsInOneCallAndKeepsStoredLessonOrder() {
        CourseOutline bought = outline(2007L, lesson(4022L, "线程安全问题的根源"), lesson(4024L, "ConcurrentHashMap 原理"));
        bought.setCanStudyAll(true); // 学生后来买了这门课
        when(outlineService.outlines(any(), any(), any())).thenReturn(R.ok(List.of(bought)));

        List<List<RecommendationCard>> cards = recommender.resolve(List.of(
                List.of(),
                List.of(new StoredRecommendation("2007", 0.66, List.of("4024", "4022")),
                        new StoredRecommendation("2002", 0.70, List.of()))), STUDENT);

        verify(outlineService, times(1)).outlines(any(), any(), any());
        assertThat(cards).hasSize(2);
        assertThat(cards.get(0)).isEmpty();
        assertThat(cards.get(1)).extracting(RecommendationCard::courseId).containsExactly("2007"); // 2002 已下架
        assertThat(cards.get(1).get(0).purchased()).isTrue();
        assertThat(cards.get(1).get(0).lessons()).extracting(RecommendationCard.LessonCard::lessonId)
                .containsExactly("4024", "4022");
    }

    private static RetrievedChunk hit(Long courseId, double cosine) {
        return new RetrievedChunk(92000L, courseId, cosine, "资料", "内容");
    }

    private static CourseOutline outline(long courseId, LessonOutline... lessons) {
        CourseOutline outline = new CourseOutline();
        outline.setCourseId(courseId);
        outline.setTitle("课程 " + courseId);
        outline.setCoverUrl("https://dummyimage.com/600x340.png");
        outline.setPrice(new BigDecimal("99.00"));
        outline.setPreviewable(true);
        outline.setLessons(List.of(lessons));
        return outline;
    }

    private static LessonOutline lesson(long lessonId, String title) {
        LessonOutline lesson = new LessonOutline();
        lesson.setLessonId(lessonId);
        lesson.setChapterId(3000L + lessonId % 10);
        lesson.setTitle(title);
        lesson.setDuration(900);
        lesson.setChapterIndex(1);
        lesson.setLessonIndex(1);
        return lesson;
    }

    private static List<Float> unit(double cos) {
        return List.of((float) cos, (float) Math.sqrt(1 - cos * cos));
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -am -Dtest=CourseRecommenderTest -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "cannot find symbol|找不到符号" | head -3
```

Expected: 编译失败。

- [ ] **Step 4: 实现配置、身份与数据形态**

`RecommendConfig.java`：

```java
package com.zhixue.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 课程推荐的门槛与上限。门槛均为余弦值，与 ai.rag.min-similarity 同一刻度 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.recommend")
public class RecommendConfig {

    /** 课程得分（命中片段的最高余弦）门槛；高于回答检索的 0.45：资料可以有点相关，推荐必须明确相关 */
    private double courseMinCos = 0.55;
    /** 课时标题与问句的余弦门槛；达不到只显示课程卡片 */
    private double lessonMinCos = 0.50;
    private int maxCourses = 3;
    private int maxLessons = 2;
    /** 课时标题向量缓存条数 */
    private int lessonCacheSize = 1000;
}
```

`Viewer.java`：

```java
package com.zhixue.ai.chat;

import com.zhixue.common.security.utils.SecurityUtils;

/**
 * 提问者身份。
 *
 * <p><b>必须在请求线程上取</b>：身份来自网关注入的请求头，进入流式处理后读不到。
 * 落库归属与「是否已购」判定都靠这里取出的值。</p>
 */
public record Viewer(Long userId, boolean admin) {

    /** 当前登录用户；未登录抛 401 */
    public static Viewer current() {
        return new Viewer(SecurityUtils.requireUserId(), SecurityUtils.hasAnyRole("ADMIN"));
    }
}
```

`StoredRecommendation.java`：

```java
package com.zhixue.ai.chat;

import java.util.List;

/**
 * 落库形态：只有课程/课时 ID 与分数。价格、是否已购会变化，打开历史时按最新状态重新解析。
 */
public record StoredRecommendation(String courseId, double score, List<String> lessonIds) {
}
```

`RecommendationCard.java`：

```java
package com.zhixue.ai.chat;

import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.LessonOutline;

import java.math.BigDecimal;
import java.util.List;

/**
 * 推荐卡片（实时状态，不落库）。ID 一律是字符串：雪花 ID 超出 JS 的安全整数范围。
 *
 * @param purchased 可观看全部课时（已购、免费、管理员或本课教师）
 * @param lessons   最多 2 个最相关的课时；为空表示只推荐课程
 */
public record RecommendationCard(String courseId, String title, String coverUrl, BigDecimal price,
                                 boolean purchased, boolean previewable, List<LessonCard> lessons) {

    public record LessonCard(String lessonId, String chapterId, String title, Integer duration,
                             int chapterIndex, int lessonIndex, boolean playable) {

        static LessonCard of(LessonOutline lesson) {
            return new LessonCard(String.valueOf(lesson.getLessonId()), String.valueOf(lesson.getChapterId()),
                    lesson.getTitle(), lesson.getDuration(), lesson.getChapterIndex(), lesson.getLessonIndex(),
                    lesson.isPlayable());
        }
    }

    static RecommendationCard of(CourseOutline outline, List<LessonOutline> lessons) {
        return new RecommendationCard(String.valueOf(outline.getCourseId()), outline.getTitle(),
                outline.getCoverUrl(), outline.getPrice(), outline.isCanStudyAll(), outline.isPreviewable(),
                lessons.stream().map(LessonCard::of).toList());
    }
}
```

- [ ] **Step 5: 实现课时标题向量缓存**

`LessonTitleVectors.java`：

```java
package com.zhixue.ai.chat;

import com.zhixue.ai.config.RecommendConfig;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.api.course.domain.LessonOutline;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 课时标题向量的 LRU 缓存。键为「课时 ID + 标题」：标题改了键就变，旧向量自然不再命中，无需失效逻辑。
 */
@Component
public class LessonTitleVectors {

    private final EmbeddingService embeddingService;
    private final Map<String, List<Float>> cache;

    public LessonTitleVectors(EmbeddingService embeddingService, RecommendConfig config) {
        this.embeddingService = embeddingService;
        int capacity = Math.max(16, config.getLessonCacheSize());
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, List<Float>> eldest) {
                return size() > capacity;
            }
        });
    }

    /**
     * 返回 课时 ID → 标题向量。未命中的标题一次批量向量化；向量化失败时抛出，由调用方降级为「不标课时」。
     */
    public Map<Long, List<Float>> vectorsOf(List<LessonOutline> lessons) {
        Map<Long, List<Float>> result = new HashMap<>();
        List<LessonOutline> misses = new ArrayList<>();
        for (LessonOutline lesson : lessons) {
            List<Float> cached = cache.get(key(lesson));
            if (cached != null) {
                result.put(lesson.getLessonId(), cached);
            } else {
                misses.add(lesson);
            }
        }
        if (misses.isEmpty()) {
            return result;
        }
        List<List<Float>> vectors = embeddingService.embedBatch(misses.stream().map(LessonOutline::getTitle).toList());
        if (vectors.size() != misses.size()) {
            // sandbox 下模型不可用返回空列表：不缓存，不标课时
            return result;
        }
        for (int i = 0; i < misses.size(); i++) {
            cache.put(key(misses.get(i)), vectors.get(i));
            result.put(misses.get(i).getLessonId(), vectors.get(i));
        }
        return result;
    }

    private static String key(LessonOutline lesson) {
        return lesson.getLessonId() + "|" + lesson.getTitle();
    }
}
```

- [ ] **Step 6: 实现推荐器**

`CourseRecommender.java`：

```java
package com.zhixue.ai.chat;

import com.zhixue.ai.config.RecommendConfig;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.api.course.RemoteCourseOutlineService;
import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.api.course.domain.LessonOutline;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 课程推荐：复用回答的同一次检索结果，不额外调用模型，因此回答引用的资料与推荐的课程不会对不上。
 *
 * <p>选课：命中片段按课程分组，课程分 = 组内最高余弦，过门槛后按分数取前 3；
 * 是否上架、是否已购、能否播放一律以课程服务为准。选课时：问句向量与课时标题向量比余弦，取前 2。
 * 任何外部失败都只记日志、返回空结果，回答照常完成。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourseRecommender {

    private final RemoteCourseOutlineService remoteCourseOutlineService;
    private final LessonTitleVectors lessonTitleVectors;
    private final RecommendConfig config;
    private final SecurityProperties securityProperties;

    public record Result(List<StoredRecommendation> stored, List<RecommendationCard> cards) {

        public static Result empty() {
            return new Result(List.of(), List.of());
        }
    }

    public Result recommend(List<RetrievedChunk> hits, List<Float> questionVector, Viewer viewer) {
        if (hits == null || hits.isEmpty()) {
            return Result.empty();
        }
        Map<Long, Double> courseScore = new HashMap<>();
        for (RetrievedChunk hit : hits) {
            if (hit.courseId() != null) {
                courseScore.merge(hit.courseId(), hit.cosine(), Math::max);
            }
        }
        List<Long> ranked = courseScore.entrySet().stream()
                .filter(e -> e.getValue() >= config.getCourseMinCos())
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(config.getMaxCourses())
                .map(Map.Entry::getKey)
                .toList();
        if (ranked.isEmpty()) {
            return Result.empty();
        }
        Map<Long, CourseOutline> outlines = fetchOutlines(ranked, viewer);
        List<StoredRecommendation> stored = new ArrayList<>();
        List<RecommendationCard> cards = new ArrayList<>();
        for (Long courseId : ranked) {
            CourseOutline outline = outlines.get(courseId);
            if (outline == null) {
                continue; // 未上架、已删除或查询失败
            }
            List<LessonOutline> lessons = bestLessons(outline, questionVector);
            stored.add(new StoredRecommendation(String.valueOf(courseId), round(courseScore.get(courseId)),
                    lessons.stream().map(l -> String.valueOf(l.getLessonId())).toList()));
            cards.add(RecommendationCard.of(outline, lessons));
        }
        return new Result(stored, cards);
    }

    /**
     * 历史消息里的推荐只存了 ID：按最新的价格、购买与上架状态重新生成卡片。
     * 所有消息共用一次批量查询；返回列表与输入等长，查询失败时每条都是空列表。
     */
    public List<List<RecommendationCard>> resolve(List<List<StoredRecommendation>> perMessage, Viewer viewer) {
        List<Long> ids = perMessage.stream()
                .flatMap(List::stream)
                .map(s -> Long.valueOf(s.courseId()))
                .distinct()
                .limit(20)
                .toList();
        Map<Long, CourseOutline> outlines = ids.isEmpty() ? Map.of() : fetchOutlines(ids, viewer);
        List<List<RecommendationCard>> result = new ArrayList<>();
        for (List<StoredRecommendation> stored : perMessage) {
            List<RecommendationCard> cards = new ArrayList<>();
            for (StoredRecommendation item : stored) {
                CourseOutline outline = outlines.get(Long.valueOf(item.courseId()));
                if (outline == null) {
                    continue;
                }
                Map<String, LessonOutline> byId = outline.getLessons().stream()
                        .collect(Collectors.toMap(l -> String.valueOf(l.getLessonId()), Function.identity(), (a, b) -> a));
                cards.add(RecommendationCard.of(outline,
                        item.lessonIds().stream().map(byId::get).filter(Objects::nonNull).toList()));
            }
            result.add(cards);
        }
        return result;
    }

    private List<LessonOutline> bestLessons(CourseOutline outline, List<Float> questionVector) {
        List<LessonOutline> lessons = outline.getLessons() == null ? List.of() : outline.getLessons();
        if (lessons.isEmpty() || questionVector == null || questionVector.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Float>> vectors;
        try {
            vectors = lessonTitleVectors.vectorsOf(lessons);
        } catch (RuntimeException e) {
            log.warn("课时标题向量化失败，只推荐课程不标课时 courseId={}", outline.getCourseId(), e);
            return List.of();
        }
        record Scored(LessonOutline lesson, double cos) {
        }
        return lessons.stream()
                .filter(l -> vectors.containsKey(l.getLessonId()))
                .map(l -> new Scored(l, cosine(questionVector, vectors.get(l.getLessonId()))))
                .filter(s -> s.cos() >= config.getLessonMinCos())
                .sorted(Comparator.comparingDouble(Scored::cos).reversed())
                .limit(config.getMaxLessons())
                .map(Scored::lesson)
                .toList();
    }

    private Map<Long, CourseOutline> fetchOutlines(List<Long> courseIds, Viewer viewer) {
        try {
            R<List<CourseOutline>> response = remoteCourseOutlineService.outlines(
                    new CourseOutlineQuery(viewer.userId(), viewer.admin(), courseIds),
                    "true", securityProperties.getInternalToken());
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.error("查询课程大纲失败，本次不推荐 courseIds={} msg={}", courseIds,
                        response == null ? null : response.getMsg());
                return Map.of();
            }
            return response.getData().stream()
                    .collect(Collectors.toMap(CourseOutline::getCourseId, Function.identity(), (a, b) -> a));
        } catch (RuntimeException e) {
            log.error("调用课程服务查询大纲异常，本次不推荐 courseIds={}", courseIds, e);
            return Map.of();
        }
    }

    /** 余弦相似度；维度不一致或含零向量时为 0 */
    static double cosine(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.isEmpty() || a.size() != b.size()) {
            return 0;
        }
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            na += a.get(i) * a.get(i);
            nb += b.get(i) * b.get(i);
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static double round(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
```

- [ ] **Step 7: 运行确认通过**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -am -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -3
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 8: 提交**

```bash
git add zhixue-modules/zhixue-ai
git commit -m "feat(ai): 课程推荐器——复用同一次检索选课、按课时标题向量选课时、历史卡片批量按最新状态解析"
```

---

## 检查点 B（任务 4–6 之后）

- [ ] 涉及模块全部测试通过：

```bash
mvn test -q -pl zhixue-api,zhixue-modules/zhixue-course,zhixue-modules/zhixue-ai -am -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -3
```

- [ ] 部署课程与 AI 服务，确认 AI 服务带着 Feign 正常启动、内部接口对外不可用：

```bash
cd /Users/jax/Projects/zhixue-cloud && source scripts/local/common.sh && load_env
mvn -q -DskipTests install -pl zhixue-modules/zhixue-course,zhixue-modules/zhixue-ai -am
compose up -d --force-recreate --no-deps course ai
for s in course ai; do
  for i in $(seq 1 40); do docker logs zhixue-local-$s --since 3m 2>&1 | grep -qE "Started [A-Za-z]+Application" && break; sleep 3; done
  echo "$s: $(docker logs zhixue-local-$s --since 3m 2>&1 | grep -cE 'Started [A-Za-z]+Application')"
done
GW="http://127.0.0.1:${ZHIXUE_GATEWAY_PORT:-19001}"
# 学生令牌：本地微信沙箱登录（local.env 中 ZHIXUE_AUTH_WECHAT_MODE=sandbox），不需要验证码
ST=$(curl -s -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
  -d '{"loginType":"wechat","wechatCode":"plan-check"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["token"])')
curl -s -X POST "$GW/course/inner/outlines" -H "Authorization: Bearer $ST" \
  -H 'Content-Type: application/json' -d '{"userId":1003,"admin":true,"courseIds":[2007]}'; echo
```

Expected: `course: 1`、`ai: 1`；最后一行 `{"code":403,"msg":"禁止外部访问内部课程接口",...}`（学生冒充管理员查大纲被拒）。

- [ ] 推荐的真实端到端效果在任务 9 之后用 `event: done` 验证（检查点 C）；这里不单独造测试接口。

---

### Task 7: `chatStream` 真流式 + 跨块过滤 `<think>`

**Files:**
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/service/LlmService.java`
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/service/impl/LlmServiceImpl.java`
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/config/LlmConfig.java`
- Modify: `zhixue-modules/zhixue-ai/src/main/resources/application.yml`（`ai.llm` 下两项）
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/service/impl/ThinkFilter.java`
- Modify: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/support/FakeOllama.java`（支持逐行慢速输出）
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/service/impl/ThinkFilterTest.java`
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/service/impl/LlmServiceStreamTest.java`

**Interfaces:**
- Produces:
  - `record LlmService.ChatMessage(String role, String content)`（role 为 `system` / `user` / `assistant`）
  - `Flux<String> LlmService.chatStream(List<ChatMessage> messages)`：逐块输出已过滤 `<think>`、已去掉开头空白的文本
  - 错误语义（`ServiceException`）：模型不可用或回答为空 `503`「AI 服务暂不可用，请稍后再试」；30 秒内没有任何返回块 `504`「AI 响应超时」；已开始返回但超过 90 秒 `504`「生成超时，已中断」。取消订阅即关闭到 Ollama 的连接。
  - `LlmConfig.firstChunkTimeoutSeconds = 30`、`LlmConfig.numPredict = 1024`

- [ ] **Step 1: 让 FakeOllama 支持逐行慢速输出**

把 `FakeOllama.java` 整体替换为（原有方法与行为不变，新增 `streamChat` 与线程池）：

```java
package com.zhixue.ai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * 测试用的假 Ollama：用 JDK 自带 HttpServer 起一个本地端口，记录收到的请求体并按路径返回预设响应。
 * 让测试覆盖真实的 HTTP 序列化与解析路径，而不是只测 stub 分支。
 *
 * <p>{@link #streamChat} 模拟 Ollama 的流式输出（NDJSON，一行一个 JSON），可设置首块延迟与块间隔，
 * 用来验证首块超时、总时长超时与中途取消。</p>
 */
public final class FakeOllama implements AutoCloseable {

    public record Reply(int status, String body) {
    }

    /** 流式响应：逐行写出，每行之间间隔 gapMs */
    private record Stream(List<String> lines, long firstDelayMs, long gapMs) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpServer server;
    /** 流式处理会阻塞线程，必须用线程池，否则其他请求要排队等它写完 */
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, Function<JsonNode, Reply>> handlers = new ConcurrentHashMap<>();
    private final Map<String, Stream> streams = new ConcurrentHashMap<>();
    private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
    private final List<String> paths = new CopyOnWriteArrayList<>();

    public FakeOllama() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        byte[] raw = exchange.getRequestBody().readAllBytes();
        JsonNode body = raw.length == 0 ? MAPPER.createObjectNode() : MAPPER.readTree(raw);
        paths.add(path);
        requests.add(body);
        Stream stream = streams.get(path);
        if (stream != null) {
            writeStream(exchange, stream);
            return;
        }
        Function<JsonNode, Reply> handler = handlers.get(path);
        Reply reply = handler == null ? new Reply(404, "{\"error\":\"not found\"}") : handler.apply(body);
        byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), out.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(out);
        }
    }

    private static void writeStream(HttpExchange exchange, Stream stream) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
        exchange.sendResponseHeaders(200, 0); // 0 = chunked
        try (OutputStream os = exchange.getResponseBody()) {
            sleep(stream.firstDelayMs());
            for (String line : stream.lines()) {
                os.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
                sleep(stream.gapMs());
            }
        } catch (IOException clientGone) {
            // 客户端取消订阅或超时后断开：正常情况
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public FakeOllama on(String path, Function<JsonNode, Reply> handler) {
        streams.remove(path);
        handlers.put(path, handler);
        return this;
    }

    /** /api/chat 固定回复 content */
    public FakeOllama chatReplies(String content) {
        return on("/api/chat", body -> new Reply(200, json(Map.of(
                "model", body.path("model").asText(),
                "message", Map.of("role", "assistant", "content", content),
                "done", true))));
    }

    /**
     * /api/chat 以流式逐块返回 pieces，最后一行 done=true。
     *
     * @param firstDelayMs 第一块之前的等待（模拟模型冷启动）
     * @param gapMs        块与块之间的间隔
     */
    public FakeOllama streamChat(long firstDelayMs, long gapMs, String... pieces) {
        List<String> lines = new ArrayList<>();
        for (String piece : pieces) {
            lines.add(json(Map.of("message", Map.of("role", "assistant", "content", piece), "done", false)));
        }
        lines.add(json(Map.of("message", Map.of("role", "assistant", "content", ""), "done", true)));
        handlers.remove("/api/chat");
        streams.put("/api/chat", new Stream(lines, firstDelayMs, gapMs));
        return this;
    }

    /** /api/embed 为每条输入返回 dims 维的向量（第 i 条的首维为 i+1，便于断言顺序） */
    public FakeOllama embedReturns(int dims) {
        return on("/api/embed", body -> {
            int n = body.path("input").isArray() ? body.path("input").size() : 1;
            float[][] vectors = new float[n][dims];
            for (int i = 0; i < n; i++) {
                vectors[i][0] = i + 1;
            }
            return new Reply(200, json(Map.of("model", body.path("model").asText(), "embeddings", vectors)));
        });
    }

    public List<JsonNode> requests() {
        return requests;
    }

    public List<String> paths() {
        return paths;
    }

    public JsonNode lastRequest() {
        return requests.isEmpty() ? null : requests.get(requests.size() - 1);
    }

    public static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
```

- [ ] **Step 2: 写失败测试（过滤器）**

`ThinkFilterTest.java`：

```java
package com.zhixue.ai.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流式输出时 &lt;think&gt; 可能被拆在两个块里：整段正则只能处理完整文本，
 * 学生会先看到一段推理内容，再看到它消失。过滤器必须跨块保持状态。
 */
class ThinkFilterTest {

    @Test
    void plainTextPassesThrough() {
        ThinkFilter filter = new ThinkFilter();
        assertThat(filter.accept("HashMap ")).isEqualTo("HashMap ");
        assertThat(filter.accept("不是线程安全的")).isEqualTo("不是线程安全的");
        assertThat(filter.finish()).isEmpty();
    }

    @Test
    void thinkBlockSplitAcrossChunksIsRemoved() {
        ThinkFilter filter = new ThinkFilter();
        assertThat(filter.accept("前言<thi")).isEqualTo("前言");
        assertThat(filter.accept("nk>先想一想</th")).isEmpty();
        assertThat(filter.accept("ink>答案")).isEqualTo("答案");
        assertThat(filter.finish()).isEmpty();
    }

    @Test
    void unclosedThinkBlockIsDroppedAtEnd() {
        ThinkFilter filter = new ThinkFilter();
        assertThat(filter.accept("结论<think>推理一直没结束")).isEqualTo("结论");
        assertThat(filter.finish()).isEmpty();
    }

    @Test
    void lookalikePrefixIsReleasedOnceItCannotBeATag() {
        ThinkFilter filter = new ThinkFilter();
        assertThat(filter.accept("a<th")).isEqualTo("a");
        assertThat(filter.accept("x>b")).isEqualTo("<thx>b");
        assertThat(filter.accept("比较 a<")).isEqualTo("比较 a");
        assertThat(filter.finish()).isEqualTo("<");
    }
}
```

- [ ] **Step 3: 写失败测试（流式调用）**

`LlmServiceStreamTest.java`：

```java
package com.zhixue.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.ai.config.LlmConfig;
import com.zhixue.ai.service.LlmService.ChatMessage;
import com.zhixue.ai.support.FakeOllama;
import com.zhixue.common.core.exception.ServiceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 真流式：边生成边下发；首块超时与总时长超时分开提示；失败不泄露内部地址 */
class LlmServiceStreamTest {

    private FakeOllama ollama;

    @BeforeEach
    void start() throws Exception {
        ollama = new FakeOllama();
    }

    @AfterEach
    void stop() {
        ollama.close();
    }

    private LlmServiceImpl service(int firstChunkSeconds, int totalSeconds) {
        LlmConfig config = new LlmConfig();
        config.setBaseUrl(ollama.baseUrl());
        config.setChatModel("qwen3.5:9b");
        config.setFirstChunkTimeoutSeconds(firstChunkSeconds);
        config.setChatTimeoutSeconds(totalSeconds);
        LlmServiceImpl service = new LlmServiceImpl(config);
        ReflectionTestUtils.setField(service, "aiMode", "real");
        return service;
    }

    private static List<ChatMessage> conversation() {
        return List.of(
                new ChatMessage("system", "你是课程助教"),
                new ChatMessage("user", "HashMap 是什么"),
                new ChatMessage("assistant", "一种哈希表"),
                new ChatMessage("user", "它为什么线程不安全"));
    }

    @Test
    void streamsPiecesInOrderAndSendsAStreamingRequest() {
        ollama.streamChat(0, 30, "HashMap ", "不是", "线程安全的");

        List<String> pieces = service(5, 10).chatStream(conversation()).collectList().block(Duration.ofSeconds(10));

        assertThat(pieces).hasSizeGreaterThan(1); // 逐块到达，而不是攒成一整段
        assertThat(String.join("", pieces)).isEqualTo("HashMap 不是线程安全的");
        JsonNode request = ollama.lastRequest();
        assertThat(request.path("stream").asBoolean()).isTrue();
        assertThat(request.path("think").asBoolean(true)).isFalse();
        assertThat(request.path("options").path("num_predict").asInt()).isEqualTo(1024);
        List<String> roles = new ArrayList<>();
        request.path("messages").forEach(m -> roles.add(m.path("role").asText()));
        assertThat(roles).containsExactly("system", "user", "assistant", "user");
    }

    @Test
    void removesThinkBlockSplitAcrossChunksAndLeadingBlankLines() {
        ollama.streamChat(0, 10, "<thi", "nk>先想一想", "</th", "ink>\n\n答案", "是 B");

        List<String> pieces = service(5, 10).chatStream(conversation()).collectList().block(Duration.ofSeconds(10));

        assertThat(String.join("", pieces)).isEqualTo("答案是 B");
    }

    @Test
    void noChunkWithinFirstChunkTimeoutIs504NotResponding() {
        ollama.streamChat(2500, 0, "迟到的回答");

        assertThatThrownBy(() -> service(1, 10).chatStream(conversation()).blockLast(Duration.ofSeconds(10)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("AI 响应超时")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(504));
    }

    @Test
    void exceedingTotalTimeoutKeepsWhatArrivedAndIs504Interrupted() {
        ollama.streamChat(0, 700, "一", "二", "三", "四", "五");
        List<String> received = new CopyOnWriteArrayList<>();

        assertThatThrownBy(() -> service(1, 2).chatStream(conversation())
                .doOnNext(received::add).blockLast(Duration.ofSeconds(10)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("生成超时，已中断");
        assertThat(received).isNotEmpty();
    }

    @Test
    void ollamaErrorIs503WithoutLeakingAddress() {
        ollama.on("/api/chat", body -> new FakeOllama.Reply(500, "{\"error\":\"model not found\"}"));

        assertThatThrownBy(() -> service(5, 10).chatStream(conversation()).blockLast(Duration.ofSeconds(10)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("AI 服务暂不可用，请稍后再试")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(503));
    }

    @Test
    void onlyThinkingOrBlankIsAnEmptyAnswer() {
        ollama.streamChat(0, 0, "<think>只有推理</think>", "  \n");

        assertThatThrownBy(() -> service(5, 10).chatStream(conversation()).blockLast(Duration.ofSeconds(10)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(503));
    }
}
```

- [ ] **Step 4: 运行确认失败**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -Dtest='ThinkFilterTest,LlmServiceStreamTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "cannot find symbol|找不到符号" | head -3
```

Expected: 编译失败（`ThinkFilter`、`ChatMessage`、`chatStream`、`setFirstChunkTimeoutSeconds` 不存在）。

- [ ] **Step 5: 实现过滤器**

`ThinkFilter.java`：

```java
package com.zhixue.ai.service.impl;

/**
 * 跨块过滤模型输出中的 &lt;think&gt;…&lt;/think&gt;。
 *
 * <p>流式输出时标签可能被拆在两个块里（如 "&lt;thi" + "nk&gt;"）：块末尾若是标签的不完整前缀，
 * 先暂存，与下一块拼起来再判断；处于推理块内时一律不下发。每次流式调用使用一个新实例。</p>
 */
final class ThinkFilter {

    private static final String OPEN = "<think>";
    private static final String CLOSE = "</think>";

    private final StringBuilder pending = new StringBuilder();
    private boolean inside;

    /** 输入一个片段，返回可以下发的文本（可能为空串） */
    String accept(String chunk) {
        if (chunk != null) {
            pending.append(chunk);
        }
        return drain(false);
    }

    /** 流结束：暂存的普通文本照常下发；未闭合的推理块丢弃 */
    String finish() {
        return drain(true);
    }

    private String drain(boolean end) {
        StringBuilder out = new StringBuilder();
        while (true) {
            String tag = inside ? CLOSE : OPEN;
            int at = pending.indexOf(tag);
            if (at >= 0) {
                if (!inside) {
                    out.append(pending, 0, at);
                }
                pending.delete(0, at + tag.length());
                inside = !inside;
                continue;
            }
            int keep = end ? 0 : partialSuffix(pending, tag);
            if (!inside) {
                out.append(pending, 0, pending.length() - keep);
            }
            pending.delete(0, pending.length() - keep);
            return out.toString();
        }
    }

    /** text 末尾有多少个字符可能是 tag 的开头（如 "&lt;thi"） */
    private static int partialSuffix(CharSequence text, String tag) {
        for (int len = Math.min(tag.length() - 1, text.length()); len > 0; len--) {
            if (tag.startsWith(text.subSequence(text.length() - len, text.length()).toString())) {
                return len;
            }
        }
        return 0;
    }
}
```

- [ ] **Step 6: 实现流式调用**

`LlmConfig` 追加两个字段：

```java
    /** 流式回答：多久内必须收到模型的第一个返回块（冷启动加载模型约 6s，给足余量） */
    private int firstChunkTimeoutSeconds = 30;
    /** 单次回答最多生成的 token 数（约 1500 字） */
    private int numPredict = 1024;
```

`application.yml` 的 `ai.llm` 下追加：

```yaml
    first-chunk-timeout-seconds: ${ZHIXUE_AI_FIRST_CHUNK_TIMEOUT:30}
    num-predict: ${ZHIXUE_AI_NUM_PREDICT:1024}
```

`LlmService` 追加：

```java
    /** 一条对话消息；role 为 system / user / assistant */
    record ChatMessage(String role, String content) {
    }

    /**
     * 流式对话：逐块返回已过滤 &lt;think&gt;、已去掉开头空白的文本。
     *
     * <p>以 {@code ServiceException} 结束的情况：模型不可用或回答为空 503；
     * 首个返回块超时 504「AI 响应超时」；已开始返回但总时长超时 504「生成超时，已中断」。
     * 取消订阅会关闭到 Ollama 的连接，模型随即停止生成。</p>
     */
    Flux<String> chatStream(List<ChatMessage> messages);
```

（`LlmService` 需要 `import java.util.List;` 与 `import reactor.core.publisher.Flux;`。）

`LlmServiceImpl` 追加（新增导入：`reactor.core.publisher.Flux`、`java.util.concurrent.TimeoutException`、`java.util.concurrent.atomic.AtomicBoolean`）：

```java
    /** 504 不在公共状态码常量里：只有流式回答用到 */
    static final int GATEWAY_TIMEOUT = 504;

    @Override
    public Flux<String> chatStream(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Flux.error(new ServiceException(HttpStatus.BAD_REQUEST, "问题不能为空"));
        }
        if (isStubMode()) {
            return Flux.just("【stub答复】", "当前为 stub 模式，未调用模型。");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getChatModel());
        body.put("stream", true);
        body.put("think", false);
        body.put("keep_alive", config.getKeepAlive());
        body.put("messages", messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content() == null ? "" : m.content()))
                .toList());
        body.put("options", Map.of(
                "num_ctx", config.getNumCtx(), "temperature", 0.6, "num_predict", config.getNumPredict()));
        return Flux.defer(() -> {
            long deadline = System.nanoTime() + Duration.ofSeconds(config.getChatTimeoutSeconds()).toNanos();
            ThinkFilter filter = new ThinkFilter();
            AtomicBoolean received = new AtomicBoolean();
            AtomicBoolean started = new AtomicBoolean();
            return WebClient.create(config.getBaseUrl())
                    .post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToFlux(String.class) // NDJSON：一行一个 JSON
                    // 首块最多等 firstChunkTimeout；之后每块的等待上限 = 距总截止时间的剩余时长
                    .timeout(Mono.delay(Duration.ofSeconds(config.getFirstChunkTimeoutSeconds())),
                            line -> Mono.delay(remaining(deadline)))
                    .doOnNext(line -> received.set(true))
                    .filter(StringUtils::hasText)
                    .map(line -> textOf(line, filter))
                    .map(text -> started.get() ? text : stripLeading(text, started))
                    .filter(text -> !text.isEmpty())
                    .switchIfEmpty(Flux.defer(() -> Flux.error(unavailable("模型返回了空回答", null))))
                    .onErrorMap(TimeoutException.class, e -> new ServiceException(GATEWAY_TIMEOUT,
                            received.get() ? "生成超时，已中断" : "AI 响应超时", e))
                    .onErrorMap(e -> !(e instanceof ServiceException), e -> unavailable("本地模型流式调用失败", e));
        });
    }

    /** 解析一行 NDJSON，返回过滤后的文本；最后一行（done=true）冲出暂存内容 */
    private String textOf(String line, ThinkFilter filter) {
        JsonNode node;
        try {
            node = objectMapper.readTree(line);
        } catch (Exception e) {
            throw new IllegalStateException("无法解析模型返回: " + abbreviate(line, 60), e);
        }
        if (node.hasNonNull("error")) {
            throw new IllegalStateException("模型返回错误: " + abbreviate(node.path("error").asText(), 60));
        }
        String text = filter.accept(node.path("message").path("content").asText(""));
        return node.path("done").asBoolean(false) ? text + filter.finish() : text;
    }

    /** 回答开头常有推理块留下的空行：首个非空白字符出现之前的空白不下发 */
    private static String stripLeading(String text, AtomicBoolean started) {
        String stripped = text.stripLeading();
        if (!stripped.isEmpty()) {
            started.set(true);
        }
        return stripped;
    }

    private static Duration remaining(long deadlineNanos) {
        return Duration.ofNanos(Math.max(0, deadlineNanos - System.nanoTime()));
    }
```

- [ ] **Step 7: 运行确认通过**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -3
```

Expected: `BUILD SUCCESS`（原有 `LlmServiceImplTest` 也通过，证明 FakeOllama 改造没有改变旧行为）。

- [ ] **Step 8: 用真实模型看一眼流式效果**

```bash
curl -sN -m 60 http://127.0.0.1:11434/api/chat -d '{"model":"qwen3.5:9b","stream":true,"think":false,"messages":[{"role":"user","content":"一句话说明 HashMap 为什么线程不安全"}]}' | head -3
```

Expected: 逐行输出 `{"message":{"content":"…"},"done":false}`，与 FakeOllama 的格式一致。

- [ ] **Step 9: 提交**

```bash
git add zhixue-modules/zhixue-ai/src
git commit -m "feat(ai): 本地模型真流式输出，跨块过滤 think，首块超时与总时长超时分别提示"
```

---

### Task 8: 会话两张表 + 存储 + 4 个 REST 接口

**Files:**
- Modify: `zhixue-modules/zhixue-ai/src/main/resources/sql/ai_schema.sql`（末尾追加两张表）
- Create: `zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-20-ai-conversation.sql`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/domain/entity/AiConversation.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/domain/entity/AiMessage.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/mapper/AiConversationMapper.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/mapper/AiMessageMapper.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/MessageStatus.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/Source.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/ConversationView.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/MessageView.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/ConversationStore.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/controller/ConversationController.java`
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/chat/ConversationStoreTest.java`
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/controller/ConversationControllerTest.java`
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/config/ConversationSchemaTest.java`

**Interfaces:**
- Consumes: `CourseRecommender.resolve`、`StoredRecommendation`、`RecommendationCard`、`Viewer`（Task 6）
- Produces（`ConversationStore`，均为 public）：
  - `AiConversation requireOwned(Long conversationId, Long userId)`：不存在或非本人一律 `ServiceException(404, "会话不存在或已删除")`
  - `AiConversation create(Long userId, String firstQuestion, Long courseId)`
  - `AiMessage appendUserMessage(AiConversation conversation, String question)`
  - `void saveAssistantMessage(long messageId, AiConversation conversation, String content, int status, List<Source> sources, List<StoredRecommendation> recommendations, long costMs)`
  - `List<Turn> completedHistory(Long conversationId, Long beforeMessageId, int rounds)`，`record Turn(String question, String answer)`
  - `String previousQuestion(Long conversationId, Long beforeMessageId)`（没有则 null）
  - `PageResult<ConversationView> listRecent(Long userId, int pageNum, int pageSize)`（pageSize 上限 50）
  - `List<AiMessage> messagesOf(Long conversationId)`、`List<Source> sourcesOf(AiMessage)`、`List<StoredRecommendation> recommendationsOf(AiMessage)`
  - `void rename(Long conversationId, Long userId, String title)`、`void delete(Long conversationId, Long userId)`
  - `static String titleOf(String question)`
- Produces（REST，网关前缀 `/ai`）：`GET /conversations`、`GET /conversations/{id}`（单个会话，学生网页版据此显示「正在问：某课程」）、`GET /conversations/{id}/messages`、`PUT /conversations/{id}`、`DELETE /conversations/{id}`
- `MessageStatus.COMPLETED=1`、`STOPPED=2`、`FAILED=3`；`record Source(String title)`

- [ ] **Step 1: 写失败测试（表结构与增量脚本）**

`ConversationSchemaTest.java`：

```java
package com.zhixue.ai.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** 新表在全新环境（ai_schema.sql）与已有数据环境（增量脚本）都要能建出来，且增量脚本绝不清库 */
class ConversationSchemaTest {

    @Test
    void schemaAndUpgradeBothCreateConversationTables() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/sql/ai_schema.sql"), StandardCharsets.UTF_8);
        String upgrade = Files.readString(Path.of("src/main/resources/sql/upgrade/2026-10-20-ai-conversation.sql"),
                StandardCharsets.UTF_8);
        for (String table : new String[]{"ai_conversation", "ai_message"}) {
            assertThat(schema).contains("CREATE TABLE " + table);
            assertThat(upgrade).contains("CREATE TABLE IF NOT EXISTS " + table);
        }
        for (String sql : new String[]{schema, upgrade}) {
            assertThat(sql).contains("idx_user_recent (user_id, deleted, last_message_time)")
                    .contains("idx_conversation (conversation_id, id)");
        }
        assertThat(upgrade.toLowerCase()).doesNotContain("drop table").doesNotContain("delete from")
                .doesNotContain("truncate");
    }
}
```

- [ ] **Step 2: 写失败测试（存储）**

`ConversationStoreTest.java`：

```java
package com.zhixue.ai.chat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.ai.domain.entity.AiConversation;
import com.zhixue.ai.domain.entity.AiMessage;
import com.zhixue.ai.mapper.AiConversationMapper;
import com.zhixue.ai.mapper.AiMessageMapper;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.ServiceException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationStoreTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AiConversation.class);
        TableInfoHelper.initTableInfo(assistant, AiMessage.class);
    }

    private final AiConversationMapper conversationMapper = mock(AiConversationMapper.class);
    private final AiMessageMapper messageMapper = mock(AiMessageMapper.class);
    private final ConversationStore store = new ConversationStore(conversationMapper, messageMapper);

    @Test
    void titleIsFirstTwentyCharactersWithWhitespaceCollapsed() {
        assertThat(ConversationStore.titleOf("  HashMap\n为什么  线程不安全？请详细讲讲它在多线程下的问题  "))
                .isEqualTo("HashMap 为什么 线程不安全？请详");
        assertThat(ConversationStore.titleOf("   ")).isEqualTo("新对话");
    }

    @Test
    void otherUsersConversationLooksExactlyLikeAMissingOne() {
        when(conversationMapper.selectById(5L)).thenReturn(conversation(5L, 1099L));

        assertThatThrownBy(() -> store.requireOwned(5L, 1003L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("会话不存在或已删除")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(404));
        assertThatThrownBy(() -> store.requireOwned(6L, 1003L))
                .isInstanceOf(ServiceException.class)
                .hasMessage("会话不存在或已删除");
    }

    @Test
    void historyKeepsLastThreeCompletedTurnsOnly() {
        // 数据库按 id 倒序返回最近的消息；第 2 轮的回答失败了，不能带进上下文
        List<AiMessage> desc = new ArrayList<>(List.of(
                message(10L, "assistant", "A5", MessageStatus.COMPLETED), message(9L, "user", "Q5", 1),
                message(8L, "assistant", "A4", MessageStatus.COMPLETED), message(7L, "user", "Q4", 1),
                message(6L, "assistant", "A3", MessageStatus.COMPLETED), message(5L, "user", "Q3", 1),
                message(4L, "assistant", "", MessageStatus.FAILED), message(3L, "user", "Q2", 1),
                message(2L, "assistant", "A1", MessageStatus.COMPLETED), message(1L, "user", "Q1", 1)));
        when(messageMapper.selectList(any())).thenReturn(desc);

        List<ConversationStore.Turn> turns = store.completedHistory(100L, 11L, 3);

        assertThat(turns).containsExactly(
                new ConversationStore.Turn("Q3", "A3"),
                new ConversationStore.Turn("Q4", "A4"),
                new ConversationStore.Turn("Q5", "A5"));
    }

    @Test
    void renameValidatesLengthAndOwnership() {
        when(conversationMapper.selectById(5L)).thenReturn(conversation(5L, 1003L));

        assertThatThrownBy(() -> store.rename(5L, 1003L, "  "))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> store.rename(5L, 1003L, "长".repeat(65)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));

        store.rename(5L, 1003L, "  并发笔记  ");
        ArgumentCaptor<AiConversation> update = ArgumentCaptor.forClass(AiConversation.class);
        verify(conversationMapper).updateById(update.capture());
        assertThat(update.getValue().getTitle()).isEqualTo("并发笔记");
    }

    @Test
    void deletingSomeoneElsesConversationIs404AndDeletesNothing() {
        when(conversationMapper.selectById(5L)).thenReturn(conversation(5L, 1099L));

        assertThatThrownBy(() -> store.delete(5L, 1003L)).isInstanceOf(ServiceException.class);
        verify(conversationMapper, never()).deleteById(anyLong());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageSizeIsCappedAndIdsAreStrings() {
        AiConversation c = conversation(2112171801568825346L, 1003L);
        c.setTitle("HashMap 线程安全");
        when(conversationMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<AiConversation> page = invocation.getArgument(0);
            page.setRecords(List.of(c));
            page.setTotal(1);
            return page;
        });

        PageResult<ConversationView> result = store.listRecent(1003L, 1, 500);

        ArgumentCaptor<Page<AiConversation>> page = ArgumentCaptor.forClass(Page.class);
        verify(conversationMapper).selectPage(page.capture(), any());
        assertThat(page.getValue().getSize()).isEqualTo(50);
        assertThat(result.getRecords()).extracting(ConversationView::id).containsExactly("2112171801568825346");
    }

    @Test
    void recommendationsRoundTripThroughJson() {
        AiConversation c = conversation(100L, 1003L);
        store.saveAssistantMessage(200L, c, "回答", MessageStatus.COMPLETED, List.of(new Source("Java 并发编程实战 · 线程池")),
                List.of(new StoredRecommendation("2007", 0.66, List.of("4025"))), 1234L);
        ArgumentCaptor<AiMessage> saved = ArgumentCaptor.forClass(AiMessage.class);
        verify(messageMapper).insert(saved.capture());

        assertThat(saved.getValue().getId()).isEqualTo(200L);
        assertThat(store.recommendationsOf(saved.getValue()))
                .containsExactly(new StoredRecommendation("2007", 0.66, List.of("4025")));
        assertThat(store.sourcesOf(saved.getValue())).containsExactly(new Source("Java 并发编程实战 · 线程池"));
    }

    private static AiConversation conversation(long id, long userId) {
        AiConversation c = new AiConversation();
        c.setId(id);
        c.setUserId(userId);
        c.setTitle("会话");
        return c;
    }

    private static AiMessage message(long id, String role, String content, int status) {
        AiMessage m = new AiMessage();
        m.setId(id);
        m.setConversationId(100L);
        m.setUserId(1003L);
        m.setRole(role);
        m.setContent(content);
        m.setStatus(status);
        return m;
    }
}
```

- [ ] **Step 3: 写失败测试（接口）**

`ConversationControllerTest.java`：

```java
package com.zhixue.ai.controller;

import com.zhixue.ai.chat.ConversationStore;
import com.zhixue.ai.chat.ConversationView;
import com.zhixue.ai.chat.CourseRecommender;
import com.zhixue.ai.chat.RecommendationCard;
import com.zhixue.ai.chat.StoredRecommendation;
import com.zhixue.ai.chat.Viewer;
import com.zhixue.ai.domain.entity.AiConversation;
import com.zhixue.ai.domain.entity.AiMessage;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.common.security.config.SecurityAccessAspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class ConversationControllerTest {

    private final ConversationStore store = mock(ConversationStore.class);
    private final CourseRecommender recommender = mock(CourseRecommender.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new ConversationController(store, recommender));
        factory.setProxyTargetClass(true);
        factory.addAspect(new SecurityAccessAspect());
        mockMvc = MockMvcBuilders.standaloneSetup((Object) factory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockHttpServletRequestBuilder asStudent(MockHttpServletRequestBuilder builder) {
        return builder.requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                .header(InternalAccessConstants.USER_ID_HEADER, "1003")
                .header(InternalAccessConstants.USER_ROLES_HEADER, "STUDENT");
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/conversations")).andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void listsOwnConversations() throws Exception {
        when(store.listRecent(1003L, 1, 20)).thenReturn(PageResult.of(List.of(
                new ConversationView("2112171801568825346", "HashMap 线程安全", null, LocalDateTime.now())), 1, 20));

        mockMvc.perform(asStudent(get("/conversations")))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.records[0].id").value("2112171801568825346"));
    }

    @Test
    void detailReturnsOwnCourseScopeAndHidesOthers() throws Exception {
        AiConversation owned = new AiConversation();
        owned.setId(5L);
        owned.setUserId(1003L);
        owned.setTitle("并发笔记");
        owned.setCourseId(2007L);
        owned.setLastMessageTime(LocalDateTime.now());
        when(store.requireOwned(5L, 1003L)).thenReturn(owned);
        when(store.requireOwned(6L, 1003L)).thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "会话不存在或已删除"));

        mockMvc.perform(asStudent(get("/conversations/5")))
                .andExpect(jsonPath("$.data.id").value("5"))
                .andExpect(jsonPath("$.data.courseId").value("2007"));
        mockMvc.perform(asStudent(get("/conversations/6")))
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void othersConversationIs404() throws Exception {
        when(store.requireOwned(5L, 1003L)).thenThrow(new ServiceException(HttpStatus.NOT_FOUND, "会话不存在或已删除"));

        mockMvc.perform(asStudent(get("/conversations/5/messages")))
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("会话不存在或已删除"));
    }

    @Test
    void messagesCarryCardsResolvedInOneBatch() throws Exception {
        AiMessage question = message(1L, "user", "HashMap 为什么线程不安全");
        AiMessage answer = message(2L, "assistant", "因为……");
        List<StoredRecommendation> stored = List.of(new StoredRecommendation("2007", 0.66, List.of("4024")));
        when(store.messagesOf(5L)).thenReturn(List.of(question, answer));
        when(store.recommendationsOf(question)).thenReturn(List.of());
        when(store.recommendationsOf(answer)).thenReturn(stored);
        when(store.sourcesOf(question)).thenReturn(List.of());
        when(store.sourcesOf(answer)).thenReturn(List.of());
        RecommendationCard card = new RecommendationCard("2007", "Java 并发编程实战", "c.png",
                new BigDecimal("99.00"), true, false, List.of());
        when(recommender.resolve(eq(List.of(List.of(), stored)), eq(new Viewer(1003L, false))))
                .thenReturn(List.of(List.of(), List.of(card)));

        mockMvc.perform(asStudent(get("/conversations/5/messages")))
                .andExpect(jsonPath("$.data[0].id").value("1"))
                .andExpect(jsonPath("$.data[1].recommendations[0].courseId").value("2007"))
                .andExpect(jsonPath("$.data[1].recommendations[0].purchased").value(true));
        verify(recommender, times(1)).resolve(anyList(), eq(new Viewer(1003L, false)));
    }

    @Test
    void renameAndDeleteDelegateWithCurrentUser() throws Exception {
        mockMvc.perform(asStudent(put("/conversations/5")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"并发笔记\"}"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(asStudent(delete("/conversations/5"))).andExpect(jsonPath("$.code").value(200));

        verify(store).rename(5L, 1003L, "并发笔记");
        verify(store).delete(5L, 1003L);
    }

    private static AiMessage message(long id, String role, String content) {
        AiMessage m = new AiMessage();
        m.setId(id);
        m.setConversationId(5L);
        m.setUserId(1003L);
        m.setRole(role);
        m.setContent(content);
        m.setStatus(1);
        return m;
    }
}
```

- [ ] **Step 4: 运行确认失败**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -Dtest='ConversationSchemaTest,ConversationStoreTest,ConversationControllerTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "cannot find symbol|找不到符号|Tests run" | head -3
```

Expected: 编译失败。

- [ ] **Step 5: 建表与增量脚本**

`ai_schema.sql` 末尾追加：

```sql

DROP TABLE IF EXISTS ai_conversation;
CREATE TABLE ai_conversation
(
    id                BIGINT       NOT NULL PRIMARY KEY,
    user_id           BIGINT       NOT NULL COMMENT '所属学生，所有查询必须带此条件',
    title             VARCHAR(64)  NOT NULL COMMENT '首个问题前 20 字，可重命名',
    course_id         BIGINT                COMMENT '限定课程提问时的课程，创建会话时确定',
    last_message_time DATETIME     NOT NULL,
    create_time       DATETIME,
    update_time       DATETIME,
    deleted           TINYINT      DEFAULT 0,
    INDEX idx_user_recent (user_id, deleted, last_message_time)
);

DROP TABLE IF EXISTS ai_message;
CREATE TABLE ai_message
(
    id               BIGINT       NOT NULL PRIMARY KEY,
    conversation_id  BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL COMMENT '冗余：按消息查询时校验归属',
    role             VARCHAR(16)  NOT NULL COMMENT 'user / assistant',
    content          MEDIUMTEXT   NOT NULL,
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1 完成 2 已停止 3 失败',
    sources          JSON                  COMMENT '引用资料标题',
    recommendations  JSON                  COMMENT '[{courseId, score, lessonIds}]，只存 ID 与分数',
    cost_ms          INT                   COMMENT '生成耗时（毫秒）',
    create_time      DATETIME,
    update_time      DATETIME,
    deleted          TINYINT      DEFAULT 0,
    INDEX idx_conversation (conversation_id, id)
);
```

`upgrade/2026-10-20-ai-conversation.sql`：

```sql
-- 已有数据的环境新增 AI 会话两张表。可重复执行：表已存在时什么都不做。
USE zhixue_ai;

CREATE TABLE IF NOT EXISTS ai_conversation
(
    id                BIGINT       NOT NULL PRIMARY KEY,
    user_id           BIGINT       NOT NULL COMMENT '所属学生，所有查询必须带此条件',
    title             VARCHAR(64)  NOT NULL COMMENT '首个问题前 20 字，可重命名',
    course_id         BIGINT                COMMENT '限定课程提问时的课程，创建会话时确定',
    last_message_time DATETIME     NOT NULL,
    create_time       DATETIME,
    update_time       DATETIME,
    deleted           TINYINT      DEFAULT 0,
    INDEX idx_user_recent (user_id, deleted, last_message_time)
);

CREATE TABLE IF NOT EXISTS ai_message
(
    id               BIGINT       NOT NULL PRIMARY KEY,
    conversation_id  BIGINT       NOT NULL,
    user_id          BIGINT       NOT NULL COMMENT '冗余：按消息查询时校验归属',
    role             VARCHAR(16)  NOT NULL COMMENT 'user / assistant',
    content          MEDIUMTEXT   NOT NULL,
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '1 完成 2 已停止 3 失败',
    sources          JSON                  COMMENT '引用资料标题',
    recommendations  JSON                  COMMENT '[{courseId, score, lessonIds}]，只存 ID 与分数',
    cost_ms          INT                   COMMENT '生成耗时（毫秒）',
    create_time      DATETIME,
    update_time      DATETIME,
    deleted          TINYINT      DEFAULT 0,
    INDEX idx_conversation (conversation_id, id)
);
```

- [ ] **Step 6: 实体、Mapper 与小类型**

`AiConversation.java`：

```java
package com.zhixue.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** AI 会话：侧边栏「最近」里的一项 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_conversation")
public class AiConversation extends BaseEntity {

    private Long userId;
    private String title;
    /** 限定课程提问时的课程；会话创建时确定，之后不可更改 */
    private Long courseId;
    private LocalDateTime lastMessageTime;
}
```

`AiMessage.java`：

```java
package com.zhixue.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 会话中的一条消息；sources 与 recommendations 以 JSON 字符串存取 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
public class AiMessage extends BaseEntity {

    private Long conversationId;
    /** 冗余的归属，按消息查询时直接校验 */
    private Long userId;
    /** user / assistant */
    private String role;
    private String content;
    /** 见 MessageStatus */
    private Integer status;
    /** 引用资料标题，JSON 数组 */
    private String sources;
    /** [{courseId, score, lessonIds}]，只有 ID 与分数 */
    private String recommendations;
    private Integer costMs;
}
```

`AiConversationMapper.java`：

```java
package com.zhixue.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.ai.domain.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}
```

`AiMessageMapper.java`：

```java
package com.zhixue.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.ai.domain.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}
```

`MessageStatus.java`：

```java
package com.zhixue.ai.chat;

/** ai_message.status；学生消息恒为 COMPLETED */
public final class MessageStatus {

    public static final int COMPLETED = 1;
    public static final int STOPPED = 2;
    public static final int FAILED = 3;

    private MessageStatus() {
    }
}
```

`Source.java`：

```java
package com.zhixue.ai.chat;

/** 回答引用的资料（显示为「参考资料：标题」） */
public record Source(String title) {
}
```

`ConversationView.java`：

```java
package com.zhixue.ai.chat;

import com.zhixue.ai.domain.entity.AiConversation;

import java.time.LocalDateTime;

/** 「最近」列表的一项；ID 为字符串 */
public record ConversationView(String id, String title, String courseId, LocalDateTime lastMessageTime) {

    /** 控制器在另一个包，必须是 public */
    public static ConversationView of(AiConversation c) {
        return new ConversationView(String.valueOf(c.getId()), c.getTitle(),
                c.getCourseId() == null ? null : String.valueOf(c.getCourseId()), c.getLastMessageTime());
    }
}
```

`MessageView.java`：

```java
package com.zhixue.ai.chat;

import com.zhixue.ai.domain.entity.AiMessage;

import java.time.LocalDateTime;
import java.util.List;

/** 历史消息；recommendations 为打开历史时按最新状态解析出的卡片 */
public record MessageView(String id, String role, String content, int status, List<Source> sources,
                          List<RecommendationCard> recommendations, Integer costMs, LocalDateTime createTime) {

    public static MessageView of(AiMessage m, List<Source> sources, List<RecommendationCard> cards) {
        return new MessageView(String.valueOf(m.getId()), m.getRole(), m.getContent(),
                m.getStatus() == null ? MessageStatus.COMPLETED : m.getStatus(), sources, cards,
                m.getCostMs(), m.getCreateTime());
    }
}
```

- [ ] **Step 7: 实现存储**

`ConversationStore.java`：

```java
package com.zhixue.ai.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.ai.domain.entity.AiConversation;
import com.zhixue.ai.domain.entity.AiMessage;
import com.zhixue.ai.mapper.AiConversationMapper;
import com.zhixue.ai.mapper.AiMessageMapper;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 会话与消息的持久化。所有读写都按用户归属校验：别人的会话与不存在的会话返回同样的 404，
 * 不暴露会话 ID 是否存在。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationStore {

    static final int TITLE_LENGTH = 20;
    static final int MAX_TITLE_LENGTH = 64;
    static final int MAX_PAGE_SIZE = 50;
    /** 取历史时最多回看的消息条数：足够凑出最近 3 轮已完成的问答 */
    private static final int HISTORY_SCAN = 20;
    private static final String USER = "user";
    private static final String ASSISTANT = "assistant";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<StoredRecommendation>> STORED_LIST = new TypeReference<>() {
    };

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 一轮已完成的问答 */
    public record Turn(String question, String answer) {
    }

    public AiConversation requireOwned(Long conversationId, Long userId) {
        AiConversation conversation = conversationId == null ? null : conversationMapper.selectById(conversationId);
        if (conversation == null || !Objects.equals(conversation.getUserId(), userId)) {
            throw new ServiceException(HttpStatus.NOT_FOUND, "会话不存在或已删除");
        }
        return conversation;
    }

    public AiConversation create(Long userId, String firstQuestion, Long courseId) {
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(titleOf(firstQuestion));
        conversation.setCourseId(courseId);
        conversation.setLastMessageTime(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return conversation;
    }

    public AiMessage appendUserMessage(AiConversation conversation, String question) {
        AiMessage message = new AiMessage();
        message.setConversationId(conversation.getId());
        message.setUserId(conversation.getUserId());
        message.setRole(USER);
        message.setContent(question);
        message.setStatus(MessageStatus.COMPLETED);
        messageMapper.insert(message);
        touch(conversation.getId());
        return message;
    }

    /**
     * @param messageId 生成开始时预先分配的 ID（meta 事件已经把它发给了前端）
     */
    public void saveAssistantMessage(long messageId, AiConversation conversation, String content, int status,
                                     List<Source> sources, List<StoredRecommendation> recommendations, long costMs) {
        AiMessage message = new AiMessage();
        message.setId(messageId);
        message.setConversationId(conversation.getId());
        message.setUserId(conversation.getUserId());
        message.setRole(ASSISTANT);
        message.setContent(content == null ? "" : content);
        message.setStatus(status);
        message.setSources(toJson(sources == null ? List.of() : sources.stream().map(Source::title).toList()));
        message.setRecommendations(toJson(recommendations == null ? List.of() : recommendations));
        message.setCostMs((int) Math.min(costMs, Integer.MAX_VALUE));
        messageMapper.insert(message);
        touch(conversation.getId());
    }

    /** 最近 rounds 轮已完成的问答（回答失败或已停止的那一轮不带入），按时间正序 */
    public List<Turn> completedHistory(Long conversationId, Long beforeMessageId, int rounds) {
        List<AiMessage> recent = new ArrayList<>(messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .lt(AiMessage::getId, beforeMessageId)
                .orderByDesc(AiMessage::getId)
                .last("LIMIT " + HISTORY_SCAN)));
        Collections.reverse(recent);
        List<Turn> turns = new ArrayList<>();
        for (int i = 0; i + 1 < recent.size(); i++) {
            AiMessage question = recent.get(i);
            AiMessage answer = recent.get(i + 1);
            if (USER.equals(question.getRole()) && ASSISTANT.equals(answer.getRole())
                    && Objects.equals(answer.getStatus(), MessageStatus.COMPLETED)) {
                turns.add(new Turn(question.getContent(), answer.getContent()));
                i++;
            }
        }
        return turns.size() <= rounds ? turns : turns.subList(turns.size() - rounds, turns.size());
    }

    /** 同一会话中上一个学生问题（无论回答成败），用于理解「那它呢」这类追问 */
    public String previousQuestion(Long conversationId, Long beforeMessageId) {
        List<AiMessage> last = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .eq(AiMessage::getRole, USER)
                .lt(AiMessage::getId, beforeMessageId)
                .orderByDesc(AiMessage::getId)
                .last("LIMIT 1"));
        return last.isEmpty() ? null : last.get(0).getContent();
    }

    public PageResult<ConversationView> listRecent(Long userId, int pageNum, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Page<AiConversation> page = conversationMapper.selectPage(new Page<>(Math.max(pageNum, 1), size),
                new LambdaQueryWrapper<AiConversation>()
                        .eq(AiConversation::getUserId, userId)
                        .orderByDesc(AiConversation::getLastMessageTime)
                        .orderByDesc(AiConversation::getId));
        return PageResult.of(page.getRecords().stream().map(ConversationView::of).toList(), page.getTotal(), size);
    }

    public List<AiMessage> messagesOf(Long conversationId) {
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getId));
    }

    public void rename(Long conversationId, Long userId, String title) {
        String trimmed = title == null ? "" : title.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_TITLE_LENGTH) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "标题长度为 1-" + MAX_TITLE_LENGTH + " 个字");
        }
        requireOwned(conversationId, userId);
        AiConversation update = new AiConversation();
        update.setId(conversationId);
        update.setTitle(trimmed);
        conversationMapper.updateById(update);
    }

    /** 逻辑删除会话；消息随会话一起不可见 */
    public void delete(Long conversationId, Long userId) {
        requireOwned(conversationId, userId);
        conversationMapper.deleteById(conversationId);
    }

    public List<Source> sourcesOf(AiMessage message) {
        return parse(message.getSources(), STRING_LIST).stream().map(Source::new).toList();
    }

    public List<StoredRecommendation> recommendationsOf(AiMessage message) {
        return parse(message.getRecommendations(), STORED_LIST);
    }

    /** 首个问题的前 20 个字（合并空白）；全是空白时为「新对话」 */
    static String titleOf(String question) {
        String normalized = question == null ? "" : question.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return "新对话";
        }
        int codePoints = normalized.codePointCount(0, normalized.length());
        return normalized.substring(0, normalized.offsetByCodePoints(0, Math.min(TITLE_LENGTH, codePoints)));
    }

    private void touch(Long conversationId) {
        AiConversation update = new AiConversation();
        update.setId(conversationId);
        update.setLastMessageTime(LocalDateTime.now());
        conversationMapper.updateById(update);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("序列化失败", e);
        }
    }

    private <T> List<T> parse(String json, TypeReference<List<T>> type) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<T> value = objectMapper.readValue(json, type);
            return value == null ? List.of() : value;
        } catch (Exception e) {
            log.warn("消息附加数据无法解析，按空处理：{}", e.getMessage());
            return List.of();
        }
    }
}
```

- [ ] **Step 8: 实现接口**

`ConversationController.java`：

```java
package com.zhixue.ai.controller;

import com.zhixue.ai.chat.ConversationStore;
import com.zhixue.ai.chat.ConversationView;
import com.zhixue.ai.chat.CourseRecommender;
import com.zhixue.ai.chat.MessageView;
import com.zhixue.ai.chat.RecommendationCard;
import com.zhixue.ai.chat.StoredRecommendation;
import com.zhixue.ai.chat.Viewer;
import com.zhixue.ai.domain.entity.AiMessage;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.annotation.RequireLogin;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话管理（侧边栏「最近」、打开历史、重命名、删除）。流式提问在 ConversationStreamController。
 * 网关路径带 /ai 前缀（StripPrefix=1）。
 */
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationStore store;
    private final CourseRecommender recommender;

    public record RenameRequest(String title) {
    }

    @RequireLogin
    @GetMapping
    public R<PageResult<ConversationView>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(store.listRecent(Viewer.current().userId(), pageNum, pageSize));
    }

    /** 单个会话：学生网页版打开不在「最近」前 50 条里的会话时，用它取课程范围 */
    @RequireLogin
    @GetMapping("/{id}")
    public R<ConversationView> detail(@PathVariable Long id) {
        return R.ok(ConversationView.of(store.requireOwned(id, Viewer.current().userId())));
    }

    @RequireLogin
    @GetMapping("/{id}/messages")
    public R<List<MessageView>> messages(@PathVariable Long id) {
        Viewer viewer = Viewer.current();
        store.requireOwned(id, viewer.userId());
        List<AiMessage> messages = store.messagesOf(id);
        List<List<StoredRecommendation>> stored = messages.stream().map(store::recommendationsOf).toList();
        // 卡片按最新的价格、购买与上架状态解析，全部消息一次批量查询
        List<List<RecommendationCard>> cards = recommender.resolve(stored, viewer);
        List<MessageView> views = new ArrayList<>(messages.size());
        for (int i = 0; i < messages.size(); i++) {
            AiMessage message = messages.get(i);
            views.add(MessageView.of(message, store.sourcesOf(message), cards.get(i)));
        }
        return R.ok(views);
    }

    @RequireLogin
    @PutMapping("/{id}")
    public R<Void> rename(@PathVariable Long id, @RequestBody RenameRequest request) {
        store.rename(id, Viewer.current().userId(), request == null ? null : request.title());
        return R.ok();
    }

    @RequireLogin
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        store.delete(id, Viewer.current().userId());
        return R.ok();
    }
}
```

- [ ] **Step 9: 运行确认通过**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -3
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 10: 本机执行增量建表（两次，验证可重复）并部署**

```bash
cd /Users/jax/Projects/zhixue-cloud && source scripts/local/common.sh && load_env
for i in 1 2; do
  docker exec -i zhixue-local-mysql mysql --default-character-set=utf8mb4 -uroot -p"$ZHIXUE_DB_PASSWORD" \
    < zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-20-ai-conversation.sql 2>&1 | grep -v Warning
done
docker exec zhixue-local-mysql mysql -uroot -p"$ZHIXUE_DB_PASSWORD" -N -e "SHOW TABLES FROM zhixue_ai LIKE 'ai_%';" 2>&1 | grep -v Warning
mvn -q -DskipTests install -pl zhixue-modules/zhixue-ai -am && compose up -d --force-recreate --no-deps ai
for i in $(seq 1 40); do docker logs zhixue-local-ai --since 2m 2>&1 | grep -q "Started AiApplication" && break; sleep 3; done
GW="http://127.0.0.1:${ZHIXUE_GATEWAY_PORT:-19001}"
ST=$(curl -s -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
  -d '{"loginType":"wechat","wechatCode":"plan-check"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["token"])')
curl -s "$GW/ai/conversations" -H "Authorization: Bearer $ST"; echo
curl -s "$GW/ai/conversations/1" -H "Authorization: Bearer $ST"; echo
curl -s -o /dev/null -w "未登录 HTTP=%{http_code}\n" "$GW/ai/conversations"
```

Expected: 两次执行均无报错；列出 `ai_conversation`、`ai_message`；登录后返回 `{"code":200,"data":{"records":[],"total":0,...}}`；未登录 `HTTP=401`。

- [ ] **Step 11: 提交**

```bash
git add zhixue-modules/zhixue-ai/src
git commit -m "feat(ai): AI 会话与消息两张表、归属校验与 4 个会话接口（历史卡片按最新状态解析）"
```

---

### Task 9: 流式提问接口（编排 + SSE）

**Files:**
- Modify: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/controller/AiChatController.java`（`DEFAULT_SYSTEM_PROMPT` 改为 `public`，供新旧两个入口共用同一份指令）
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/GenerationSlots.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/PromptAssembler.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/AskRequest.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/ChatEvents.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/chat/ConversationChatService.java`
- Create: `zhixue-modules/zhixue-ai/src/main/java/com/zhixue/ai/controller/ConversationStreamController.java`
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/chat/PromptAssemblerTest.java`
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/chat/ConversationChatServiceTest.java`
- Test: `zhixue-modules/zhixue-ai/src/test/java/com/zhixue/ai/controller/ConversationStreamControllerTest.java`

**Interfaces:**
- Consumes: `ConversationStore`（Task 8）、`CourseRecommender`、`Viewer`（Task 6）、`LlmService.chatStream`、`ChatMessage`（Task 7）、`KnowledgeBaseService.retrieveScored`（Task 3）、`EmbeddingService.embed`
- Produces:
  - `POST /conversations/messages`（网关 `/ai/conversations/messages`），请求体 `AskRequest(Long conversationId, String question, Long courseId)`；`courseId` 只在新建会话时生效
  - 流前错误按普通 JSON 返回（HTTP 200 + `body.code`）；否则 `text/event-stream`，事件依次为：
    - `meta`：`{conversationId, messageId, title}`
    - `delta`：`{text}`（多次）
    - 以 `done` 或 `error` 二者之一结束：`done` 为 `{messageId, sources:[{title}], notice, recommendations:[RecommendationCard]}`；`error` 为 `{code, msg}`

- [ ] **Step 1: 写失败测试（提示词组装）**

`PromptAssemblerTest.java`：

```java
package com.zhixue.ai.chat;

import com.zhixue.ai.controller.AiChatController;
import com.zhixue.ai.domain.entity.PromptTemplate;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.ai.service.LlmService.ChatMessage;
import com.zhixue.ai.service.PromptTemplateService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptAssemblerTest {

    private final PromptTemplateService templates = mock(PromptTemplateService.class);
    private final PromptAssembler assembler = new PromptAssembler(templates);

    @Test
    void keepsLastThreeTurnsTruncatedAndPutsMaterialsBeforeTheQuestion() {
        List<ConversationStore.Turn> history = List.of(
                new ConversationStore.Turn("Q1", "A1"), new ConversationStore.Turn("Q2", "A2"),
                new ConversationStore.Turn("Q3", "长".repeat(900)), new ConversationStore.Turn("Q4", "A4"));
        List<RetrievedChunk> context = List.of(new RetrievedChunk(92017L, 2007L, 0.66,
                "Java 并发编程实战 · 锁与并发容器", "JDK 8 的 ConcurrentHashMap 对非空桶只锁头节点"));

        List<ChatMessage> messages = assembler.build(history, context, "那 ConcurrentHashMap 呢");

        assertThat(messages).extracting(ChatMessage::role)
                .containsExactly("system", "user", "assistant", "user", "assistant", "user", "assistant", "user");
        assertThat(messages.get(1).content()).isEqualTo("Q2"); // Q1 超出 3 轮
        assertThat(messages.get(4).content()).hasSize(801).endsWith("…"); // 800 字 + 省略号
        String last = messages.get(7).content();
        assertThat(last).contains("1. 《Java 并发编程实战 · 锁与并发容器》JDK 8 的 ConcurrentHashMap");
        assertThat(last.indexOf("【课程资料】")).isLessThan(last.indexOf("【问题】"));
        assertThat(last).endsWith("那 ConcurrentHashMap 呢");
    }

    @Test
    void saysSoWhenNothingWasRetrieved() {
        List<ChatMessage> messages = assembler.build(List.of(), List.of(), "今天中午吃什么");
        assertThat(messages.get(messages.size() - 1).content()).contains("（未检索到相关资料）");
    }

    @Test
    void onlyTopFourChunksGoIntoThePrompt() {
        List<RetrievedChunk> hits = List.of(1L, 2L, 3L, 4L, 5L, 6L).stream()
                .map(id -> new RetrievedChunk(id, 2007L, 0.6, "资料 " + id, "内容")).toList();
        assertThat(assembler.contextOf(hits)).extracting(RetrievedChunk::chunkId).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void retrievalQueryAppendsPreviousQuestionForFollowUps() {
        assertThat(PromptAssembler.retrievalQuery("那它呢", "HashMap 为什么线程不安全"))
                .isEqualTo("那它呢\nHashMap 为什么线程不安全");
        assertThat(PromptAssembler.retrievalQuery("HashMap 为什么线程不安全", null))
                .isEqualTo("HashMap 为什么线程不安全");
    }

    @Test
    void systemPromptComesFromTemplateWithPlaceholdersRemoved() {
        assertThat(assembler.build(List.of(), List.of(), "q").get(0).content())
                .isEqualTo(AiChatController.DEFAULT_SYSTEM_PROMPT);
        PromptTemplate template = new PromptTemplate();
        template.setContent("你是助教。{{context}}{{question}}");
        when(templates.getByCode("COURSE_QA")).thenReturn(template);
        assertThat(assembler.build(List.of(), List.of(), "q").get(0).content()).isEqualTo("你是助教。");
    }
}
```

- [ ] **Step 2: 写失败测试（编排）**

`ConversationChatServiceTest.java`：

```java
package com.zhixue.ai.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.ai.domain.entity.AiConversation;
import com.zhixue.ai.domain.entity.AiMessage;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.ai.service.KnowledgeBaseService;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.ai.service.LlmService;
import com.zhixue.ai.service.LlmService.ChatMessage;
import com.zhixue.ai.service.PromptTemplateService;
import com.zhixue.common.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 一次提问的完整编排。重点是边界：
 * 占位必须先于任何写库（409 不留垃圾）、流前异常释放占位、流开始后三种结束方式都落库并释放占位。
 */
class ConversationChatServiceTest {

    private static final Viewer STUDENT = new Viewer(1003L, false);
    private static final String QUESTION = "HashMap 为什么线程不安全";

    private final ConversationStore store = mock(ConversationStore.class);
    private final GenerationSlots slots = new GenerationSlots();
    private final EmbeddingService embedding = mock(EmbeddingService.class);
    private final KnowledgeBaseService knowledge = mock(KnowledgeBaseService.class);
    private final LlmService llm = mock(LlmService.class);
    private final CourseRecommender recommender = mock(CourseRecommender.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AiConversation conversation = new AiConversation();
    private ConversationChatService service;

    @BeforeEach
    void setUp() {
        service = new ConversationChatService(store, slots, new PromptAssembler(mock(PromptTemplateService.class)),
                embedding, knowledge, llm, recommender);
        conversation.setId(100L);
        conversation.setUserId(1003L);
        conversation.setTitle(QUESTION);
        AiMessage question = new AiMessage();
        question.setId(101L);
        when(store.create(eq(1003L), anyString(), any())).thenReturn(conversation);
        when(store.appendUserMessage(eq(conversation), anyString())).thenReturn(question);
        when(store.completedHistory(anyLong(), anyLong(), anyInt())).thenReturn(List.of());
        when(embedding.embed(anyString())).thenReturn(List.of(1f, 0f));
        when(knowledge.retrieveScored(anyString(), anyList(), anyInt(), any())).thenReturn(List.of(new RetrievedChunk(
                92017L, 2007L, 0.66, "Java 并发编程实战 · 锁与并发容器", "ConcurrentHashMap 只锁桶的头节点")));
        when(recommender.recommend(anyList(), anyList(), eq(STUDENT))).thenReturn(new CourseRecommender.Result(
                List.of(new StoredRecommendation("2007", 0.66, List.of())),
                List.of(new RecommendationCard("2007", "Java 并发编程实战", "c.png", new BigDecimal("99.00"),
                        false, true, List.of()))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void streamsMetaThenDeltasThenDoneAndSavesTheAnswer() throws Exception {
        when(llm.chatStream(anyList())).thenReturn(Flux.just("Hash", "Map"));

        List<ServerSentEvent<String>> events = service.ask(STUDENT, new AskRequest(null, QUESTION, null))
                .collectList().block(Duration.ofSeconds(5));

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("meta", "delta", "delta", "done");
        JsonNode meta = json.readTree(events.get(0).data());
        JsonNode done = json.readTree(events.get(3).data());
        assertThat(meta.path("conversationId").asText()).isEqualTo("100");
        assertThat(done.path("messageId").asText()).isEqualTo(meta.path("messageId").asText());
        assertThat(done.path("sources").get(0).path("title").asText()).isEqualTo("Java 并发编程实战 · 锁与并发容器");
        assertThat(done.path("recommendations").get(0).path("courseId").asText()).isEqualTo("2007");
        assertThat(done.path("notice").isNull()).isTrue();
        verify(store).saveAssistantMessage(eq(Long.parseLong(meta.path("messageId").asText())), eq(conversation),
                eq("HashMap"), eq(MessageStatus.COMPLETED), anyList(),
                eq(List.of(new StoredRecommendation("2007", 0.66, List.of()))), anyLong());
        ArgumentCaptor<List<ChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        verify(llm).chatStream(prompt.capture());
        assertThat(prompt.getValue().get(prompt.getValue().size() - 1).content())
                .contains("《Java 并发编程实战 · 锁与并发容器》");
        assertThat(slots.tryAcquire(1003L)).as("结束后释放占位").isTrue();
    }

    @Test
    void invalidQuestionIsRejectedBeforeTakingTheSlot() {
        assertThatThrownBy(() -> service.ask(STUDENT, new AskRequest(null, "   ", null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> service.ask(STUDENT, new AskRequest(null, "问".repeat(501), null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(400));
        verify(store, never()).create(anyLong(), anyString(), any());
        assertThat(slots.tryAcquire(1003L)).isTrue();
    }

    @Test
    void othersConversationIs404BeforeTakingTheSlot() {
        when(store.requireOwned(5L, 1003L)).thenThrow(new ServiceException(404, "会话不存在或已删除"));

        assertThatThrownBy(() -> service.ask(STUDENT, new AskRequest(5L, QUESTION, null)))
                .isInstanceOf(ServiceException.class)
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(404));
        assertThat(slots.tryAcquire(1003L)).isTrue();
    }

    @Test
    void secondQuestionWhileGeneratingIs409AndWritesNothing() {
        slots.tryAcquire(1003L);

        assertThatThrownBy(() -> service.ask(STUDENT, new AskRequest(null, "线程池参数怎么设", null)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("上一个回答还在生成")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(409));
        // 被拒绝的请求不能在「最近」里留下空会话，也不能在历史里留下没有回答的问题
        verify(store, never()).create(anyLong(), anyString(), any());
        verify(store, never()).appendUserMessage(any(), anyString());
    }

    @Test
    void failureBeforeStreamingReleasesTheSlot() {
        when(store.appendUserMessage(any(), anyString())).thenThrow(new IllegalStateException("数据库连接断开"));

        assertThatThrownBy(() -> service.ask(STUDENT, new AskRequest(null, QUESTION, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessage("发送失败，请重试")
                .satisfies(e -> assertThat(((ServiceException) e).getCode()).isEqualTo(500));
        assertThat(slots.tryAcquire(1003L)).as("否则这个学生之后每次提问都是 409，直到服务重启").isTrue();
    }

    @Test
    void modelFailureAfterSomeTextEndsWithErrorEventAndKeepsThePartialAnswer() throws Exception {
        when(llm.chatStream(anyList())).thenReturn(Flux.concat(Flux.just("Hash"),
                Flux.error(new ServiceException(503, "AI 服务暂不可用，请稍后再试"))));

        List<ServerSentEvent<String>> events = service.ask(STUDENT, new AskRequest(null, QUESTION, null))
                .collectList().block(Duration.ofSeconds(5));

        assertThat(events).extracting(ServerSentEvent::event).containsExactly("meta", "delta", "error");
        JsonNode error = json.readTree(events.get(2).data());
        assertThat(error.path("code").asInt()).isEqualTo(503);
        assertThat(error.path("msg").asText()).isEqualTo("AI 服务暂不可用，请稍后再试");
        verify(store).saveAssistantMessage(anyLong(), eq(conversation), eq("Hash"), eq(MessageStatus.FAILED),
                anyList(), eq(List.of()), anyLong());
        assertThat(slots.tryAcquire(1003L)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrievalFailureStillAnswersWithANoticeAndNoRecommendation() throws Exception {
        when(embedding.embed(anyString())).thenThrow(new ServiceException(503, "AI 服务暂不可用，请稍后再试"));
        when(llm.chatStream(anyList())).thenReturn(Flux.just("答案"));

        List<ServerSentEvent<String>> events = service.ask(STUDENT, new AskRequest(null, QUESTION, null))
                .collectList().block(Duration.ofSeconds(5));

        JsonNode done = json.readTree(events.get(events.size() - 1).data());
        assertThat(done.path("notice").asText()).isEqualTo(ConversationChatService.RETRIEVAL_NOTICE);
        assertThat(done.path("recommendations").size()).isZero();
        verify(recommender, never()).recommend(anyList(), anyList(), any());
        ArgumentCaptor<List<ChatMessage>> prompt = ArgumentCaptor.forClass(List.class);
        verify(llm).chatStream(prompt.capture());
        assertThat(prompt.getValue().get(prompt.getValue().size() - 1).content()).contains("（未检索到相关资料）");
    }

    @Test
    void followUpUsesConversationScopeAndPreviousQuestion() {
        conversation.setCourseId(null);
        when(store.requireOwned(100L, 1003L)).thenReturn(conversation);
        when(store.previousQuestion(100L, 101L)).thenReturn(QUESTION);
        when(llm.chatStream(anyList())).thenReturn(Flux.just("用 CAS 加桶级锁"));

        service.ask(STUDENT, new AskRequest(100L, "那 ConcurrentHashMap 呢", 2007L)).blockLast(Duration.ofSeconds(5));

        verify(store, never()).create(anyLong(), anyString(), any());
        verify(embedding).embed("那 ConcurrentHashMap 呢\n" + QUESTION);
        // 已有会话的范围以会话为准，请求里的 courseId=2007 被忽略
        verify(knowledge).retrieveScored(anyString(), anyList(), eq(8), isNull());
    }

    @Test
    void cancellingSavesWhatWasGeneratedAsStoppedAndReleasesTheSlot() throws Exception {
        when(llm.chatStream(anyList())).thenReturn(Flux.interval(Duration.ofMillis(50)).map(i -> "片" + i));
        CountDownLatch firstDelta = new CountDownLatch(1);

        Disposable subscription = service.ask(STUDENT, new AskRequest(null, "讲讲线程池", null))
                .subscribe(event -> {
                    if ("delta".equals(event.event())) {
                        firstDelta.countDown();
                    }
                });
        assertThat(firstDelta.await(5, TimeUnit.SECONDS)).isTrue();
        subscription.dispose();

        verify(store, timeout(3000)).saveAssistantMessage(anyLong(), eq(conversation), startsWith("片"),
                eq(MessageStatus.STOPPED), anyList(), eq(List.of()), anyLong());
        verify(store, never()).saveAssistantMessage(anyLong(), any(), anyString(), eq(MessageStatus.COMPLETED),
                anyList(), anyList(), anyLong());
        assertThat(slots.tryAcquire(1003L)).isTrue();
    }
}
```

- [ ] **Step 3: 写失败测试（接口）**

`ConversationStreamControllerTest.java`：

```java
package com.zhixue.ai.controller;

import com.zhixue.ai.chat.ConversationChatService;
import com.zhixue.ai.chat.Viewer;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.common.security.config.SecurityAccessAspect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ConversationStreamControllerTest {

    private static final String BODY = "{\"question\":\"HashMap 为什么线程不安全\"}";

    private final ConversationChatService chatService = mock(ConversationChatService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new ConversationStreamController(chatService));
        factory.setProxyTargetClass(true);
        factory.addAspect(new SecurityAccessAspect());
        mockMvc = MockMvcBuilders.standaloneSetup((Object) factory.getProxy())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static MockHttpServletRequestBuilder asStudent(MockHttpServletRequestBuilder builder) {
        return builder.requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                .header(InternalAccessConstants.USER_ID_HEADER, "1003")
                .header(InternalAccessConstants.USER_ROLES_HEADER, "STUDENT")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
    }

    @Test
    void streamsServerSentEventsWithProxyBufferingDisabled() throws Exception {
        when(chatService.ask(eq(new Viewer(1003L, false)), any())).thenReturn(Flux.just(
                ServerSentEvent.builder("{\"conversationId\":\"100\"}").event("meta").build(),
                ServerSentEvent.builder("{\"text\":\"答\"}").event("delta").build(),
                ServerSentEvent.builder("{\"recommendations\":[]}").event("done").build()));

        MvcResult result = mockMvc.perform(asStudent(post("/conversations/messages")))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(5000);

        MockHttpServletResponse response = result.getResponse();
        assertThat(response.getContentType()).startsWith("text/event-stream");
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).contains("event:meta").contains("event:delta").contains("event:done");
        assertThat(body.indexOf("event:meta")).isLessThan(body.indexOf("event:done"));
    }

    @Test
    void errorBeforeStreamingIsPlainJsonWithCodeInBody() throws Exception {
        when(chatService.ask(any(), any())).thenThrow(new ServiceException(HttpStatus.CONFLICT, "上一个回答还在生成"));

        // 前端同时接受两种类型（见学生网页版计划任务 4）：流前错误仍须按 JSON 返回，不能 406
        mockMvc.perform(asStudent(post("/conversations/messages")).accept("text/event-stream, application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.msg").value("上一个回答还在生成"));
    }

    /** 反过来：Accept 里同时有 application/json 时，成功路径仍必须是 text/event-stream */
    @Test
    void streamsEvenWhenClientAlsoAcceptsJson() throws Exception {
        when(chatService.ask(eq(new Viewer(1003L, false)), any())).thenReturn(Flux.just(
                ServerSentEvent.builder("{\"text\":\"答\"}").event("delta").build()));

        MvcResult result = mockMvc.perform(asStudent(post("/conversations/messages"))
                        .accept("text/event-stream, application/json"))
                .andExpect(request().asyncStarted())
                .andReturn();
        result.getAsyncResult(5000);

        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(post("/conversations/messages").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(jsonPath("$.code").value(401));
    }
}
```

- [ ] **Step 4: 运行确认失败**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -Dtest='PromptAssemblerTest,ConversationChatServiceTest,ConversationStreamControllerTest' -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | grep -E "cannot find symbol|找不到符号" | head -3
```

Expected: 编译失败。

- [ ] **Step 5: 实现小组件**

`AiChatController.java` 中把：

```java
    static final String DEFAULT_SYSTEM_PROMPT =
```

改为：

```java
    /** 课程答疑的默认系统指令；会话式答疑（PromptAssembler）共用同一份 */
    public static final String DEFAULT_SYSTEM_PROMPT =
```

`GenerationSlots.java`：

```java
package com.zhixue.ai.chat;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「同一用户同时只能有一个回答在生成」的占位。本地模型只有一块 GPU，排队会相互拖慢。
 *
 * <p>只在单个 AI 服务实例的内存中有效；多实例部署前须改为 Redis SET NX + 过期时间（设计文档 9 节）。</p>
 */
@Component
public class GenerationSlots {

    private final Set<Long> busy = ConcurrentHashMap.newKeySet();

    /** 原子占位；已有进行中的回答时返回 false */
    public boolean tryAcquire(Long userId) {
        return busy.add(userId);
    }

    public void release(Long userId) {
        busy.remove(userId);
    }
}
```

`AskRequest.java`：

```java
package com.zhixue.ai.chat;

/**
 * 提问请求。ID 在 JSON 里是字符串，Jackson 会转成 Long。
 *
 * @param conversationId 为空表示新建会话
 * @param courseId       限定课程提问；只在新建会话时生效，已有会话以会话保存的范围为准
 */
public record AskRequest(Long conversationId, String question, Long courseId) {
}
```

`PromptAssembler.java`：

```java
package com.zhixue.ai.chat;

import com.zhixue.ai.controller.AiChatController;
import com.zhixue.ai.domain.entity.PromptTemplate;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.ai.service.LlmService.ChatMessage;
import com.zhixue.ai.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 系统指令 + 最近 3 轮问答 + 本轮【课程资料】与【问题】 */
@Component
@RequiredArgsConstructor
public class PromptAssembler {

    public static final int HISTORY_ROUNDS = 3;
    static final int MAX_HISTORY_CHARS = 800;
    static final int CONTEXT_CHUNKS = 4;

    private final PromptTemplateService promptTemplateService;

    public List<ChatMessage> build(List<ConversationStore.Turn> history, List<RetrievedChunk> context, String question) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemPrompt()));
        List<ConversationStore.Turn> recent = history.size() <= HISTORY_ROUNDS ? history
                : history.subList(history.size() - HISTORY_ROUNDS, history.size());
        for (ConversationStore.Turn turn : recent) {
            messages.add(new ChatMessage("user", truncate(turn.question())));
            messages.add(new ChatMessage("assistant", truncate(turn.answer())));
        }
        messages.add(new ChatMessage("user", userPrompt(context, question)));
        return messages;
    }

    /** 进提示词的资料：检索命中的前 4 条（其余 4 条只参与推荐） */
    public List<RetrievedChunk> contextOf(List<RetrievedChunk> hits) {
        return hits.stream().limit(CONTEXT_CHUNKS).toList();
    }

    /** 检索用的问句：带上一个问题，才能理解「那它和 HashMap 有什么区别」；只带上一问，避免话题漂移 */
    public static String retrievalQuery(String question, String previousQuestion) {
        return StringUtils.hasText(previousQuestion) ? question + "\n" + previousQuestion : question;
    }

    private String systemPrompt() {
        PromptTemplate template = promptTemplateService.getByCode("COURSE_QA");
        if (template == null || !StringUtils.hasText(template.getContent())) {
            return AiChatController.DEFAULT_SYSTEM_PROMPT;
        }
        return template.getContent().replace("{{context}}", "").replace("{{question}}", "").trim();
    }

    private static String userPrompt(List<RetrievedChunk> context, String question) {
        StringBuilder sb = new StringBuilder("【课程资料】\n");
        if (context.isEmpty()) {
            sb.append("（未检索到相关资料）\n");
        } else {
            for (int i = 0; i < context.size(); i++) {
                RetrievedChunk chunk = context.get(i);
                sb.append(i + 1).append(". 《").append(chunk.title()).append("》").append(chunk.content()).append('\n');
            }
        }
        return sb.append("\n【问题】\n").append(question).toString();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_HISTORY_CHARS ? text : text.substring(0, MAX_HISTORY_CHARS) + "…";
    }
}
```

`ChatEvents.java`：

```java
package com.zhixue.ai.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixue.ai.domain.entity.AiConversation;
import org.springframework.http.codec.ServerSentEvent;

import java.util.List;

/** SSE 事件载荷。由这里自行序列化成 JSON 字符串，保证 ID 一律为字符串 */
final class ChatEvents {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Meta(String conversationId, String messageId, String title) {
    }

    record Delta(String text) {
    }

    record Done(String messageId, List<Source> sources, String notice, List<RecommendationCard> recommendations) {
    }

    record Failure(int code, String msg) {
    }

    private ChatEvents() {
    }

    static ServerSentEvent<String> meta(AiConversation conversation, long messageId) {
        return sse("meta", new Meta(String.valueOf(conversation.getId()), String.valueOf(messageId),
                conversation.getTitle()));
    }

    static ServerSentEvent<String> delta(String text) {
        return sse("delta", new Delta(text));
    }

    static ServerSentEvent<String> done(long messageId, List<Source> sources, String notice,
                                        List<RecommendationCard> recommendations) {
        return sse("done", new Done(String.valueOf(messageId), sources, notice, recommendations));
    }

    static ServerSentEvent<String> failure(int code, String msg) {
        return sse("error", new Failure(code, msg));
    }

    private static ServerSentEvent<String> sse(String event, Object payload) {
        try {
            return ServerSentEvent.builder(MAPPER.writeValueAsString(payload)).event(event).build();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SSE 事件序列化失败", e);
        }
    }
}
```

- [ ] **Step 6: 实现编排**

`ConversationChatService.java`：

```java
package com.zhixue.ai.chat;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.zhixue.ai.domain.entity.AiConversation;
import com.zhixue.ai.domain.entity.AiMessage;
import com.zhixue.ai.service.EmbeddingService;
import com.zhixue.ai.service.KnowledgeBaseService;
import com.zhixue.ai.service.KnowledgeBaseService.RetrievedChunk;
import com.zhixue.ai.service.LlmService;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 一次提问的编排：校验 → 占位 → 建会话/存问题 → meta → 检索 → 流式 delta → 推荐 → 落库 → done。
 *
 * <p>同步部分在请求线程上完成，失败直接抛 ServiceException，由全局异常处理器按普通 JSON 返回（流前错误）。
 * 流开始后不再抛异常：失败以 error 事件结束；学生点停止或断线则保存为「已停止」。
 * 三种结束方式都只落库一次、只释放一次占位。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationChatService {

    static final int MAX_QUESTION_LENGTH = 500;
    static final int RETRIEVE_TOP_K = 8;
    static final String RETRIEVAL_NOTICE = "课程资料暂时无法检索，本次回答未参考课程资料";

    private final ConversationStore store;
    private final GenerationSlots slots;
    private final PromptAssembler assembler;
    private final EmbeddingService embeddingService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final LlmService llmService;
    private final CourseRecommender recommender;

    /** 同一次检索的结果：问句向量（供课时匹配复用）、命中、降级提示 */
    private record Retrieval(List<Float> vector, List<RetrievedChunk> hits, String notice) {
    }

    /** 一次生成的状态；回答缓冲由模型返回线程追加、由取消线程读取，因此用 StringBuffer */
    private static final class Generation {
        private final AiConversation conversation;
        private final long assistantId;
        private final String question;
        private final List<ConversationStore.Turn> history;
        private final String previousQuestion;
        private final long startNanos = System.nanoTime();
        private final StringBuffer answer = new StringBuffer();
        private final AtomicBoolean finished = new AtomicBoolean();

        private Generation(AiConversation conversation, long assistantId, String question,
                           List<ConversationStore.Turn> history, String previousQuestion) {
            this.conversation = conversation;
            this.assistantId = assistantId;
            this.question = question;
            this.history = history;
            this.previousQuestion = previousQuestion;
        }

        private long costMs() {
            return (System.nanoTime() - startNanos) / 1_000_000;
        }
    }

    /**
     * @param viewer 必须在请求线程上取得
     * @throws ServiceException 流前错误：400 问题为空/过长、404 会话不存在、409 已有回答在生成、500 保存提问失败
     */
    public Flux<ServerSentEvent<String>> ask(Viewer viewer, AskRequest request) {
        String question = request == null || request.question() == null ? "" : request.question().strip();
        if (question.isEmpty()) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "问题不能为空");
        }
        if (question.length() > MAX_QUESTION_LENGTH) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "问题不能超过 " + MAX_QUESTION_LENGTH + " 字");
        }
        AiConversation existing = request.conversationId() == null ? null
                : store.requireOwned(request.conversationId(), viewer.userId());
        // 写任何数据之前原子占位：被 409 拒绝的请求不能留下空会话或没有回答的问题
        if (!slots.tryAcquire(viewer.userId())) {
            throw new ServiceException(HttpStatus.CONFLICT, "上一个回答还在生成");
        }
        Generation generation;
        try {
            generation = prepare(viewer, existing, request.courseId(), question);
        } catch (RuntimeException e) {
            // 占位后、流开始前的任何异常都必须释放，否则这个学生之后每次提问都是 409
            slots.release(viewer.userId());
            if (e instanceof ServiceException se) {
                throw se;
            }
            log.error("保存提问失败 userId={}", viewer.userId(), e);
            throw new ServiceException(HttpStatus.INTERNAL_SERVER_ERROR, "发送失败，请重试", e);
        }
        AtomicBoolean released = new AtomicBoolean();
        return stream(viewer, generation)
                // 学生点停止或断线：取消订阅即关闭到 Ollama 的连接；已生成部分保存为「已停止」
                .doOnCancel(() -> Schedulers.boundedElastic().schedule(() -> stop(generation)))
                .doFinally(signal -> {
                    if (released.compareAndSet(false, true)) {
                        slots.release(viewer.userId());
                    }
                });
    }

    private Generation prepare(Viewer viewer, AiConversation existing, Long courseId, String question) {
        AiConversation conversation = existing != null ? existing : store.create(viewer.userId(), question, courseId);
        AiMessage userMessage = store.appendUserMessage(conversation, question);
        List<ConversationStore.Turn> history = store.completedHistory(
                conversation.getId(), userMessage.getId(), PromptAssembler.HISTORY_ROUNDS);
        String previous = store.previousQuestion(conversation.getId(), userMessage.getId());
        // 回答的 ID 先分配好：meta 事件就要告诉前端；雪花 ID 递增，保证排在这条问题之后
        return new Generation(conversation, IdWorker.getId(), question, history, previous);
    }

    private Flux<ServerSentEvent<String>> stream(Viewer viewer, Generation g) {
        Flux<ServerSentEvent<String>> answer = Mono.fromCallable(() -> retrieve(g))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(r -> llmService.chatStream(assembler.build(g.history, assembler.contextOf(r.hits()), g.question))
                        .map(text -> {
                            g.answer.append(text);
                            return ChatEvents.delta(text);
                        })
                        // 推荐要调 Feign、算向量，落库是阻塞的 JDBC：都放到 boundedElastic，不占模型返回线程
                        .concatWith(Mono.fromCallable(() -> complete(viewer, g, r))
                                .subscribeOn(Schedulers.boundedElastic())))
                .onErrorResume(e -> Mono.fromCallable(() -> fail(g, e)).subscribeOn(Schedulers.boundedElastic()));
        return Flux.just(ChatEvents.meta(g.conversation, g.assistantId)).concatWith(answer);
    }

    /** 检索失败（ES 或向量模型不可用）不中断回答：不带资料回答、不出推荐，并在 done 里说明 */
    private Retrieval retrieve(Generation g) {
        String query = PromptAssembler.retrievalQuery(g.question, g.previousQuestion);
        try {
            List<Float> vector = embeddingService.embed(query);
            List<RetrievedChunk> hits = knowledgeBaseService.retrieveScored(
                    query, vector, RETRIEVE_TOP_K, g.conversation.getCourseId());
            return new Retrieval(vector, hits, null);
        } catch (RuntimeException e) {
            log.warn("课程资料检索失败，本次不参考资料 conversationId={}：{}", g.conversation.getId(), e.getMessage());
            return new Retrieval(List.of(), List.of(), RETRIEVAL_NOTICE);
        }
    }

    /** 正常结束；若已被取消（stop 抢先落库）返回 null，Mono.fromCallable 视为空 */
    private ServerSentEvent<String> complete(Viewer viewer, Generation g, Retrieval r) {
        if (!g.finished.compareAndSet(false, true)) {
            return null;
        }
        List<Source> sources = assembler.contextOf(r.hits()).stream()
                .map(RetrievedChunk::title).distinct().map(Source::new).toList();
        CourseRecommender.Result recommendation = r.hits().isEmpty()
                ? CourseRecommender.Result.empty()
                : recommender.recommend(r.hits(), r.vector(), viewer);
        try {
            store.saveAssistantMessage(g.assistantId, g.conversation, g.answer.toString(), MessageStatus.COMPLETED,
                    sources, recommendation.stored(), g.costMs());
        } catch (RuntimeException e) {
            // 学生已经看到完整回答，不能因落库失败改成报错；历史里这一问会显示「未完成回答」
            log.error("保存回答失败 conversationId={} messageId={}", g.conversation.getId(), g.assistantId, e);
        }
        return ChatEvents.done(g.assistantId, sources, r.notice(), recommendation.cards());
    }

    private ServerSentEvent<String> fail(Generation g, Throwable e) {
        int code = e instanceof ServiceException se ? se.getCode() : HttpStatus.SERVICE_UNAVAILABLE;
        String msg = e instanceof ServiceException ? e.getMessage() : "AI 服务暂不可用，请稍后再试";
        if (!(e instanceof ServiceException)) {
            log.error("生成回答失败 conversationId={}", g.conversation.getId(), e);
        }
        if (g.finished.compareAndSet(false, true)) {
            saveQuietly(g, MessageStatus.FAILED);
        }
        return ChatEvents.failure(code, msg);
    }

    private void stop(Generation g) {
        if (g.finished.compareAndSet(false, true)) {
            saveQuietly(g, MessageStatus.STOPPED);
        }
    }

    private void saveQuietly(Generation g, int status) {
        try {
            store.saveAssistantMessage(g.assistantId, g.conversation, g.answer.toString(), status,
                    List.of(), List.of(), g.costMs());
        } catch (RuntimeException e) {
            log.error("保存回答失败 messageId={} status={}", g.assistantId, status, e);
        }
    }
}
```

- [ ] **Step 7: 实现接口**

`ConversationStreamController.java`：

```java
package com.zhixue.ai.controller;

import com.zhixue.ai.chat.AskRequest;
import com.zhixue.ai.chat.ConversationChatService;
import com.zhixue.ai.chat.Viewer;
import com.zhixue.common.security.annotation.RequireLogin;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * 流式提问。返回 Flux&lt;ServerSentEvent&gt; 时 Spring MVC 自动按 text/event-stream 输出。
 *
 * <p>不声明 produces：流前错误由全局异常处理器按 JSON 返回，声明了 text/event-stream 会导致 JSON 无法输出。
 * 前端先看 Content-Type 再决定读流还是读 JSON（以 body.code 为准）。</p>
 */
@RestController
@RequestMapping("/conversations")
@RequiredArgsConstructor
public class ConversationStreamController {

    private final ConversationChatService chatService;

    @RequireLogin
    @PostMapping("/messages")
    public Flux<ServerSentEvent<String>> ask(@RequestBody AskRequest request, HttpServletResponse response) {
        // 身份必须在请求线程上取：进入流式处理后读不到网关注入的请求头
        Flux<ServerSentEvent<String>> events = chatService.ask(Viewer.current(), request);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        // 经 Nginx 部署时关闭代理缓冲，否则整段回答会被攒到最后才发出
        response.setHeader("X-Accel-Buffering", "no");
        return events;
    }
}
```

- [ ] **Step 8: 运行确认通过**

```bash
mvn test -q -pl zhixue-modules/zhixue-ai -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -3
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 9: 部署并经网关实测一次**

```bash
cd /Users/jax/Projects/zhixue-cloud && source scripts/local/common.sh && load_env
mvn -q -DskipTests install -pl zhixue-modules/zhixue-ai -am && compose up -d --force-recreate --no-deps ai
for i in $(seq 1 40); do docker logs zhixue-local-ai --since 2m 2>&1 | grep -q "Started AiApplication" && break; sleep 3; done
GW="http://127.0.0.1:${ZHIXUE_GATEWAY_PORT:-19001}"
T=$(curl -s -X POST "$GW/auth/login" -H 'Content-Type: application/json' \
  -d '{"loginType":"wechat","wechatCode":"plan-check"}' | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["token"])')
curl -sN -m 120 -X POST "$GW/ai/conversations/messages" -H "Authorization: Bearer $T" \
  -H 'Content-Type: application/json' -d '{"question":"HashMap 为什么线程不安全"}' -D /tmp/zx_sse_headers.txt | head -c 1500; echo
grep -iE "content-type|x-accel-buffering|cache-control" /tmp/zx_sse_headers.txt
```

Expected: 先看到 `event:meta`，随后逐段出现多个 `event:delta`，最后是 `event:done`，其中 `recommendations` 含 `"courseId":"2007"`；响应头含 `text/event-stream`、`X-Accel-Buffering: no`、`Cache-Control: no-cache`。

- [ ] **Step 10: 提交**

```bash
git add zhixue-modules/zhixue-ai/src
git commit -m "feat(ai): 流式提问接口——占位先于写库、同一次检索出回答与推荐、停止/断线/失败各自落库"
```

---

### Task 10: 验收脚本、三端旅程与文档回填

**Files:**
- Create: `scripts/local/lib/ai-sse.mjs`
- Create: `scripts/local/ai-reco-check.mjs`
- Create: `scripts/local/ai-reco-check.sh`
- Modify: `scripts/local/journeys.mjs`
- Modify: `scripts/local/contract-check.sh:83`
- Modify: `README.md`（「本地回归」一节）
- Modify: `docs/superpowers/specs/2026-10-20-student-web-ai-chat-design.md`（4.4 回填实测）

**Interfaces:**
- Consumes: 任务 9 的 SSE 接口与任务 8 的会话接口
- Produces: `askStream(gateway, token, body)` → `{ events: [{event, data, at}], headers, error? }`；`studentToken(gateway, code)`

- [ ] **Step 1: 共用的 SSE 读取与登录**

`scripts/local/lib/ai-sse.mjs`：

```js
// 本地脚本共用：读取 AI 对话的 SSE 流。
// Spring 输出形如 "event:delta\ndata:{...}\n\n"（冒号后没有空格），带空格的写法也兼容。

/** 逐个产出 { event, data }；data 能按 JSON 解析时为对象，否则为原文 */
export async function* readSse(response) {
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  for (;;) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    let match
    while ((match = /\r?\n\r?\n/.exec(buffer))) {
      const block = buffer.slice(0, match.index)
      buffer = buffer.slice(match.index + match[0].length)
      const event = parseBlock(block)
      if (event) yield event
    }
  }
  const tail = parseBlock(buffer + decoder.decode())
  if (tail) yield tail
}

function parseBlock(block) {
  let event = 'message'
  const data = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
  }
  if (data.length === 0) return null
  const raw = data.join('\n')
  try {
    return { event, data: JSON.parse(raw) }
  } catch {
    return { event, data: raw }
  }
}

/**
 * 提问并读完整个流。每个事件带 at（距发出请求的毫秒数），用于检查首字与推荐的时延。
 * 流前错误（响应不是 text/event-stream）时 events 为空、error 为响应 JSON（以 code 为准）。
 */
export async function askStream(gateway, token, body, timeoutMs = 150000) {
  const startedAt = Date.now()
  const response = await fetch(`${gateway}/ai/conversations/messages`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(timeoutMs)
  })
  const type = response.headers.get('content-type') || ''
  if (!type.includes('text/event-stream')) {
    const error = await response.json().catch(() => ({ code: response.status }))
    return { events: [], headers: response.headers, error }
  }
  const events = []
  for await (const event of readSse(response)) events.push({ ...event, at: Date.now() - startedAt })
  return { events, headers: response.headers }
}

/** 学生端本地联调：微信沙箱登录（映射到 student 账号），不需要验证码 */
export async function studentToken(gateway, code = `script-${Date.now()}`) {
  const response = await fetch(`${gateway}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ loginType: 'wechat', wechatCode: code })
  })
  const json = await response.json()
  if (!json.data?.token) {
    throw new Error(`学生登录失败：${json.msg}（local.env 需设置 ZHIXUE_AUTH_WECHAT_MODE=sandbox）`)
  }
  return json.data.token
}
```

- [ ] **Step 2: 推荐效果验收脚本**

`scripts/local/ai-reco-check.mjs`：

```js
// 推荐效果验收（设计文档 5.3 的 5 个用例）：
// 1) 直接问 ES 与 Ollama，打印每门课、每个课时的真实余弦分数，便于校准门槛；
// 2) 经网关走真实的流式对话，校验推荐结果与时延目标（首字 ≤ 1.5s、回答结束到推荐 ≤ 0.5s）。
import { execFileSync } from 'node:child_process'
import { askStream, studentToken } from './lib/ai-sse.mjs'

const GW = `http://127.0.0.1:${process.env.ZHIXUE_GATEWAY_PORT || 19001}`
const ES = `http://127.0.0.1:${process.env.ZHIXUE_ES_PORT || 19200}`
const OLLAMA = 'http://127.0.0.1:11434'
const EMBED_MODEL = process.env.ZHIXUE_AI_EMBEDDING_MODEL || 'qwen3-embedding:0.6b'
const COURSE_MIN = Number(process.env.ZHIXUE_AI_RECO_COURSE_MIN_COS || 0.55)
const LESSON_MIN = Number(process.env.ZHIXUE_AI_RECO_LESSON_MIN_COS || 0.5)

const CASES = [
  { question: 'HashMap 为什么线程不安全', courses: ['2007', '2006'], lessons: { 2007: ['4024', '4022'], 2006: ['4018', '4019'] } },
  { question: '线程池参数怎么设', courses: ['2007'], lessons: { 2007: ['4025'] } },
  { question: 'String 为什么是不可变的', courses: ['2005'], lessons: { 2005: ['4015'] } },
  { question: 'Vue 3 组合式 API 怎么组织页面逻辑', courses: [] }, // 2002 过门槛但已下架，验证下架过滤
  { question: '今天中午吃什么', courses: [] }
]

const compose = (...args) =>
  execFileSync('docker', ['compose', '--env-file', process.env.ENV_FILE, '-f', process.env.COMPOSE_FILE, ...args]).toString()
const sql = (query) =>
  compose('exec', '-T', 'mysql', 'mysql', '--default-character-set=utf8mb4', '-uroot',
    `-p${process.env.ZHIXUE_DB_PASSWORD}`, '-N', '-e', query)
    .split('\n').map((line) => line.trim()).filter((line) => line && !line.includes('Warning'))

let failures = 0
const check = (label, ok, detail = '') => {
  if (!ok) failures++
  console.log(`${ok ? '[ok]' : '[fail]'} ${label}${ok ? '' : `：${detail}`}`)
}

async function postJson(url, body) {
  const response = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
  if (!response.ok) throw new Error(`${url} -> HTTP ${response.status}`)
  return response.json()
}
const embed = async (texts) => (await postJson(`${OLLAMA}/api/embed`, { model: EMBED_MODEL, input: texts })).embeddings
const cosine = (a, b) => {
  let dot = 0, na = 0, nb = 0
  for (let i = 0; i < a.length; i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
  return dot / Math.sqrt(na * nb)
}

async function scoreReport() {
  console.log(`===== 实测分数（余弦；课程门槛 ${COURSE_MIN}，课时门槛 ${LESSON_MIN}）=====`)
  const lessons = sql('SELECT id, course_id, title FROM zhixue_course.section WHERE course_id IN (2005, 2006, 2007) AND deleted = 0 ORDER BY id;')
    .map((line) => { const [id, courseId, ...title] = line.split('\t'); return { id, courseId, title: title.join('\t') } })
  const titleVectors = await embed(lessons.map((l) => l.title))
  for (const c of CASES) {
    const [vector] = await embed([c.question])
    const result = await postJson(`${ES}/zhixue_kb/_search`, {
      knn: { field: 'vector', query_vector: vector, k: 8, num_candidates: 80, similarity: 0.45 },
      _source: ['chunkId', 'courseId']
    })
    const best = new Map()
    for (const hit of result.hits.hits) {
      if (hit._source.courseId == null) continue
      const id = String(hit._source.courseId)
      best.set(id, Math.max(best.get(id) ?? -1, 2 * hit._score - 1)) // ES _score 换算回余弦
    }
    console.log(`\n${c.question}`)
    if (best.size === 0) console.log('  （没有课程资料达到 0.45）')
    for (const [courseId, cos] of [...best.entries()].sort((a, b) => b[1] - a[1])) {
      const top = lessons.map((l, i) => ({ ...l, cos: cosine(vector, titleVectors[i]) }))
        .filter((l) => l.courseId === courseId).sort((a, b) => b.cos - a.cos).slice(0, 2)
        .map((l) => `${l.id}:${l.cos.toFixed(3)}`).join('  ')
      console.log(`  课程 ${courseId}  ${cos.toFixed(3)}${cos >= COURSE_MIN ? '' : '（低于门槛）'}  课时 ${top}`)
    }
  }
}

async function endToEnd() {
  console.log('\n===== 经网关的真实流式对话 =====')
  const token = await studentToken(GW, 'ai-reco-check')
  const created = []
  try {
    // 预热一次，避免把模型冷启动计入时延
    const warm = await askStream(GW, token, { question: '你好' })
    const warmMeta = warm.events.find((e) => e.event === 'meta')
    if (warmMeta) created.push(warmMeta.data.conversationId)
    for (const c of CASES) {
      const { events, error } = await askStream(GW, token, { question: c.question })
      if (error) { check(c.question, false, `流前错误 ${JSON.stringify(error)}`); continue }
      const meta = events.find((e) => e.event === 'meta')
      if (meta) created.push(meta.data.conversationId)
      const last = events.at(-1)
      if (last?.event !== 'done') { check(c.question, false, `没有以 done 结束：${JSON.stringify(last?.data)}`); continue }
      const deltas = events.filter((e) => e.event === 'delta')
      const firstDelta = deltas[0]?.at ?? Infinity
      const doneGap = last.at - (deltas.at(-1)?.at ?? last.at)
      check(`  首字 ${firstDelta}ms ≤ 1500ms`, firstDelta <= 1500, `${firstDelta}ms`)
      check(`  回答结束到推荐 ${doneGap}ms ≤ 500ms`, doneGap <= 500, `${doneGap}ms`)
      const cards = last.data.recommendations || []
      const courseIds = cards.map((card) => card.courseId)
      if (c.courses.length === 0) {
        check(`${c.question} → 不推荐`, cards.length === 0, `实际推荐了 ${courseIds.join(',')}`)
        continue
      }
      check(`${c.question} → 推荐 ${c.courses.join('、')}`, c.courses.every((id) => courseIds.includes(id)), `实际 ${courseIds.join(',')}`)
      for (const [courseId, expected] of Object.entries(c.lessons)) {
        const lessonIds = (cards.find((card) => card.courseId === courseId)?.lessons || []).map((l) => l.lessonId)
        check(`  课程 ${courseId} 标注了 ${expected.join(' 或 ')}`, lessonIds.some((id) => expected.includes(id)), `实际 ${lessonIds.join(',') || '无'}`)
      }
    }
  } finally {
    if (created.length) {
      const ids = created.join(',')
      sql(`DELETE FROM zhixue_ai.ai_message WHERE conversation_id IN (${ids}); DELETE FROM zhixue_ai.ai_conversation WHERE id IN (${ids});`)
      console.log(`[info] 已清理本次创建的 ${created.length} 个会话`)
    }
  }
}

const pending = sql('SELECT COUNT(*) FROM zhixue_ai.kb_chunk WHERE id BETWEEN 92011 AND 92018 AND es_id IS NULL;')[0]
check('8 篇 Java 资料都已写入向量索引', pending === '0',
  `仍有 ${pending} 个分片未入索引：先执行 sql/upgrade 下的增量脚本，再重启 AI 服务`)
await scoreReport()
await endToEnd()
console.log(failures ? `\n[fail] 推荐效果验收：${failures} 项失败` : '\n[ok] 推荐效果验收全部通过')
process.exit(failures ? 1 : 0)
```

`scripts/local/ai-reco-check.sh`：

```bash
#!/usr/bin/env bash
# 推荐效果验收（设计文档 5.3 的 5 个用例）：打印真实余弦分数，并经网关走一遍流式对话。
# 前置：服务已启动、Ollama 就绪、已执行 Java 课与知识资料的增量脚本、local.env 中 ZHIXUE_AUTH_WECHAT_MODE=sandbox。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
source "$SCRIPT_DIR/common.sh"
load_env

command -v node >/dev/null || { echo "[fail] 需要 Node.js 22+（使用内置 fetch）"; exit 1; }
export ENV_FILE COMPOSE_FILE
exec node "$SCRIPT_DIR/ai-reco-check.mjs"
```

```bash
chmod +x scripts/local/ai-reco-check.sh && node --check scripts/local/ai-reco-check.mjs && node --check scripts/local/lib/ai-sse.mjs && echo ok
```

Expected: `ok`。

- [ ] **Step 3: 三端旅程追加「学生 AI 对话」**

`scripts/local/journeys.mjs`：

1. 文件顶部导入后追加：

```js
import { askStream } from './lib/ai-sse.mjs'
```

2. `const created = { courseId: null, seckillId: null }` 改为：

```js
const created = { courseId: null, seckillId: null, conversations: [] }
```

3. 在 `check('学生不能上传知识库', upload.code, 403)` 之后插入：

```js
  console.log('===== 学生 AI 对话：流式回答 → 推荐卡片 → 追问 → 并发与隔离 =====')
  const first = await askStream(GW, student, { question: 'HashMap 为什么线程不安全？' })
  const conversationId = first.events.find((e) => e.event === 'meta')?.data?.conversationId
  if (conversationId) created.conversations.push(conversationId)
  const names = first.events.map((e) => e.event)
  check('流式事件：先 meta、多个 delta、最后 done',
    [names[0], names.filter((n) => n === 'delta').length > 1, names.at(-1)], ['meta', true, 'done'])
  check('流式响应关闭代理缓冲', first.headers.get('x-accel-buffering'), 'no')
  const cards = first.events.at(-1)?.data?.recommendations || []
  check('回答下方推荐了《Java 并发编程实战》', cards.some((c) => c.courseId === '2007'), true)
  const follow = await askStream(GW, student, { conversationId, question: '那 ConcurrentHashMap 是怎么解决的？' })
  check('同一会话追问正常完成', follow.events.at(-1)?.event, 'done')
  const history = await api('GET', `/ai/conversations/${conversationId}/messages`, student)
  check('历史是两问两答', (history.data || []).map((m) => m.role), ['user', 'assistant', 'user', 'assistant'])
  check('老师读不到学生的会话（404）', (await api('GET', `/ai/conversations/${conversationId}/messages`, teacher)).code, 404)
  // 并发：第一个回答还在生成时再问，必须 409，且不留下任何会话
  const before = (await api('GET', '/ai/conversations?pageNum=1&pageSize=50', student)).data?.total
  const slow = askStream(GW, student, { question: '详细讲讲 HashMap 的扩容过程，分步骤说明' })
  await new Promise((resolve) => setTimeout(resolve, 400))
  const busy = await askStream(GW, student, { question: '第二个问题' })
  const slowResult = await slow
  const slowId = slowResult.events.find((e) => e.event === 'meta')?.data?.conversationId
  if (slowId) created.conversations.push(slowId)
  check('生成中再问返回 409', busy.error?.code, 409)
  const after = (await api('GET', '/ai/conversations?pageNum=1&pageSize=50', student)).data?.total
  check('被拒绝的提问没有留下会话', after - before, 1)
```

4. 在 `async function cleanup() {` 的下一行插入：

```js
  if (created.conversations.length) {
    const ids = created.conversations.join(',')
    compose('exec', '-T', 'mysql', 'mysql', '--default-character-set=utf8mb4', '-uroot',
      `-p${process.env.ZHIXUE_DB_PASSWORD}`, 'zhixue_ai', '-e',
      `DELETE FROM ai_message WHERE conversation_id IN (${ids}); DELETE FROM ai_conversation WHERE id IN (${ids});`)
  }
```

```bash
node --check scripts/local/journeys.mjs && echo ok
```

- [ ] **Step 4: 契约检查追加学生网页版目录**

`scripts/local/contract-check.sh` 第 83 行（`scan_dir "app-mp" ...`）之后追加一行（若网页版计划任务 1 已经加过，跳过本步）；目录不存在时 `scan_dir` 会直接返回，网页版建好目录后自动生效：

```bash
scan_dir "student-web" "$ROOT_DIR/zhixue-student-web/src"
```

- [ ] **Step 5: README 补充增量上线说明**

在 README「### 本地回归」一节末尾（`已知功能缺口与遗留风险见 …` 一行之后）追加：

````markdown

#### 已有数据的环境升级（不要执行 `db-init.sh`，它会清空全库）

```bash
source scripts/local/common.sh && load_env
for f in zhixue-modules/zhixue-course/src/main/resources/sql/upgrade/2026-10-20-java-courses.sql \
         zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-20-java-knowledge.sql \
         zhixue-modules/zhixue-ai/src/main/resources/sql/upgrade/2026-10-20-ai-conversation.sql; do
  docker exec -i zhixue-local-mysql mysql --default-character-set=utf8mb4 -uroot -p"$ZHIXUE_DB_PASSWORD" < "$f"
done
compose up -d --force-recreate --no-deps course ai   # AI 服务启动时会把新资料补进向量索引
bash scripts/local/ai-reco-check.sh                  # 推荐效果验收（5 个用例 + 实测分数）
```

增量脚本只插入缺失的行，可以重复执行。
````

- [ ] **Step 6: 运行验收并回填实测**

```bash
cd /Users/jax/Projects/zhixue-cloud
bash scripts/local/ai-reco-check.sh | tee /tmp/zx_reco_check.out
```

Expected: 最后一行 `[ok] 推荐效果验收全部通过`。

把输出中「实测分数」一节原样整理成表，追加到设计文档 4.4 末尾：

```markdown
**实测分数（YYYY-MM-DD，qwen3-embedding:0.6b，门槛 课程 0.55 / 课时 0.50）**

| 提问 | 课程（余弦） | 标注课时（余弦） | 结果 |
|---|---|---|---|
| （逐行填入 /tmp/zx_reco_check.out 的数值） | | | |
```

若检查点 A 调整过门槛，同步把 4.4 正文中的 0.55 / 0.50 改为实际取值，并在设计文档 11 节修订记录里加一行说明依据。

- [ ] **Step 7: 提交**

```bash
git add scripts/local README.md docs/superpowers/specs/2026-10-20-student-web-ai-chat-design.md
git commit -m "test(local): 推荐效果验收脚本与学生 AI 对话旅程；README 增量上线说明；回填实测门槛"
```

---

## 检查点 C：全量回归（后端完成的定义）

- [ ] 全部单元测试：

```bash
cd /Users/jax/Projects/zhixue-cloud && mvn test 2>&1 | grep -E "Tests run:.*Failures" | grep -v " in com" | awk -F'[:,]' '{r+=$2; f+=$4; e+=$6} END {print "run="r" failures="f" errors="e}'
```

Expected: `failures=0 errors=0`，总数比 344 多出本计划新增的用例。

- [ ] 构建并重新部署受影响的服务（`zhixue-api` 有新增，依赖它的服务一并重建）：

```bash
source scripts/local/common.sh && load_env
mvn -q -DskipTests install
compose up -d --force-recreate --no-deps course order marketing interaction ai
```

- [ ] 回归脚本全部通过：

```bash
ZHIXUE_SKIP_MEDIA=1 bash scripts/local/smoke.sh | tail -1
bash scripts/local/journeys.sh | tail -1
bash scripts/local/ai-reco-check.sh | tail -1
bash scripts/local/contract-check.sh | tail -1
```

Expected: 四个脚本都以 `[ok]` 结束。

- [ ] 增量脚本在本机再执行一遍仍无报错、数据不重复（可重复执行的证据）。
- [ ] 推送前做密钥扫描（与以往一致）；推送需用户确认。

---

## 自查记录（写完计划后对照设计文档逐项核对）

**1. 设计覆盖**

| 设计条目 | 落在 |
|---|---|
| 4.1 五个接口、他人会话 404、PUT 重命名 | 任务 8、9 |
| 4.2 事件格式、ID 为字符串、流前错误 HTTP 200 + body.code、SSE 响应头 | 任务 9 |
| 4.3 处理顺序 1–6（含占位时机、检索问句、一次检索两处使用、跨块 think、断线取消） | 任务 3、7、9 |
| 4.4 余弦刻度、课程/课时门槛、显式身份、课时向量缓存、故障隔离、实测回填 | 任务 3、4、5、6、检查点 A、任务 10 |
| 4.5 历史卡片按最新状态批量解析 | 任务 6（resolve）、任务 8 |
| 4.6 409、三种结束方式释放占位、超时 30/90/120 秒、时延目标 | 任务 7、9、10 |
| 5.1 两张表 | 任务 8 |
| 5.2 / 5.3 种子课、知识资料、验收用例 | 任务 1、10 |
| 5.4 上线路径（增量脚本、补齐 es_id） | 任务 1、2、8、10 |
| 6.1 异常矩阵 | 任务 7（503/504）、任务 9（400/404/409/500、notice、停止、失败）、任务 6（推荐失败） |
| 6.2 内容安全 | 后端部分：任务 7（think 过滤）；Markdown 渲染属学生端网页版计划 |
| 7.1 后端测试清单 | 各任务的测试；7.2 → 任务 10；7.3 → 学生端网页版计划 |

**2. 与草稿相比修正的问题**
- 种子校验测试的路径：模块工作目录是 `zhixue-modules/zhixue-ai`，课程种子要用 `../zhixue-course/...`。
- Lombok 生成的布尔 getter 是 `isCanStudyAll()` / `isPlayable()`，测试与实现统一使用。
- 增量脚本由种子段生成，并由测试保证逐字一致，避免「复制种子」这类无法验证的步骤。
- 推荐落库保留真实分数（草稿里写成了 0.0）。
- `ThinkFilter` 放在 `service.impl` 包内（只被 `LlmServiceImpl` 使用），不让 service 层依赖 chat 包。
- 流式提问放在独立的 `ConversationStreamController`，任务 8 的控制器与测试在任务 9 中无需改动。
- 编排里检索为空时不调用推荐器（被 mock 时会返回 null）。

**3. 类型一致性**：`ScoredChunk.cosine`、`RetrievedChunk(chunkId, courseId, cosine, title, content)`、`CourseOutline.lessons`、`LessonOutline.lessonId`、`RecommendationCard` / `StoredRecommendation` 的字符串 ID、`ConversationStore.Turn`、`MessageStatus` 常量在各任务中名称一致。

**4. 留给学生端网页版计划的部分**：`zhixue-student-web` 工程、SSE 客户端、Markdown 安全渲染、推荐卡片与课程详情、界面验收（设计文档 7.3）。该计划以本计划任务 9 的接口为契约。
