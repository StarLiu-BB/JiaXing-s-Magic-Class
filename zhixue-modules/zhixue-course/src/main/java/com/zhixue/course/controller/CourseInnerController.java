package com.zhixue.course.controller;

import com.zhixue.api.course.domain.CourseOutline;
import com.zhixue.api.course.domain.CourseOutlineQuery;
import com.zhixue.common.core.constant.HttpStatus;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.utils.SecurityUtils;
import com.zhixue.course.service.CourseOutlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 服务间接口：按请求体里的学生身份返回课程大纲。
 *
 * <p>身份由调用方给出，因此只能由可信内部调用访问；网关从不设置 X-Inner-Call，外部请求一律 403。</p>
 */
@RestController
@RequiredArgsConstructor
public class CourseInnerController {

    private final CourseOutlineService courseOutlineService;

    @PostMapping("/inner/outlines")
    public R<List<CourseOutline>> outlines(@RequestBody CourseOutlineQuery query) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(HttpStatus.FORBIDDEN, "禁止外部访问内部课程接口");
        }
        return R.ok(courseOutlineService.outlines(query));
    }
}
