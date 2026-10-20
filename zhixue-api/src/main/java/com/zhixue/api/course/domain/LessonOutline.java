package com.zhixue.api.course.domain;

import lombok.Data;

/** 推荐卡片里一个课时的最小信息 */
@Data
public class LessonOutline {

    private Long lessonId;
    private Long chapterId;
    private String title;
    /** 秒 */
    private Integer duration;
    /** 章节序号，从 1 开始 */
    private int chapterIndex;
    /** 本章内的课时序号，从 1 开始 */
    private int lessonIndex;
    /** 已购、免费、管理员/本课教师，或处于试看范围 */
    private boolean playable;
}
