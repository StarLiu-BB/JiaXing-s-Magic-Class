package com.zhixue.system.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 用户展示对象。
 * </p>
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private Integer status;
    private LocalDateTime createTime;
    private List<Long> roleIds;
}

