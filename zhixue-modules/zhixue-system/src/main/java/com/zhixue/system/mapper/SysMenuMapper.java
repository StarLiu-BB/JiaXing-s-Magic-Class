package com.zhixue.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.system.domain.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;

/**
 * 菜单表 Mapper。
 */
@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {
}

