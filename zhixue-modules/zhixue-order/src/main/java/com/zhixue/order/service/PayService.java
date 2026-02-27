package com.zhixue.order.service;

import com.zhixue.order.domain.dto.PayRequestDTO;
import com.zhixue.order.domain.dto.PayResponse;

/**
 * 支付服务接口。
 * 定义了支付相关的业务操作，主要是发起支付请求。
 */
public interface PayService {

    /**
     * 发起支付请求。
     * 根据订单信息和支付渠道，调用对应的支付平台（支付宝或微信）生成支付链接或二维码。
     * @param dto 支付请求参数
     * @return 支付响应结果，包含支付链接或二维码
     */
    PayResponse pay(PayRequestDTO dto);
}


