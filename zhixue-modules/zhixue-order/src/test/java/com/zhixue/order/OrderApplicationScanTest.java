package com.zhixue.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验 order 服务的组件扫描范围包含 com.zhixue.common。
 *
 * <p>若不包含，common 中的 GlobalExceptionHandler 不会生效，
 * ServiceException（如支付回调验签失败）会以 500 返回，
 * 导致"安全拦截"与"服务故障"无法区分。</p>
 */
class OrderApplicationScanTest {

    @Test
    void shouldScanCommonPackageSoGlobalExceptionHandlerApplies() {
        SpringBootApplication annotation =
                OrderApplication.class.getAnnotation(SpringBootApplication.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.scanBasePackages())
                .as("必须显式扫描 com.zhixue.common，否则全局异常处理器失效")
                .contains("com.zhixue.order", "com.zhixue.common");
    }
}
