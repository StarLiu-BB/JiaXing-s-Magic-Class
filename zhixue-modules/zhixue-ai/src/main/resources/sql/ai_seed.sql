USE zhixue_ai;

DELETE FROM kb_chunk;
DELETE FROM kb_document;
DELETE FROM prompt_template;

INSERT INTO kb_document
    (id, title, source, tags, status, create_time, update_time, deleted)
VALUES
    (91001, 'Spring Cloud 微服务实战课程说明', 'course:2001', 'spring-cloud,微服务,网关', 1, NOW(), NOW(), 0);

INSERT INTO kb_chunk
    (id, document_id, chunk_index, content, es_id, create_time, update_time, deleted)
VALUES
    (92001, 91001, 0, '本课程覆盖注册中心、配置中心、网关、鉴权与服务治理等核心内容。', 'seed-course-2001-0', NOW(), NOW(), 0),
    (92002, 91001, 1, '适合有 Java 和 Spring Boot 基础的工程师，用于系统掌握微服务工程化实践。', 'seed-course-2001-1', NOW(), NOW(), 0);

INSERT INTO prompt_template
    (id, code, name, content, description, create_time, update_time, deleted)
VALUES
    (93001, 'COURSE_QA', '课程问答模板', '你是智学云课程助教。请基于给定课程资料回答，并在结尾说明引用来源。', '课程内问答默认模板', NOW(), NOW(), 0);
