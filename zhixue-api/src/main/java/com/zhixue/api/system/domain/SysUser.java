package com.zhixue.api.system.domain;

import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 系统用户实体，供 Feign 远程接口复用。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysUser extends BaseEntity {

    private String username;
    private String nickname;
    private String mobile;
    private String email;
    private String avatar;
    private Integer status;
}

