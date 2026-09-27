package com.zhixue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型，如"用户状态"对应 sys_user_status。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class SysDictType extends BaseEntity {

    /** 字典显示名称。 */
    private String dictName;

    /** 字典类型编码，全局唯一。 */
    private String dictType;

    /** 状态：0 正常，1 停用。 */
    private Integer status;

    private String remark;
}
