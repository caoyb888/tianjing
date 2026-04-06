package com.tianzhu.tianjing.drift.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 系统角色实体（对应 sys_role 表）
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("role_code")
    @JsonProperty("role_code")
    private String roleCode;

    @TableField("role_name")
    @JsonProperty("role_name")
    private String roleName;

    @TableField("description")
    private String description;

    @TableLogic
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private Boolean isDeleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @TableField("created_by")
    @JsonProperty("created_by")
    private String createdBy;
}
