package com.zhixue.system.service;

import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.system.domain.entity.SysDictData;
import com.zhixue.system.domain.entity.SysDictType;

import java.util.List;

/**
 * 字典管理服务。
 */
public interface SysDictService {

    /** 新增或更新字典类型（有 id 即更新）。 */
    SysDictType saveType(SysDictType dictType);

    /** 删除字典类型，并级联清理其下数据项。 */
    void deleteType(Long id);

    PageResult<SysDictType> pageTypes(PageQuery query, String dictName, Integer status);

    List<SysDictType> listTypes();

    /** 新增或更新字典数据项（有 id 即更新）。 */
    SysDictData saveData(SysDictData dictData);

    void deleteData(Long id);

    /** 按类型编码查询启用的数据项，供前端下拉使用。 */
    List<SysDictData> listDataByType(String dictType);
}
