-- 已有数据的环境追加 8 篇 Java 知识。可重复执行：只插入缺失的行。
-- 只写 MySQL；AI 服务启动时会把 es_id 为空的分片补进向量索引。
-- 本文件由 ai_seed.sql 的 java-knowledge 段生成，勿手改。
USE zhixue_ai;

INSERT IGNORE INTO kb_document
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

INSERT IGNORE INTO kb_chunk
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
