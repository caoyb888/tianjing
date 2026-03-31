package com.tianzhu.tianjing.auth.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianzhu.tianjing.auth.domain.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
        SELECT r.role_code FROM sys_role r
        INNER JOIN sys_user_role ur ON ur.role_id = r.id
        WHERE ur.user_id = #{userId}
          AND r.is_deleted = 0
        """)
    List<String> selectRolesByUserId(Long userId);
}
