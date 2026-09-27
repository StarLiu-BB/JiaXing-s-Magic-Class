package com.zhixue.system.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhixue.common.mybatis.core.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据项，隶属于某个字典类型。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class SysDictData extends BaseEntity {

    /** 所属字典类型编码。 */
    private String dictType;

    /** 展示文本。 */
    private String dictLabel;

    /** 实际取值。 */
    private String dictValue;

    /** 排序，越小越前。 */
    private Integer dictSort;

    /** 状态：0 正常，1 停用。 */
    private Integer status;

    private String remark;
}
