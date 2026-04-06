package com.tianzhu.tianjing.drift.domain;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;
import java.util.List;

@Data
@TableName("sys_user")
public class SysUserView {
    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private String userId;

    @TableField("username")
    private String username;

    @TableField("display_name")
    private String displayName;

    @TableField("email")
    private String email;

    @TableField("phone")
    private String phone;

    @TableField("dept_code")
    private String deptCode;

    @TableField("status")
    private String status;

    @TableField("last_login_at")
    private OffsetDateTime lastLoginAt;

    @TableLogic
    private Boolean isDeleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private OffsetDateTime createdAt;

    /** 运行时填充，非数据库字段 */
    @TableField(exist = false)
    private List<String> roles;
}
