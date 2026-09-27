package com.zhixue.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhixue.common.core.domain.PageQuery;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.system.domain.entity.SysDictData;
import com.zhixue.system.domain.entity.SysDictType;
import com.zhixue.system.mapper.SysDictDataMapper;
import com.zhixue.system.mapper.SysDictTypeMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 字典服务测试。
 *
 * <p>覆盖 D31：原 SysDictController 的 saveType/saveData 只打印日志后
 * 返回 {@code R.ok()}，对调用方声称保存成功却从不落库；
 * 前端字典页则硬编码两条假数据。数据库表 sys_dict_type / sys_dict_data
 * 早已存在，只是从未被使用。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SysDictServiceImplTest {

    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysDictType.class);
        TableInfoHelper.initTableInfo(assistant, SysDictData.class);
    }

    @Mock
    private SysDictTypeMapper dictTypeMapper;

    @Mock
    private SysDictDataMapper dictDataMapper;

    @InjectMocks
    private SysDictServiceImpl dictService;

    private SysDictType type(String dictType) {
        SysDictType t = new SysDictType();
        t.setId(1L);
        t.setDictName("用户状态");
        t.setDictType(dictType);
        t.setStatus(0);
        return t;
    }

    @Test
    void saveTypeMustActuallyPersist() {
        when(dictTypeMapper.selectCount(any())).thenReturn(0L);

        dictService.saveType(type("sys_user_status"));

        // 必须真正写库，不能只打日志就返回成功
        verify(dictTypeMapper, times(1)).insert(any(SysDictType.class));
    }

    @Test
    void saveTypeMustRejectDuplicateDictType() {
        when(dictTypeMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> dictService.saveType(type("sys_user_status")))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    void saveTypeMustRejectBlankFields() {
        SysDictType blank = new SysDictType();
        assertThatThrownBy(() -> dictService.saveType(blank))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    void updateTypeMustUseIdAndPersist() {
        SysDictType existing = type("sys_user_status");
        when(dictTypeMapper.selectById(1L)).thenReturn(existing);
        when(dictTypeMapper.selectCount(any())).thenReturn(0L);

        dictService.saveType(existing);

        verify(dictTypeMapper, times(1)).updateById(any(SysDictType.class));
    }

    @Test
    void saveDataMustRequireExistingDictType() {
        when(dictTypeMapper.selectCount(any())).thenReturn(0L);

        SysDictData data = new SysDictData();
        data.setDictType("not_exist");
        data.setDictLabel("启用");
        data.setDictValue("0");

        assertThatThrownBy(() -> dictService.saveData(data))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("字典类型");
    }

    @Test
    void saveDataMustActuallyPersist() {
        when(dictTypeMapper.selectCount(any())).thenReturn(1L);

        SysDictData data = new SysDictData();
        data.setDictType("sys_user_status");
        data.setDictLabel("启用");
        data.setDictValue("0");

        dictService.saveData(data);

        verify(dictDataMapper, times(1)).insert(any(SysDictData.class));
    }

    @Test
    void deleteTypeMustAlsoRemoveItsData() {
        when(dictTypeMapper.selectById(1L)).thenReturn(type("sys_user_status"));

        dictService.deleteType(1L);

        // 否则会残留孤儿字典项
        verify(dictDataMapper, times(1)).delete(any());
        verify(dictTypeMapper, times(1)).deleteById(1L);
    }

    @Test
    void deleteTypeMustRejectMissingId() {
        when(dictTypeMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> dictService.deleteType(99L))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    void listDataByTypeShouldReturnEmptyForBlankType() {
        assertThat(dictService.listDataByType("  ")).isEmpty();
    }

    @Test
    void listDataByTypeShouldQueryByType() {
        SysDictData data = new SysDictData();
        data.setDictType("sys_user_status");
        when(dictDataMapper.selectList(any())).thenReturn(List.of(data));

        assertThat(dictService.listDataByType("sys_user_status")).hasSize(1);
    }

    @Test
    void pageTypesShouldNotFailWithoutFilters() {
        when(dictTypeMapper.selectPage(any(), any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        PageQuery query = new PageQuery();
        assertThat(dictService.pageTypes(query, null, null)).isNotNull();
    }
}
