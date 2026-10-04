package com.zhixue.media;

import com.zhixue.common.security.annotation.RequirePermission;
import com.zhixue.media.controller.UploadController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 媒资模块鉴权装配测试。
 *
 * <p>覆盖 C6：media 曾是唯一没有任何鉴权配置的业务服务——启动类未扫描 com.zhixue.common，
 * SecurityAccessAspect 不生效。后来补上了 @RequireLogin，但"只要登录"仍不够：
 * 学生能翻出全部媒资地址、能往对象存储写文件。现要求具体权限，行为由 MediaAccessControlTest 验证。</p>
 */
class MediaSecurityWiringTest {

    @Test
    void applicationMustScanCommonSoSecurityAspectTakesEffect() {
        SpringBootApplication annotation =
                MediaApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.scanBasePackages())
                .as("未扫描 com.zhixue.common 时 SecurityAccessAspect 不加载，权限注解不会生效")
                .contains("com.zhixue.media", "com.zhixue.common");
    }

    @Test
    void uploadEndpointsMustRequireCourseContentPermission() {
        for (String methodName : Arrays.asList("upload", "uploadChunk", "merge")) {
            assertThat(permissionsOf(methodName))
                    .as("上传接口 %s 只对课程内容编辑者开放", methodName)
                    .containsExactlyInAnyOrder("course:edit", "course:publish");
        }
    }

    @Test
    void fileQueryEndpointsMustRequireMediaPermission() {
        for (String methodName : Arrays.asList("getFile", "listByIds", "page")) {
            assertThat(permissionsOf(methodName))
                    .as("查询接口 %s 会暴露 fileUrl（含付费课时视频），必须要求媒资管理权限", methodName)
                    .containsExactly("media:file:list");
        }
    }

    private List<String> permissionsOf(String name) {
        Method method = Arrays.stream(UploadController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到方法: " + name));
        RequirePermission permission = method.getAnnotation(RequirePermission.class);
        assertThat(permission).as("%s 缺少 @RequirePermission", name).isNotNull();
        return Arrays.asList(permission.value());
    }
}
