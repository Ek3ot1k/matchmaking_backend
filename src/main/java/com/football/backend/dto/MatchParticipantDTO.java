package com.football.backend.dto;

import com.football.backend.model.TeamColor;

public record MatchParticipantDTO(Long id,
                                  Long userId,
                                  Long matchId,
                                  TeamColor teamColor) {
}
