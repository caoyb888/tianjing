package com.tianzhu.tianjing.algomodel.dto;
import jakarta.validation.constraints.NotNull;
public record ModelApproveRequest(@NotNull Boolean approved, String comment) {}
