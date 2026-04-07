package com.zhixue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 角色实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private String roleName;
    private String roleKey;
    private Integer roleSort;
    private Integer status;
    private String remark;

    @TableField(exist = false)
    private List<Long> menuIds;
}
