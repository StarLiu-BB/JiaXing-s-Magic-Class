package com.zhixue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色菜单关联。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_menu")
public class SysRoleMenu extends BaseEntity {

    private Long roleId;
    private Long menuId;
}

