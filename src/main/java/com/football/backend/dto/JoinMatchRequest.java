package com.football.backend.dto;

import com.football.backend.model.Position;
import jakarta.validation.constraints.NotNull;

public record JoinMatchRequest(@NotNull(message = "Выберите позицию для этого матча") Position position) {}
