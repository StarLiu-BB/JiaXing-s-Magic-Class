package com.zhixue.system.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.common.security.annotation.RequirePermission;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.service.SysRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @GetMapping("/list")
    @RequirePermission("system:role:list")
    public R<List<SysRole>> list() {
        return R.ok(roleService.listRoles());
    }

    @GetMapping("/info/{id}")
    @RequirePermission("system:role:list")
    public R<SysRole> info(@PathVariable Long id) {
        return R.ok(roleService.getRole(id));
    }

    @GetMapping("/{id}")
    @RequirePermission("system:role:list")
    public R<SysRole> get(@PathVariable Long id) {
        return R.ok(roleService.getRole(id));
    }

    @PostMapping
    @RequirePermission("system:role:edit")
    public R<Void> create(@Valid @RequestBody SysRole role) {
        return roleService.createRole(role) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    @RequirePermission("system:role:edit")
    public R<Void> update(@Valid @RequestBody SysRole role) {
        return roleService.updateRole(role) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("system:role:remove")
    public R<Void> delete(@PathVariable Long id) {
        return roleService.removeRole(id) ? R.ok() : R.fail("删除失败");
    }

    @PostMapping("/assignMenu/{roleId}")
    @RequirePermission("system:role:edit")
    public R<Void> assignMenu(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        return roleService.assignMenus(roleId, menuIds) ? R.ok() : R.fail("分配权限失败");
    }

    @PutMapping("/changeStatus")
    @RequirePermission("system:role:edit")
    public R<Void> changeStatus(@RequestBody Map<String, Object> request) {
        Long roleId = request.get("roleId") == null ? null : Long.valueOf(String.valueOf(request.get("roleId")));
        Integer status = request.get("status") == null ? null : Integer.valueOf(String.valueOf(request.get("status")));
        return roleService.changeStatus(roleId, status) ? R.ok() : R.fail("修改状态失败");
    }

    @GetMapping("/menuIds/{roleId}")
    @RequirePermission("system:role:list")
    public R<List<Long>> menuIds(@PathVariable Long roleId) {
        return R.ok(roleService.listMenuIds(roleId));
    }
}
