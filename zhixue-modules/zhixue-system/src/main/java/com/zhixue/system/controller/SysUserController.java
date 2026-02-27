package com.zhixue.system.controller;

import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.system.domain.dto.UserDTO;
import com.zhixue.system.domain.dto.UserQueryDTO;
import com.zhixue.system.domain.vo.UserVO;
import com.zhixue.system.service.SysUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 用户管理接口。
 * </p>
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @GetMapping("/list")
    public R<PageResult<UserVO>> list(UserQueryDTO query) {
        return R.ok(userService.listUsers(query));
    }

    @GetMapping("/info/{id}")
    public R<UserVO> info(@PathVariable Long id) {
        return R.ok(userService.getUser(id));
    }

    @PostMapping
    public R<Void> create(@Valid @RequestBody UserDTO dto) {
        return userService.createUser(dto) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    public R<Void> update(@Valid @RequestBody UserDTO dto) {
        return userService.updateUser(dto) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        return userService.removeUser(id) ? R.ok() : R.fail("删除失败");
    }

    @PostMapping("/assignRole/{userId}")
    public R<Void> assignRole(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        return userService.assignRoles(userId, roleIds) ? R.ok() : R.fail("分配角色失败");
    }
}

