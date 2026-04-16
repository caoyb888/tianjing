package com.tianzhu.tianjing.scene.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RollbackRequest(
        @NotNull @Min(1)
        @JsonProperty("target_version") Integer targetVersion,

        @JsonProperty("rollback_reason") String rollbackReason
) {}
