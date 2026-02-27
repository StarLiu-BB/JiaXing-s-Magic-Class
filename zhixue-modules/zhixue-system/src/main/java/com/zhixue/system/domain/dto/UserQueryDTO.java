package com.zhixue.system.domain.dto;

import com.zhixue.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 用户查询条件。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryDTO extends PageQuery {

    private String username;
    private Integer status;
    private Long roleId;
}

