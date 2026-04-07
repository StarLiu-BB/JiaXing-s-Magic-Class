USE zhixue_interaction;

DELETE FROM danmaku;
DELETE FROM interaction_course_like;
DELETE FROM interaction_course_favorite;
DELETE FROM interaction_course_stats;

INSERT INTO interaction_course_stats
    (id, course_id, like_count, favorite_count, create_time, update_time, deleted)
VALUES
    (81001, 2001, 18, 12, NOW(), NOW(), 0),
    (81002, 2002, 9, 6, NOW(), NOW(), 0);

INSERT INTO interaction_course_favorite
    (id, user_id, course_id, create_time, update_time, deleted)
VALUES
    (82001, 1003, 2001, NOW(), NOW(), 0);

INSERT INTO danmaku
    (id, room_id, user_id, content, time_point, audit_status, audit_remark, create_time, update_time, deleted)
VALUES
    (83001, 2001, 1003, '这一段老师讲得很清楚', 12, 1, 'seed', NOW(), NOW(), 0),
    (83002, 2001, 1002, '这里建议暂停记笔记', 36, 1, 'seed', NOW(), NOW(), 0);
