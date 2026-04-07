package com.zhixue.marketing.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 秒杀活动状态变更请求。
 */
@Data
public class SeckillStatusDTO {

    @NotNull(message = "状态不能为空")
    private Integer status;
}
