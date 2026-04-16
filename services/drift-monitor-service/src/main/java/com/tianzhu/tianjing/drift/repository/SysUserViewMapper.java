package com.tianzhu.tianjing.drift.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.drift.domain.SysUserView;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SysUserViewMapper extends BaseMapper<SysUserView> {

    /** 查询用户角色列表 */
    @Select("SELECT ur.role_code FROM sys_user_role ur WHERE ur.user_id = #{userId}")
    List<String> selectRolesByUserId(String userId);

    /** 新增用户（含 password_hash，SysUserView 实体不含此字段） */
    @Insert("""
        INSERT INTO sys_user (user_id, username, display_name, password_hash,
            email, phone, dept_code, status, created_by, updated_by)
        VALUES (#{userId}, #{username}, #{displayName}, #{passwordHash},
            #{email}, #{phone}, #{deptCode}, 'ACTIVE', #{createdBy}, #{createdBy})
        """)
    void insertUserWithPassword(String userId, String username, String displayName,
                                String passwordHash, String email, String phone,
                                String deptCode, String createdBy);

    /** 逻辑删除用户 */
    @Update("UPDATE sys_user SET is_deleted = true, updated_by = #{operator} WHERE user_id = #{userId}")
    void softDeleteByUserId(String userId, String operator);

    /** 新增用户角色 */
    @Insert("""
        INSERT INTO sys_user_role (user_id, role_code, granted_by)
        VALUES (#{userId}, #{roleCode}, #{grantedBy})
        ON CONFLICT (user_id, role_code) DO NOTHING
        """)
    void insertUserRole(String userId, String roleCode, String grantedBy);

    /** 删除用户所有角色 */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    void deleteUserRoles(String userId);
}
