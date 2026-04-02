package com.tianzhu.tianjing.drift.domain;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;

@Data
@TableName("sys_user")
public class SysUserView {
    @TableId(type = IdType.AUTO) private Long id;

    @TableField("user_id")
    private String userId;

    @TableField("username")
    private String username;

    @TableField("display_name")
    private String displayName;

    @TableField("email")
    private String email;

    @TableField("status")
    private String status;

    @TableLogic private Boolean isDeleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT) private OffsetDateTime createdAt;
}
