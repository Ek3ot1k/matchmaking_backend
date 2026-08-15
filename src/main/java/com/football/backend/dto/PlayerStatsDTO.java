package com.football.backend.dto;

public record PlayerStatsDTO(Long id,
                             Long userId,
                             Long matchId,
                             Integer goals,
                             Integer assists,
                             Integer mvpVotes,
                             Integer fastestPlayerVotes) {
}
