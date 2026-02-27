create database if not exists zhixue_course default charset utf8mb4 collate utf8mb4_general_ci;
use zhixue_course;

drop table if exists course;
create table course (
    id            bigint primary key,
    title         varchar(128) not null,
    description   text,
    teacher_id    bigint,
    category_id   bigint,
    cover_url     varchar(255),
    price         decimal(10,2) default 0,
    status        tinyint default 0 comment '0草稿 1已发布',
    shelf_status  tinyint default 0 comment '0下架 1上架',
    publish_time  datetime,
    create_time   datetime,
    update_time   datetime,
    deleted       tinyint default 0
);

drop table if exists chapter;
create table chapter (
    id          bigint primary key,
    course_id   bigint not null,
    title       varchar(128) not null,
    order_num   int default 1,
    create_time datetime,
    update_time datetime,
    deleted     tinyint default 0
);

drop table if exists section;
create table section (
    id          bigint primary key,
    course_id   bigint not null,
    chapter_id  bigint not null,
    title       varchar(128) not null,
    video_url   varchar(255),
    duration    int,
    order_num   int default 1,
    status      tinyint default 0,
    create_time datetime,
    update_time datetime,
    deleted     tinyint default 0
);

drop table if exists course_category;
create table course_category (
    id          bigint primary key,
    parent_id   bigint default 0,
    name        varchar(64) not null,
    sort        int default 1,
    create_time datetime,
    update_time datetime,
    deleted     tinyint default 0
);

