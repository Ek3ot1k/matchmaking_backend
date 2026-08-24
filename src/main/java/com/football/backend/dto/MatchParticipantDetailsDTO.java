package com.football.backend.dto;

import com.football.backend.model.ParticipantStatus;
import com.football.backend.model.Position;
import com.football.backend.model.TeamColor;

public record MatchParticipantDetailsDTO(
        Long id,
        Long userId,
        Long matchId,
        TeamColor teamColor,
        Position position,
        ParticipantStatus status,
        UserDTO user
) {}
