create database if not exists zhixue_system default charset utf8mb4 collate utf8mb4_general_ci;
use zhixue_system;

drop table if exists sys_role_menu;
drop table if exists sys_user_role;
drop table if exists sys_menu;
drop table if exists sys_role;
drop table if exists sys_user;
create table sys_user (
    id           bigint primary key,
    username     varchar(64)  not null unique,
    password     varchar(128) not null,
    nickname     varchar(64),
    phone        varchar(20),
    email        varchar(64),
    avatar       varchar(255),
    status       tinyint default 0 comment '0正常 1停用',
    remark       varchar(255),
    create_time  datetime,
    update_time  datetime,
    deleted      tinyint default 0
);

create table sys_role (
    id           bigint primary key,
    role_name    varchar(64) not null,
    role_key     varchar(64) not null,
    role_sort    int default 1,
    status       tinyint default 0 comment '0正常 1停用',
    remark       varchar(255),
    create_time  datetime,
    update_time  datetime,
    deleted      tinyint default 0
);

create table sys_menu (
    id           bigint primary key,
    parent_id    bigint default 0,
    menu_name    varchar(64) not null,
    path         varchar(128),
    component    varchar(128),
    perms        varchar(128),
    icon         varchar(64),
    menu_type    tinyint default 1,
    order_num    int default 1,
    visible      tinyint default 1,
    status       tinyint default 0 comment '0正常 1停用',
    create_time  datetime,
    update_time  datetime,
    deleted      tinyint default 0
);

create table sys_user_role (
    id          bigint primary key,
    user_id     bigint not null,
    role_id     bigint not null,
    create_time datetime,
    update_time datetime,
    deleted     tinyint default 0,
    unique key uk_sys_user_role (user_id, role_id),
    constraint fk_sys_user_role_user foreign key (user_id) references sys_user (id),
    constraint fk_sys_user_role_role foreign key (role_id) references sys_role (id)
);

create table sys_role_menu (
    id          bigint primary key,
    role_id     bigint not null,
    menu_id     bigint not null,
    create_time datetime,
    update_time datetime,
    deleted     tinyint default 0,
    unique key uk_sys_role_menu (role_id, menu_id),
    constraint fk_sys_role_menu_role foreign key (role_id) references sys_role (id),
    constraint fk_sys_role_menu_menu foreign key (menu_id) references sys_menu (id)
);

drop table if exists sys_dict_data;
drop table if exists sys_dict_type;

create table sys_dict_type (
    id           bigint primary key,
    dict_name    varchar(64) not null,
    dict_type    varchar(64) not null unique,
    status       tinyint default 0 comment '0正常 1停用',
    remark       varchar(255),
    create_time  datetime,
    update_time  datetime,
    deleted      tinyint default 0
);

create table sys_dict_data (
    id           bigint primary key,
    dict_type    varchar(64) not null,
    dict_label   varchar(64) not null,
    dict_value   varchar(64) not null,
    dict_sort    int default 1,
    status       tinyint default 0 comment '0正常 1停用',
    remark       varchar(255),
    create_time  datetime,
    update_time  datetime,
    deleted      tinyint default 0
);
