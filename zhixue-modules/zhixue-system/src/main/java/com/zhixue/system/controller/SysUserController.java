package com.zhixue.system.controller;

import com.zhixue.api.system.domain.LoginUser;
import com.zhixue.common.core.domain.PageResult;
import com.zhixue.common.core.domain.R;
import com.zhixue.common.core.exception.ServiceException;
import com.zhixue.common.security.annotation.RequirePermission;
import com.zhixue.common.security.utils.SecurityUtils;
import com.zhixue.system.domain.dto.UserDTO;
import com.zhixue.system.domain.dto.UserQueryDTO;
import com.zhixue.system.domain.vo.UserVO;
import com.zhixue.system.service.SysUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @GetMapping("/list")
    @RequirePermission("system:user:list")
    public R<PageResult<UserVO>> list(UserQueryDTO query) {
        return R.ok(userService.listUsers(query));
    }

    @GetMapping("/info/{id}")
    @RequirePermission("system:user:list")
    public R<UserVO> info(@PathVariable Long id) {
        return R.ok(userService.getUser(id));
    }

    @GetMapping("/{id}")
    @RequirePermission("system:user:list")
    public R<UserVO> get(@PathVariable Long id) {
        return R.ok(userService.getUser(id));
    }

    @PostMapping
    @RequirePermission("system:user:add")
    public R<Void> create(@Valid @RequestBody UserDTO dto) {
        return userService.createUser(dto) ? R.ok() : R.fail("新增失败");
    }

    @PutMapping
    @RequirePermission("system:user:edit")
    public R<Void> update(@Valid @RequestBody UserDTO dto) {
        return userService.updateUser(dto) ? R.ok() : R.fail("更新失败");
    }

    @DeleteMapping("/{id}")
    @RequirePermission("system:user:remove")
    public R<Void> delete(@PathVariable Long id) {
        return userService.removeUser(id) ? R.ok() : R.fail("删除失败");
    }

    @PostMapping("/assignRole/{userId}")
    @RequirePermission("system:user:edit")
    public R<Void> assignRole(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
        return userService.assignRoles(userId, roleIds) ? R.ok() : R.fail("分配角色失败");
    }

    @PutMapping("/resetPwd")
    @RequirePermission("system:user:resetPwd")
    public R<Void> resetPassword(@RequestBody Map<String, Object> request) {
        Long userId = request.get("userId") == null ? null : Long.valueOf(String.valueOf(request.get("userId")));
        String password = request.get("password") == null ? null : String.valueOf(request.get("password"));
        return userService.resetPassword(userId, password) ? R.ok() : R.fail("重置密码失败");
    }

    @PutMapping("/changeStatus")
    @RequirePermission("system:user:edit")
    public R<Void> changeStatus(@RequestBody Map<String, Object> request) {
        Long userId = request.get("userId") == null ? null : Long.valueOf(String.valueOf(request.get("userId")));
        Integer status = request.get("status") == null ? null : Integer.valueOf(String.valueOf(request.get("status")));
        return userService.changeStatus(userId, status) ? R.ok() : R.fail("修改状态失败");
    }

    @GetMapping("/profile/{username}")
    public R<LoginUser> profile(@PathVariable String username) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(403, "禁止外部访问内部用户接口");
        }
        LoginUser loginUser = userService.getLoginUserByUsername(username);
        return loginUser == null ? R.fail("用户不存在") : R.ok(loginUser);
    }

    @GetMapping("/profile/phone/{phone}")
    public R<LoginUser> profileByPhone(@PathVariable String phone) {
        if (!SecurityUtils.isTrustedInternalCall()) {
            throw new ServiceException(403, "禁止外部访问内部用户接口");
        }
        LoginUser loginUser = userService.getLoginUserByPhone(phone);
        return loginUser == null ? R.fail("用户不存在") : R.ok(loginUser);
    }
}
