package com.football.backend.dto;

import com.football.backend.model.MatchStatus;

import java.time.LocalDateTime;
import java.util.List;

public record MatchDetailsDTO(
        Long id,
        String format,
        String location,
        Double latitude,
        Double longitude,
        LocalDateTime dateTime,
        Integer duration,
        Integer currentPlayers,
        Integer maxPlayers,
        MatchStatus status,
        Integer scoreWhite,
        Integer scoreDark,
        LocalDateTime resultVotingEndsAt,
        Integer resultEligibleVoters,
        Integer resultConfirmationsRequired,
        String whiteFormation,
        String darkFormation,
        String chatLink,
        UserDTO organizer,
        List<MatchParticipantDetailsDTO> participants,
        List<MatchWaitlistDetailsDTO> waitlist,
        List<MatchPlayerReportDTO> playerStats
) {}
