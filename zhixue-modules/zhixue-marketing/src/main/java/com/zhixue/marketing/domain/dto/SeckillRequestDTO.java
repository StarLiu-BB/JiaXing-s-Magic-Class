package com.zhixue.marketing.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 秒杀请求参数DTO。
 *
 * <p>参与人取自登录态，不接收客户端传入的 userId —— 否则任何人都能替他人秒杀。</p>
 */
@Data
public class SeckillRequestDTO {

    /**
     * 秒杀活动ID
     */
    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    /**
     * 秒杀令牌（必填，通过 /seckill/token 为当前登录用户签发，一次性）
     */
    private String token;
}
