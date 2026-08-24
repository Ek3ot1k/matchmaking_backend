package com.football.backend.dto;

import com.football.backend.model.MatchStatus;

import java.time.LocalDateTime;
import java.util.List;

public record MatchDetailsDTO(
        Long id,
        String format,
        String location,
        LocalDateTime dateTime,
        Integer currentPlayers,
        Integer maxPlayers,
        MatchStatus status,
        Integer scoreWhite,
        Integer scoreDark,
        LocalDateTime resultVotingEndsAt,
        Integer resultEligibleVoters,
        Integer resultConfirmationsRequired,
        UserDTO organizer,
        List<MatchParticipantDetailsDTO> participants,
        List<MatchWaitlistDetailsDTO> waitlist
) {}
