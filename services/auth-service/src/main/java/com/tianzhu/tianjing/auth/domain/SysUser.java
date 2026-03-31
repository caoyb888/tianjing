package com.tianzhu.tianjing.auth.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 系统用户实体（对应 sys_user 表）
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String userId;
    private String username;
    private String passwordHash;
    private String displayName;
    private String email;
    private String phone;

    /** 账号状态：ACTIVE / LOCKED / INACTIVE */
    private String status;

    private Integer failedLoginCount;
    private OffsetDateTime lastLoginAt;
    private OffsetDateTime passwordChangedAt;

    @TableLogic
    private Integer isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    private String createdBy;

    /** 运行时填充的角色列表（非数据库字段） */
    @TableField(exist = false)
    private List<String> roles;
}
