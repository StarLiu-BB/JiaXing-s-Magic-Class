package com.zhixue.api.course;

import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.common.core.constant.ServiceNameConstants;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.InternalAccessConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

/**
 * 课程大纲（服务间）。目前只供 AI 服务的推荐卡片使用。
 *
 * <p>Feign 按服务名直连、不经网关：/course 只是网关前缀（StripPrefix=1），这里不能带。
 * 不配置 fallback：失败时调用方必须当作「本次没有推荐」，不能拿默认值当真实数据。
 * 路径契约由 zhixue-course 的 CourseInnerControllerTest 守护。</p>
 */
@FeignClient(contextId = "remoteCourseOutlineService", value = ServiceNameConstants.COURSE_SERVICE)
public interface RemoteCourseOutlineService {

    @PostMapping("/inner/outlines")
    R<List<CourseOutline>> outlines(@RequestBody CourseOutlineQuery query,
                                    @RequestHeader(InternalAccessConstants.INNER_CALL_HEADER) String innerHeader,
                                    @RequestHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER) String internalToken);
}
