package com.zhixue.system.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.system.domain.entity.SysMenu;
import com.zhixue.system.domain.vo.RouterVO;
import com.zhixue.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 菜单管理接口。
 * </p>
 */
@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @GetMapping("/list")
    public R<List<SysMenu>> list() {
        return R.ok(menuService.listMenus());
    }

    @GetMapping("/routers")
    public R<List<RouterVO>> routers() {
        List<SysMenu> menus = menuService.listMenus();
        return R.ok(menuService.buildRouters(menus));
    }

    @GetMapping("/info/{id}")
    public R<SysMenu> info(@PathVariable Long id) {
        return R.ok(menuService.getMenu(id));
    }

    @PostMapping
    public R<Void> create(@RequestBody SysMenu menu) {
        return menuService.createMenu(menu) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    public R<Void> update(@RequestBody SysMenu menu) {
        return menuService.updateMenu(menu) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return menuService.removeMenu(id) ? R.ok() : R.fail("删除失败");
    }
}

