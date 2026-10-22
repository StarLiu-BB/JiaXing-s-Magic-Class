package com.zhixue.api.order;

import com.zhixue.api.order.domain.OrderInfo;
import com.zhixue.api.order.domain.SeckillOrderRequest;
import com.zhixue.common.core.constant.ServiceNameConstants;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.config.InternalAccessConstants;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 订单服务远程调用接口。
 *
 * <p>订单服务未做 StripPrefix，控制器本身映射在 /order 下，因此这里保留 /order 前缀。
 * 契约由 zhixue-order 的 PurchaseQueryContractTest 守护。</p>
 *
 * <p>不配置 fallback：调用方必须自行把失败视为"未购买"（fail-closed），
 * 不能依赖熔断返回一个默认值。</p>
 */
@FeignClient(contextId = "remoteOrderService", value = ServiceNameConstants.ORDER_SERVICE)
public interface RemoteOrderService {

    /**
     * 用户是否已购买该商品（订单状态为已支付）。仅限服务间调用。
     */
    @GetMapping("/order/inner/purchased")
    R<Boolean> hasPurchased(@RequestParam("userId") Long userId,
                            @RequestParam("productId") Long productId,
                            @RequestHeader(InternalAccessConstants.INNER_CALL_HEADER) String innerHeader,
                            @RequestHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER) String internalToken);

    /** 按活动及用户确认原单；仅限服务间调用，不存在时data为null。 */
    @GetMapping("/order/inner/seckill")
    R<OrderInfo> findSeckillOrder(@RequestParam("activityId") Long activityId,
                                 @RequestParam("userId") Long userId,
                                 @RequestHeader(InternalAccessConstants.INNER_CALL_HEADER) String innerHeader,
                                 @RequestHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER) String internalToken);

    /** 秒杀成交后按活动与用户幂等建单；仅限服务间调用，契约由 SeckillOrderContractTest 守护。 */
    @PostMapping("/order/inner/seckill")
    R<OrderInfo> createSeckillOrder(@RequestBody SeckillOrderRequest request,
                                    @RequestHeader(InternalAccessConstants.INNER_CALL_HEADER) String innerHeader,
                                    @RequestHeader(InternalAccessConstants.INTERNAL_TOKEN_HEADER) String internalToken);
}
