package com.football.backend.dto;

import com.football.backend.model.VoteCategory;
import jakarta.validation.constraints.NotNull;

public record VoteRequestDTO(
        @NotNull Long targetId,
        @NotNull VoteCategory category
) {}