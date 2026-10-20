import request from '@/utils/request'

/** 课程详情：canStudy 为当前学生能否观看全部课时（已购 / 免费 / 本课教师） */
export const getCourseDetail = (id) => request({ url: `/course/detail/${id}`, method: 'get' })

/** 章节与课时：服务端已按当前学生判定 locked / preview，并清掉无权课时的 videoUrl */
export const getCourseChapters = (id) => request({ url: `/course/${id}/chapters`, method: 'get' })
