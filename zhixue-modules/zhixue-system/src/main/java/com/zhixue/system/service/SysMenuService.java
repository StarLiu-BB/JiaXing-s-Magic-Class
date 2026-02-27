package com.zhixue.system.service;

import com.zhixue.system.domain.entity.SysMenu;
import com.zhixue.system.domain.vo.RouterVO;

import java.util.List;

/**
 * <p>
 * 菜单服务。
 * </p>
 */
public interface SysMenuService {

    List<SysMenu> listMenus();

    SysMenu getMenu(Long id);

    boolean createMenu(SysMenu menu);

    boolean updateMenu(SysMenu menu);

    boolean removeMenu(Long id);

    List<RouterVO> buildRouters(List<SysMenu> menus);
}

