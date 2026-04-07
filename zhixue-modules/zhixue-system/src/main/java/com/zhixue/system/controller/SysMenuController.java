package com.zhixue.system.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.annotation.RequirePermission;
import com.zhixue.common.security.utils.SecurityUtils;
import com.zhixue.system.domain.entity.SysMenu;
import com.zhixue.system.domain.vo.RouterVO;
import com.zhixue.system.service.SysMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/menu")
@RequiredArgsConstructor
public class SysMenuController {

    private final SysMenuService menuService;

    @GetMapping("/list")
    @RequirePermission("system:menu:list")
    public R<List<SysMenu>> list() {
        return R.ok(menuService.listMenus());
    }

    @GetMapping("/routers")
    @RequirePermission("system:menu:list")
    public R<List<RouterVO>> routers() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new ServiceException(401, "未登录或令牌已失效");
        }
        return R.ok(menuService.buildRouters(menuService.listMenusByUserId(userId)));
    }

    @GetMapping("/info/{id}")
    @RequirePermission("system:menu:list")
    public R<SysMenu> info(@PathVariable Long id) {
        return R.ok(menuService.getMenu(id));
    }

    @GetMapping("/{id}")
    @RequirePermission("system:menu:list")
    public R<SysMenu> get(@PathVariable Long id) {
        return R.ok(menuService.getMenu(id));
    }

    @PostMapping
    @RequirePermission("system:menu:edit")
    public R<Void> create(@RequestBody SysMenu menu) {
        return menuService.createMenu(menu) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    @RequirePermission("system:menu:edit")
    public R<Void> update(@RequestBody SysMenu menu) {
        return menuService.updateMenu(menu) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("system:menu:remove")
    public R<Void> delete(@PathVariable Long id) {
        return menuService.removeMenu(id) ? R.ok() : R.fail("删除失败");
    }

    @GetMapping("/role/{roleId}/menuIds")
    @RequirePermission("system:menu:list")
    public R<List<Long>> roleMenuIds(@PathVariable Long roleId) {
        return R.ok(menuService.listMenuIdsByRoleId(roleId));
    }
}
