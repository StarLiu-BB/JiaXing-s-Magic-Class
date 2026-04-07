package com.zhixue.marketing.domain.dto;

import lombok.Data;

import java.util.List;

/**
 * 优惠券发放请求。
 */
@Data
public class CouponIssueDTO {

    /**
     * 指定用户列表；为空时表示不批量发放。
     */
    private List<Long> userIds;
}
