package com.zhixue.interaction.domain.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 人工复核弹幕。
 */
@Data
public class DanmakuAuditDTO {

    /** 1 通过（重新展示） 2 拒绝（下架）；不允许改回 0 待审核 */
    @NotNull(message = "审核结果不能为空")
    @Min(value = 1, message = "审核结果只能是通过或拒绝")
    @Max(value = 2, message = "审核结果只能是通过或拒绝")
    private Integer auditStatus;

    /** 拒绝时必填 */
    @Size(max = 200, message = "原因不能超过 200 字")
    private String auditRemark;
}
