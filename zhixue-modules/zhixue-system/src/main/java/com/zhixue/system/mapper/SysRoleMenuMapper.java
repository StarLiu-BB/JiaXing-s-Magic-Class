package com.zhixue.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixue.system.domain.entity.SysRoleMenu;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 角色-菜单关联。
 *
 * <p>清除关联必须用这里的物理删除：实体继承 BaseEntity 的 @TableLogic，{@code delete(wrapper)}
 * 只会把 deleted 置 1，旧行仍占着 (role_id, menu_id) 唯一键，重新授权即 Duplicate entry。</p>
 */
@Mapper
public interface SysRoleMenuMapper extends BaseMapper<SysRoleMenu> {

    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int physicalDeleteByRoleId(@Param("roleId") Long roleId);

    @Delete("DELETE FROM sys_role_menu WHERE menu_id = #{menuId}")
    int physicalDeleteByMenuId(@Param("menuId") Long menuId);
}
