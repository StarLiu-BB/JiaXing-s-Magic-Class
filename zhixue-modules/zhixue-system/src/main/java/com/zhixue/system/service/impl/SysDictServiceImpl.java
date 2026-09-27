package com.zhixue.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.system.domain.entity.SysDictData;
import com.zhixue.system.domain.entity.SysDictType;
import com.zhixue.system.mapper.SysDictDataMapper;
import com.zhixue.system.mapper.SysDictTypeMapper;
import com.zhixue.system.service.SysDictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 字典管理实现。
 *
 * <p>此前 SysDictController 的保存接口只打日志后返回成功，
 * 从不落库；数据库表却早已存在。本实现把它接到真实表上。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysDictServiceImpl implements SysDictService {

    private final SysDictTypeMapper dictTypeMapper;
    private final SysDictDataMapper dictDataMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysDictType saveType(SysDictType dictType) {
        if (dictType == null || !StringUtils.hasText(dictType.getDictName())
                || !StringUtils.hasText(dictType.getDictType())) {
            throw new ServiceException("字典名称与字典类型不能为空");
        }
        // 类型编码唯一：排除自身后仍存在同名即冲突
        LambdaQueryWrapper<SysDictType> dup = new LambdaQueryWrapper<>();
        dup.eq(SysDictType::getDictType, dictType.getDictType())
                .ne(dictType.getId() != null, SysDictType::getId, dictType.getId());
        if (dictTypeMapper.selectCount(dup) > 0) {
            throw new ServiceException("字典类型已存在: " + dictType.getDictType());
        }

        if (dictType.getId() != null && dictTypeMapper.selectById(dictType.getId()) != null) {
            dictTypeMapper.updateById(dictType);
        } else {
            dictTypeMapper.insert(dictType);
        }
        return dictType;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteType(Long id) {
        SysDictType existing = id == null ? null : dictTypeMapper.selectById(id);
        if (existing == null) {
            throw new ServiceException("字典类型不存在");
        }
        // 级联清理数据项，避免残留孤儿记录
        LambdaQueryWrapper<SysDictData> dataWrapper = new LambdaQueryWrapper<>();
        dataWrapper.eq(SysDictData::getDictType, existing.getDictType());
        dictDataMapper.delete(dataWrapper);
        dictTypeMapper.deleteById(id);
    }

    @Override
    public PageResult<SysDictType> pageTypes(PageQuery query, String dictName, Integer status) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(dictName), SysDictType::getDictName, dictName)
                .eq(status != null, SysDictType::getStatus, status)
                .orderByDesc(SysDictType::getCreateTime);
        Page<SysDictType> page = dictTypeMapper.selectPage(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), page.getSize());
    }

    @Override
    public List<SysDictType> listTypes() {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictType::getStatus, 0).orderByAsc(SysDictType::getDictName);
        return dictTypeMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysDictData saveData(SysDictData dictData) {
        if (dictData == null || !StringUtils.hasText(dictData.getDictType())
                || !StringUtils.hasText(dictData.getDictLabel())
                || !StringUtils.hasText(dictData.getDictValue())) {
            throw new ServiceException("字典类型、标签与取值不能为空");
        }
        // 必须挂在已存在的类型下，否则会产生无法展示的游离数据
        LambdaQueryWrapper<SysDictType> typeWrapper = new LambdaQueryWrapper<>();
        typeWrapper.eq(SysDictType::getDictType, dictData.getDictType());
        if (dictTypeMapper.selectCount(typeWrapper) == 0) {
            throw new ServiceException("字典类型不存在: " + dictData.getDictType());
        }
        if (dictData.getDictSort() == null) {
            dictData.setDictSort(1);
        }

        if (dictData.getId() != null && dictDataMapper.selectById(dictData.getId()) != null) {
            dictDataMapper.updateById(dictData);
        } else {
            dictDataMapper.insert(dictData);
        }
        return dictData;
    }

    @Override
    public void deleteData(Long id) {
        if (id == null || dictDataMapper.selectById(id) == null) {
            throw new ServiceException("字典数据不存在");
        }
        dictDataMapper.deleteById(id);
    }

    @Override
    public List<SysDictData> listDataByType(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            return List.of();
        }
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictData::getDictType, dictType)
                .eq(SysDictData::getStatus, 0)
                .orderByAsc(SysDictData::getDictSort);
        return dictDataMapper.selectList(wrapper);
    }
}
