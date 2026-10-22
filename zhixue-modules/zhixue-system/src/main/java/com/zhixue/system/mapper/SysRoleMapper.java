package com.zhixue.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.system.domain.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;

/**
 * 角色表 Mapper。
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM sys_role WHERE UPPER(role_key) = 'ADMIN' AND status = 0 AND deleted = 0 ORDER BY id FOR UPDATE")
    java.util.List<Long> lockEnabledAdminRoles();

    @org.apache.ibatis.annotations.Select("SELECT COUNT(DISTINCT u.id) FROM sys_user u JOIN sys_user_role ur ON u.id = ur.user_id AND ur.deleted = 0 JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 0 AND UPPER(r.role_key) = 'ADMIN' WHERE u.deleted = 0 AND u.status = 0 AND u.id = #{userId}")
    long isEnabledAdmin(@org.apache.ibatis.annotations.Param("userId") Long userId);

    @org.apache.ibatis.annotations.Select("SELECT COUNT(DISTINCT u.id) FROM sys_user u JOIN sys_user_role ur ON u.id = ur.user_id AND ur.deleted = 0 JOIN sys_role r ON r.id = ur.role_id AND r.deleted = 0 AND r.status = 0 AND UPPER(r.role_key) = 'ADMIN' WHERE u.deleted = 0 AND u.status = 0 AND u.id != #{userId}")
    long countOtherEnabledAdmins(@org.apache.ibatis.annotations.Param("userId") Long userId);
}

