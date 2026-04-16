package com.tianzhu.tianjing.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.auth.domain.SysUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
        SELECT r.role_code FROM sys_role r
        INNER JOIN sys_user_role ur ON ur.role_code = r.role_code
        WHERE ur.user_id = (SELECT user_id FROM sys_user WHERE id = #{userId})
          AND r.is_deleted = false
        """)
    List<String> selectRolesByUserId(Long userId);

    @Insert("INSERT INTO sys_user_role(user_id, role_code, granted_by) VALUES(#{userId}, #{roleCode}, #{grantedBy})")
    void insertUserRole(String userId, String roleCode, String grantedBy);

    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    void deleteUserRoles(String userId);
}
