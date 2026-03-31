package com.tianzhu.tianjing.drift.domain;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;
@Data
@TableName("sys_user")
public class SysUserView {
    @TableId(type = IdType.AUTO) private Long id;
    private String userId;
    private String username;
    private String displayName;
    private String email;
    private String status;
    @TableLogic private Integer isDeleted;
    @TableField(fill = FieldFill.INSERT) private OffsetDateTime createdAt;
}
