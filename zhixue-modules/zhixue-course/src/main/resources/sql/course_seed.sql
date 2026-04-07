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
