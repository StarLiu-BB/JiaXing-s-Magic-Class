package com.zhixue.api.course;

import com.zhixue.api.course.domain.CourseInfo;
import com.zhixue.api.course.factory.RemoteCourseFallbackFactory;
import com.zhixue.common.core.constant.ServiceNameConstants;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * <p>
 * 课程服务远程调用接口。
 * </p>
 */
@FeignClient(contextId = "remoteCourseService",
        value = ServiceNameConstants.COURSE_SERVICE,
        fallbackFactory = RemoteCourseFallbackFactory.class)
public interface RemoteCourseService {

    @GetMapping("/course/{courseId}")
    R<CourseInfo> getCourse(@PathVariable("courseId") Long courseId);

    @GetMapping("/course/list")
    R<PageResult<CourseInfo>> listCourses(@SpringQueryMap PageQuery pageQuery);
}
