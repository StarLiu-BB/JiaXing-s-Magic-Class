USE zhixue_media;

DELETE FROM media_file;

INSERT INTO media_file
    (id, file_md5, file_name, bucket, object_name, file_url, file_type, file_size, status, remark, create_time, update_time, deleted)
VALUES
    (71001, 'demo-md5-course-cover', 'course-cover-demo.png', 'zhixue-media', 'images/course/course-cover-demo.png', 'http://127.0.0.1:19000/zhixue-media/images/course/course-cover-demo.png', 'image/png', 20480, 3, '联调示例图片', NOW(), NOW(), 0),
    (71002, 'demo-md5-video', 'demo-intro.mp4', 'zhixue-media', 'video/demo-intro.mp4', 'http://127.0.0.1:19000/zhixue-media/video/demo-intro.mp4', 'video/mp4', 10485760, 3, '联调示例视频', NOW(), NOW(), 0);
