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
 *
 * <p>注意：Feign 按服务名直连课程服务，不经网关。/course 是网关前缀（StripPrefix=1），
 * 课程控制器自身映射在根路径，因此这里不能带 /course 前缀。
 * 契约由 zhixue-course 的 RemoteCourseContractTest 守护。</p>
 */
@FeignClient(contextId = "remoteCourseService",
        value = ServiceNameConstants.COURSE_SERVICE,
        fallbackFactory = RemoteCourseFallbackFactory.class)
public interface RemoteCourseService {

    @GetMapping("/{courseId}")
    R<CourseInfo> getCourse(@PathVariable("courseId") Long courseId);

    @GetMapping("/list")
    R<PageResult<CourseInfo>> listCourses(@SpringQueryMap PageQuery pageQuery);
}
