package com.zhixue.system.controller;

import com.zhixue.common.core.domain.R;
import com.zhixue.system.domain.entity.SysRole;
import com.zhixue.system.service.SysRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 角色管理接口。
 * </p>
 */
@RestController
@RequestMapping("/role")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @GetMapping("/list")
    public R<List<SysRole>> list() {
        return R.ok(roleService.listRoles());
    }

    @GetMapping("/info/{id}")
    public R<SysRole> info(@PathVariable Long id) {
        return R.ok(roleService.getRole(id));
    }

    @PostMapping
    public R<Void> create(@Valid @RequestBody SysRole role) {
        return roleService.createRole(role) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    public R<Void> update(@Valid @RequestBody SysRole role) {
        return roleService.updateRole(role) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return roleService.removeRole(id) ? R.ok() : R.fail("删除失败");
    }

    @PostMapping("/assignMenu/{roleId}")
    public R<Void> assignMenu(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
        return roleService.assignMenus(roleId, menuIds) ? R.ok() : R.fail("分配权限失败");
    }
}

