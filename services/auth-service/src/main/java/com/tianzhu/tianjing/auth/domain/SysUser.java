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

    @TableField("user_id")
    private String userId;

    @TableField("username")
    private String username;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("display_name")
    private String displayName;

    @TableField("dept_code")
    private String deptCode;

    @TableField("email")
    private String email;

    @TableField("phone")
    private String phone;

    /** 账号状态：ACTIVE / LOCKED / INACTIVE */
    @TableField("status")
    private String status;

    @TableField("is_locked")
    private Boolean isLocked;

    @TableField("failed_login_count")
    private Integer failedLoginCount;

    @TableField("last_login_at")
    private OffsetDateTime lastLoginAt;

    @TableField("password_changed_at")
    private OffsetDateTime passwordChangedAt;

    @TableLogic
    private Boolean isDeleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedAt;

    @TableField("created_by")
    private String createdBy;

    @TableField("updated_by")
    private String updatedBy;

    @Version
    private Integer version;

    /** 运行时填充的角色列表（非数据库字段） */
    @TableField(exist = false)
    private List<String> roles;
}
