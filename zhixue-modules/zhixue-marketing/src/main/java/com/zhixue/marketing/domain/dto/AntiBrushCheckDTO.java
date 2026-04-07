package com.zhixue.marketing.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 防刷检查请求。
 */
@Data
public class AntiBrushCheckDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "IP不能为空")
    private String ip;

    @NotBlank(message = "设备ID不能为空")
    private String deviceId;
}
