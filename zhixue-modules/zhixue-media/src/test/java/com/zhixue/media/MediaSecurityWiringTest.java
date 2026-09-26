package com.zhixue.media;

import com.zhixue.common.security.annotation.RequireLogin;
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
 * <p>覆盖 C6：media 是唯一没有任何鉴权配置的业务服务。
 * 根因是启动类未扫描 com.zhixue.common，导致 SecurityAccessAspect 不生效，
 * 权限注解形同装饰；同时上传/查询接口本身也没有任何注解。</p>
 */
class MediaSecurityWiringTest {

    @Test
    void applicationMustScanCommonSoSecurityAspectTakesEffect() {
        SpringBootApplication annotation =
                MediaApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.scanBasePackages())
                .as("未扫描 com.zhixue.common 时 SecurityAccessAspect 不加载，@RequireLogin 不会生效")
                .contains("com.zhixue.media", "com.zhixue.common");
    }

    @Test
    void uploadEndpointsMustRequireLogin() {
        List<String> mustRequireLogin = Arrays.asList("uploadChunk", "merge");

        for (String methodName : mustRequireLogin) {
            Method method = findMethod(methodName);
            assertThat(method.getAnnotation(RequireLogin.class))
                    .as("上传接口 %s 必须要求登录，否则任何人都能往对象存储写文件", methodName)
                    .isNotNull();
        }
    }

    @Test
    void fileQueryEndpointsMustRequireLogin() {
        List<String> mustRequireLogin = Arrays.asList("getFile", "listByIds", "page");

        for (String methodName : mustRequireLogin) {
            Method method = findMethod(methodName);
            assertThat(method.getAnnotation(RequireLogin.class))
                    .as("查询接口 %s 必须要求登录，否则可匿名枚举他人媒资", methodName)
                    .isNotNull();
        }
    }

    private Method findMethod(String name) {
        return Arrays.stream(UploadController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到方法: " + name));
    }
}
