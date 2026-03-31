package com.tianzhu.tianjing.drift.domain;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.OffsetDateTime;
@Data
@TableName("sys_operation_log")
public class SysOperationLog {
    @TableId(type = IdType.AUTO) private Long id;
    private String operatorUsername;
    private String operationType;
    private String resourceId;
    private String requestBody;
    private String result;
    private String traceId;
    private String clientIp;
    @TableField(fill = FieldFill.INSERT) private OffsetDateTime createdAt;
}
