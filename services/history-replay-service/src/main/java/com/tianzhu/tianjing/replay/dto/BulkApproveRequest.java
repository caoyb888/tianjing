package com.tianzhu.tianjing.replay.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 批量通过审核请求
 */
public record BulkApproveRequest(
        @NotBlank
        @Pattern(regexp = "all_unmodified|by_ids", message = "模式必须是 all_unmodified 或 by_ids")
        @JsonProperty("mode")
        String mode,

        /**
         * mode=by_ids 时指定帧ID列表
         */
        @JsonProperty("frame_ids")
        List<String> frameIds
) {}
