package com.tianzhu.tianjing.dashboard.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
public record SandboxPromoteRequest(@JsonProperty("promote_reason") String promoteReason) {}
