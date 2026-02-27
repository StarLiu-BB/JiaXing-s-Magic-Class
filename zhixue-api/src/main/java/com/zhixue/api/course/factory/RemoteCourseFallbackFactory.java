package com.zhixue.api.course.factory;

import com.zhixue.api.course.RemoteCourseService;
import com.zhixue.api.course.domain.CourseInfo;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * <p>
 * 课程服务降级处理。
 * </p>
 */
@Slf4j
@Component
public class RemoteCourseFallbackFactory implements FallbackFactory<RemoteCourseService> {

    @Override
    public RemoteCourseService create(Throwable cause) {
        log.error("调用课程服务失败: {}", cause.getMessage());
        return new RemoteCourseService() {
            @Override
            public R<CourseInfo> getCourse(Long courseId) {
                return R.fail(HttpStatus.SERVICE_UNAVAILABLE, "课程服务不可用");
            }

            @Override
            public R<PageResult<CourseInfo>> listCourses(PageQuery pageQuery) {
                return R.fail(HttpStatus.SERVICE_UNAVAILABLE, "课程服务不可用");
            }
        };
    }
}

