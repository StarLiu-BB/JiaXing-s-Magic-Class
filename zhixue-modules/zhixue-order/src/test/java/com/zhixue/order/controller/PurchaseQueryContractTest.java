package com.zhixue.order.controller;

import com.zhixue.api.order.RemoteOrderService;
import com.zhixue.common.core.exception.GlobalExceptionHandler;
import com.zhixue.common.security.config.InternalAccessConstants;
import com.zhixue.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * "是否已购买"内部接口：课程服务据此决定是否下发付费课时的视频地址。
 *
 * <p>订单表是购买关系的唯一事实来源：支付成功即可观看，退款后自动失效，
 * 无需在两个服务间同步"开通/收回"状态。</p>
 */
@ExtendWith(MockitoExtension.class)
class PurchaseQueryContractTest {

    @Mock
    private OrderService orderService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private Method feignMethod() throws Exception {
        return RemoteOrderService.class.getMethod("hasPurchased", Long.class, Long.class, String.class, String.class);
    }

    /** 按 Feign 注解声明的路径与参数名构造请求，接口与控制器一旦错配即失败。 */
    private String feignUrl(long userId, long productId) throws Exception {
        Method method = feignMethod();
        String path = method.getAnnotation(GetMapping.class).value()[0];
        List<String> params = new ArrayList<>();
        for (Parameter p : method.getParameters()) {
            RequestParam rp = p.getAnnotation(RequestParam.class);
            if (rp != null) {
                params.add(rp.value() + "=" + ("userId".equals(rp.value()) ? userId : productId));
            }
        }
        return path + "?" + String.join("&", params);
    }

    @Test
    void trustedInternalCallShouldGetPurchaseFact() throws Exception {
        when(orderService.hasPaid(1003L, 2001L)).thenReturn(true);

        mockMvc.perform(get(feignUrl(1003L, 2001L))
                        .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                        .header(InternalAccessConstants.INNER_CALL_HEADER, "true"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void userRequestThroughGatewayMustNotQueryOthersPurchases() throws Exception {
        // 经网关的已登录请求同样带有效内部令牌，但网关从不设置 X-Inner-Call，
        // 否则任何用户都能探测他人买过什么
        mockMvc.perform(get(feignUrl(1001L, 2001L))
                        .requestAttr(InternalAccessConstants.TRUSTED_REQUEST_ATTR, Boolean.TRUE)
                        .header(InternalAccessConstants.USER_ID_HEADER, "1003"))
                .andExpect(jsonPath("$.code").value(403));

        verify(orderService, never()).hasPaid(anyLong(), anyLong());
    }

    @Test
    void untrustedCallMustBeRejected() throws Exception {
        mockMvc.perform(get(feignUrl(1003L, 2001L)).header(InternalAccessConstants.INNER_CALL_HEADER, "true"))
                .andExpect(jsonPath("$.code").value(403));

        verify(orderService, never()).hasPaid(anyLong(), anyLong());
    }

    @Test
    void feignClientMustSendInternalCallHeaders() throws Exception {
        List<String> headers = new ArrayList<>();
        for (Parameter p : feignMethod().getParameters()) {
            org.springframework.web.bind.annotation.RequestHeader h =
                    p.getAnnotation(org.springframework.web.bind.annotation.RequestHeader.class);
            if (h != null) {
                headers.add(h.value());
            }
        }
        assertThat(headers).containsExactlyInAnyOrder(
                InternalAccessConstants.INNER_CALL_HEADER, InternalAccessConstants.INTERNAL_TOKEN_HEADER);
    }
}
