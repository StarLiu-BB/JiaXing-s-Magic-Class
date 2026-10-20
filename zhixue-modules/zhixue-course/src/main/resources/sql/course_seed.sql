use zhixue_course;

delete from section;
delete from chapter;
delete from course;
delete from course_category;

insert into course_category (id, parent_id, name, sort, status, create_time, update_time, deleted) values
    (100, 0, '编程开发', 1, 1, now(), now(), 0),
    (110, 100, 'Java 架构', 1, 1, now(), now(), 0),
    (120, 100, '前端工程', 2, 1, now(), now(), 0),
    (200, 0, '职业成长', 2, 1, now(), now(), 0),
    (210, 200, '教学方法', 1, 1, now(), now(), 0);

insert into course (
    id, title, subtitle, description, teacher_id, category_id, cover_url, poster_url,
    price, original_price, package_type, allow_preview, preview_lesson_count, validity_type, materials, faq,
    status, shelf_status, publish_time, create_time, update_time, deleted
) values
    (2001, 'Spring Cloud 微服务实战', '网关、认证、配置、治理一站式落地', '覆盖网关、认证、配置中心与服务治理的一体化课程。', 1002, 110,
     'https://dummyimage.com/600x340/0f766e/ffffff&text=Spring+Cloud', 'https://dummyimage.com/1200x480/0f766e/ffffff&text=Spring+Cloud+Poster',
     199.00, 299.00, 2, 1, 2, 1, '讲义PDF, 示例代码仓库', '适合有 Java 基础的开发者', 1, 1, now(), now(), now(), 0),
    (2002, 'Vue 3 管理后台实战', 'Vue3 + Pinia + Element Plus 全流程', '围绕 Vue 3、Pinia、Element Plus 完成管理端闭环开发。', 1002, 120,
     'https://dummyimage.com/600x340/1d4ed8/ffffff&text=Vue+3', 'https://dummyimage.com/1200x480/1d4ed8/ffffff&text=Vue3+Poster',
     149.00, 199.00, 1, 1, 1, 2, '源码模板, UI 设计稿', '购买后 365 天内可学习', 1, 0, now(), now(), now(), 0),
    (2003, '课程运营草稿演示', '发布与下架流程验证样例', '用于发布与下架链路验证的草稿课程。', 1002, 210,
     'https://dummyimage.com/600x340/f59e0b/ffffff&text=Draft', 'https://dummyimage.com/1200x480/f59e0b/ffffff&text=Draft+Poster',
     0.00, 99.00, 1, 0, 0, 1, null, null, 0, 0, null, now(), now(), 0),
    (2004, 'AI 助教工作流基础', '课程知识库与学习助手实践', '演示开关化 AI 能力在一期如何以沙箱模式接入。', 1002, 210,
     'https://dummyimage.com/600x340/0f172a/ffffff&text=AI+Tutor', 'https://dummyimage.com/1200x480/0f172a/ffffff&text=AI+Tutor+Poster',
     99.00, 129.00, 2, 1, 1, 1, 'Prompt 样例, 接口文档', '建议先完成课程导学后再学习 AI 篇章', 1, 1, now(), now(), now(), 0);

insert into chapter (id, course_id, title, order_num, create_time, update_time, deleted) values
    (3001, 2001, '课程导学', 1, now(), now(), 0),
    (3002, 2001, '认证与网关', 2, now(), now(), 0),
    (3003, 2004, '沙箱能力设计', 1, now(), now(), 0);

insert into section (id, course_id, chapter_id, title, video_url, duration, order_num, status, create_time, update_time, deleted) values
    (4001, 2001, 3001, '项目全景与环境准备', 'https://example.com/videos/intro.mp4', 900, 1, 1, now(), now(), 0),
    (4002, 2001, 3002, '登录认证链路串讲', 'https://example.com/videos/auth.mp4', 1200, 1, 1, now(), now(), 0),
    (4003, 2001, 3002, 'RBAC 权限模型落地', 'https://example.com/videos/rbac.mp4', 1320, 2, 1, now(), now(), 0),
    (4004, 2004, 3003, 'Stub / Sandbox / Real 三态设计', 'https://example.com/videos/sandbox.mp4', 780, 1, 1, now(), now(), 0);

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
