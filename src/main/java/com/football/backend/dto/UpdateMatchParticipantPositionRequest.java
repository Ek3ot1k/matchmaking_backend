package com.football.backend.dto;

import com.football.backend.model.Position;
import jakarta.validation.constraints.NotNull;

public record UpdateMatchParticipantPositionRequest(@NotNull(message = "Выберите новую позицию") Position position) {}
