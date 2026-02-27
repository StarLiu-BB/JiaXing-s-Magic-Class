package com.zhixue.marketing.domain.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 秒杀请求参数DTO
 * 作用：封装用户执行秒杀操作时需要传递的参数，包括活动ID和用户ID。
 * 这个类用于前端和后端之间传递秒杀请求数据，确保数据的完整性和验证。
 */
@Data
public class SeckillRequestDTO {

    /**
     * 秒杀活动ID
     * 说明：要参与的秒杀活动的唯一标识，不能为空
     */
    @NotNull(message = "活动ID不能为空")
    private Long activityId;

    /**
     * 用户ID
     * 说明：参与秒杀的用户的唯一标识，不能为空
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;
}


