package com.zhixue.system.service.impl;

import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.system.domain.entity.SysMenu;
import com.zhixue.system.domain.vo.RouterVO;
import com.zhixue.system.mapper.SysMenuMapper;
import com.zhixue.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 菜单服务实现。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class SysMenuServiceImpl implements SysMenuService {

    private final SysMenuMapper menuMapper;

    @Override
    public List<SysMenu> listMenus() {
        return menuMapper.selectList(null);
    }

    @Override
    public SysMenu getMenu(Long id) {
        return menuMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean createMenu(SysMenu menu) {
        return menuMapper.insert(menu) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateMenu(SysMenu menu) {
        if (menu.getId() == null) {
            throw new ServiceException("菜单ID不能为空");
        }
        return menuMapper.updateById(menu) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeMenu(Long id) {
        return menuMapper.deleteById(id) > 0;
    }

    @Override
    public List<RouterVO> buildRouters(List<SysMenu> menus) {
        Map<Long, List<SysMenu>> childrenMap = menus.stream()
                .collect(Collectors.groupingBy(menu -> menu.getParentId() == null ? 0L : menu.getParentId()));
        return buildChildren(childrenMap, 0L);
    }

    private List<RouterVO> buildChildren(Map<Long, List<SysMenu>> map, Long parentId) {
        List<SysMenu> children = map.getOrDefault(parentId, new ArrayList<>());
        List<RouterVO> routers = new ArrayList<>();
        for (SysMenu menu : children) {
            RouterVO vo = new RouterVO();
            vo.setName(menu.getMenuName());
            vo.setPath(menu.getPath());
            vo.setComponent(menu.getComponent());
            vo.setIcon(menu.getIcon());
            vo.setHidden(menu.getVisible() != null && menu.getVisible() == 0);
            vo.setChildren(buildChildren(map, menu.getId()));
            routers.add(vo);
        }
        return routers;
    }
}

