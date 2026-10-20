-- 已有数据的环境追加 3 门 Java 种子课。可重复执行：只插入缺失的行。
-- 不得用 db-init.sh（会清空全库）。本文件由 course_seed.sql 的 java-courses 段生成，勿手改。
USE zhixue_course;

INSERT IGNORE INTO course (
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

INSERT IGNORE INTO chapter (id, course_id, title, order_num, create_time, update_time, deleted) values
    (3011, 2005, '语法与面向对象', 1, now(), now(), 0),
    (3012, 2005, '异常与常用类', 2, now(), now(), 0),
    (3013, 2006, '集合框架', 1, now(), now(), 0),
    (3014, 2006, 'HashMap 深入', 2, now(), now(), 0),
    (3015, 2006, '泛型', 3, now(), now(), 0),
    (3016, 2007, '线程基础', 1, now(), now(), 0),
    (3017, 2007, '锁与并发容器', 2, now(), now(), 0),
    (3018, 2007, '线程池', 3, now(), now(), 0);

INSERT IGNORE INTO section (id, course_id, chapter_id, title, video_url, duration, order_num, status, create_time, update_time, deleted) values
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
