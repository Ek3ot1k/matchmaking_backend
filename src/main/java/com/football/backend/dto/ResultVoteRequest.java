package com.football.backend.dto;

import com.football.backend.model.ResultVoteDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ResultVoteRequest(
        @NotNull ResultVoteDecision decision,
        @Size(max = 500) String reason
) {}
