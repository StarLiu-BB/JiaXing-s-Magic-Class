package com.zhixue.common.security.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 登录用户信息类
 * 作用：这个类用来存储当前登录用户的信息。
 * 包括用户ID、用户名、拥有的角色列表，以及一些额外的信息。
 * 用户登录成功后，我们会把他的信息存到这个对象里，方便后续使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // 用户的ID
    private Long userId;
    // 用户的用户名
    private String username;
    // 用户拥有的角色列表，比如管理员、老师、学生等
    private List<String> roles;
    // 额外的信息，用键值对的方式存储
    private Map<String, Object> extra;
}

