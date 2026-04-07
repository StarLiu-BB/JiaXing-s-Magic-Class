use zhixue_system;

delete from sys_role_menu;
delete from sys_user_role;
delete from sys_menu;
delete from sys_role;
delete from sys_user;
delete from sys_dict_data;
delete from sys_dict_type;

insert into sys_user (id, username, password, nickname, phone, email, avatar, status, remark, create_time, update_time, deleted) values
    (1001, 'admin', '$2y$10$5uw.N5.fSfU6AOdyVtZYcu9yTh9rwD9TCUWQTPc.oPRT4uSX1BAxm', '系统管理员', '13800000001', 'admin@zhixue.local', null, 0, '本地联调用管理员账号，密码 123456', now(), now(), 0),
    (1002, 'teacher', '$2y$10$oKU9cX6y2ioyirfvphSaI.Sv610me7FpHZQqfeqviLh/Et4zNsHAO', '示例教师', '13800000002', 'teacher@zhixue.local', null, 0, '本地联调用教师账号，密码 teacher123', now(), now(), 0),
    (1003, 'student', '$2y$10$TtPx8hlE78DGpE9zaFPub.kaYkQIjH6zimulknDupH0RguuxYMInK', '示例学生', '13800000003', 'student@zhixue.local', null, 0, '本地联调用学生账号，密码 student123', now(), now(), 0);

insert into sys_role (id, role_name, role_key, role_sort, status, remark, create_time, update_time, deleted) values
    (1, '超级管理员', 'ADMIN', 1, 0, '一期联调默认管理员角色', now(), now(), 0),
    (2, '教师', 'TEACHER', 2, 0, '具备课程维护权限', now(), now(), 0),
    (3, '学生', 'STUDENT', 3, 0, '学生端基础账号', now(), now(), 0);

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, order_num, visible, status, create_time, update_time, deleted) values
    (100, 0, '系统管理', '/system', 'Layout', null, 'Setting', 0, 1, 1, 0, now(), now(), 0),
    (110, 100, '用户管理', 'user', 'system/user/index', 'system:user:list', 'User', 1, 1, 1, 0, now(), now(), 0),
    (111, 110, '用户新增', '', '', 'system:user:add', 'Plus', 2, 1, 1, 0, now(), now(), 0),
    (112, 110, '用户编辑', '', '', 'system:user:edit', 'Edit', 2, 2, 1, 0, now(), now(), 0),
    (113, 110, '用户删除', '', '', 'system:user:remove', 'Delete', 2, 3, 1, 0, now(), now(), 0),
    (114, 110, '重置密码', '', '', 'system:user:resetPwd', 'Key', 2, 4, 1, 0, now(), now(), 0),
    (120, 100, '角色管理', 'role', 'system/role/index', 'system:role:list', 'UserFilled', 1, 2, 1, 0, now(), now(), 0),
    (121, 120, '角色编辑', '', '', 'system:role:edit', 'Edit', 2, 1, 1, 0, now(), now(), 0),
    (122, 120, '角色删除', '', '', 'system:role:remove', 'Delete', 2, 2, 1, 0, now(), now(), 0),
    (130, 100, '菜单管理', 'menu', 'system/menu/index', 'system:menu:list', 'Menu', 1, 3, 1, 0, now(), now(), 0),
    (131, 130, '菜单编辑', '', '', 'system:menu:edit', 'Edit', 2, 1, 1, 0, now(), now(), 0),
    (132, 130, '菜单删除', '', '', 'system:menu:remove', 'Delete', 2, 2, 1, 0, now(), now(), 0),
    (140, 100, '字典管理', 'dict', 'system/dict/index', 'system:dict:list', 'Document', 1, 4, 1, 0, now(), now(), 0),
    (200, 0, '课程管理', '/course', 'Layout', null, 'VideoPlay', 0, 2, 1, 0, now(), now(), 0),
    (210, 200, '课程列表', 'list', 'course/list/index', 'course:list', 'List', 1, 1, 1, 0, now(), now(), 0),
    (211, 210, '课程编辑', '', '', 'course:edit', 'Edit', 2, 1, 1, 0, now(), now(), 0),
    (212, 210, '课程删除', '', '', 'course:delete', 'Delete', 2, 2, 1, 0, now(), now(), 0),
    (213, 210, '课程下架', '', '', 'course:offline', 'Bottom', 2, 3, 1, 0, now(), now(), 0),
    (220, 200, '课程发布', 'publish', 'course/publish/index', 'course:publish', 'Plus', 1, 2, 1, 0, now(), now(), 0),
    (230, 200, '章节管理', 'chapter', 'course/chapter/index', 'course:chapter:list', 'Folder', 1, 3, 1, 0, now(), now(), 0),
    (240, 200, '分类管理', 'category', 'course/category/index', 'course:category:list', 'Grid', 1, 4, 1, 0, now(), now(), 0),
    (241, 240, '分类编辑', '', '', 'course:category:edit', 'Edit', 2, 1, 1, 0, now(), now(), 0);

insert into sys_user_role (id, user_id, role_id, create_time, update_time, deleted) values
    (10001, 1001, 1, now(), now(), 0),
    (10002, 1002, 2, now(), now(), 0),
    (10003, 1003, 3, now(), now(), 0);

insert into sys_role_menu (id, role_id, menu_id, create_time, update_time, deleted) values
    (20001, 1, 100, now(), now(), 0),
    (20002, 1, 110, now(), now(), 0),
    (20003, 1, 111, now(), now(), 0),
    (20004, 1, 112, now(), now(), 0),
    (20005, 1, 113, now(), now(), 0),
    (20006, 1, 114, now(), now(), 0),
    (20007, 1, 120, now(), now(), 0),
    (20008, 1, 121, now(), now(), 0),
    (20009, 1, 122, now(), now(), 0),
    (20010, 1, 130, now(), now(), 0),
    (20011, 1, 131, now(), now(), 0),
    (20012, 1, 132, now(), now(), 0),
    (20013, 1, 140, now(), now(), 0),
    (20014, 1, 200, now(), now(), 0),
    (20015, 1, 210, now(), now(), 0),
    (20016, 1, 211, now(), now(), 0),
    (20017, 1, 212, now(), now(), 0),
    (20018, 1, 213, now(), now(), 0),
    (20019, 1, 220, now(), now(), 0),
    (20020, 1, 230, now(), now(), 0),
    (20021, 1, 240, now(), now(), 0),
    (20022, 1, 241, now(), now(), 0),
    (21001, 2, 200, now(), now(), 0),
    (21002, 2, 210, now(), now(), 0),
    (21003, 2, 211, now(), now(), 0),
    (21004, 2, 213, now(), now(), 0),
    (21005, 2, 220, now(), now(), 0),
    (21006, 2, 230, now(), now(), 0),
    (21007, 2, 240, now(), now(), 0),
    (21008, 2, 241, now(), now(), 0);
