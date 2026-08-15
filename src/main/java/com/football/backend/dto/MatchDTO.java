package com.football.backend.dto;

import com.football.backend.model.MatchStatus;

import java.time.LocalDateTime;

public record MatchDTO(Long id,
                       LocalDateTime dateTime,
                       String location,
                       MatchStatus status) {
}
