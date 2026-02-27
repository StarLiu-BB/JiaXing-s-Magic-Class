package com.zhixue.system.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * <p>
 * 用户传输对象。
 * </p>
 */
@Data
public class UserDTO {

    private Long id;

    @NotBlank(message = "用户名不能为空")
    private String username;

    private String password;

    private String nickname;

    private String phone;

    private String email;

    private Integer status;

    private List<Long> roleIds;
}

