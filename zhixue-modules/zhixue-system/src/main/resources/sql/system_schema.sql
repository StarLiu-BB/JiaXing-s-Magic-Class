create database if not exists zhixue_system default charset utf8mb4 collate utf8mb4_general_ci;
use zhixue_system;

drop table if exists sys_user;
create table sys_user (
    id           bigint primary key,
    username     varchar(64)  not null unique,
    password     varchar(128) not null,
    nickname     varchar(64),
    phone        varchar(20),
    email        varchar(64),
    avatar       varchar(255),
    status       tinyint default 1,
    remark       varchar(255),
    create_time  datetime,
    update_time  datetime,
    deleted      tinyint default 0
);

drop table if exists sys_role;
create table sys_role (
    id           bigint primary key,
    role_name    varchar(64) not null,
    role_key     varchar(64) not null,
    role_sort    int default 1,
    status       tinyint default 1,
    remark       varchar(255),
    create_time  datetime,
    update_time  datetime,
    deleted      tinyint default 0
);

drop table if exists sys_menu;
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
    status       tinyint default 1,
    create_time  datetime,
    update_time  datetime,
    deleted      tinyint default 0
);

drop table if exists sys_user_role;
create table sys_user_role (
    id          bigint primary key,
    user_id     bigint not null,
    role_id     bigint not null,
    create_time datetime,
    update_time datetime,
    deleted     tinyint default 0
);

drop table if exists sys_role_menu;
create table sys_role_menu (
    id          bigint primary key,
    role_id     bigint not null,
    menu_id     bigint not null,
    create_time datetime,
    update_time datetime,
    deleted     tinyint default 0
);

drop table if exists sys_dict_type;
create table sys_dict_type (
    id           bigint primary key,
    dict_name    varchar(64) not null,
    dict_type    varchar(64) not null unique,
    status       tinyint default 1,
    remark       varchar(255),
    create_time  datetime,
    update_time  datetime,
    deleted      tinyint default 0
);

drop table if exists sys_dict_data;
create table sys_dict_data (
    id           bigint primary key,
    dict_type    varchar(64) not null,
    dict_label   varchar(64) not null,
    dict_value   varchar(64) not null,
    dict_sort    int default 1,
    status       tinyint default 1,
    remark       varchar(255),
    create_time  datetime,
    update_time  datetime,
    deleted      tinyint default 0
);

