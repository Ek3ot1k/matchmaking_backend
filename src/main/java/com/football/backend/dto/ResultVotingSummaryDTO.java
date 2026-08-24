package com.football.backend.dto;

import com.football.backend.model.MatchStatus;
import com.football.backend.model.ResultVoteDecision;

import java.time.LocalDateTime;

public record ResultVotingSummaryDTO(
        Long matchId,
        MatchStatus status,
        LocalDateTime votingEndsAt,
        Integer eligibleVoters,
        Integer confirmationsRequired,
        Integer votesCast,
        Integer confirmations,
        Integer disagreements,
        ResultVoteDecision currentUserDecision,
        String currentUserReason,
        boolean canVote
) {}
