package com.football.backend.dto;

import com.football.backend.model.MatchStatus;

import java.time.LocalDateTime;
import java.util.List;

public record AdminResultReviewDTO(
        Long matchId,
        String location,
        String format,
        MatchStatus status,
        Integer scoreWhite,
        Integer scoreDark,
        LocalDateTime votingEndsAt,
        Integer eligibleVoters,
        Integer confirmationsRequired,
        long confirmations,
        long disagreements,
        List<AdminResultVoteDTO> votes
) {}
