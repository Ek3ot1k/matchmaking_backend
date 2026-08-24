package com.football.backend.service;

import com.football.backend.dto.ResultVoteRequest;
import com.football.backend.dto.ResultVotingSummaryDTO;
import com.football.backend.entity.MatchEntity;
import com.football.backend.entity.MatchParticipantEntity;
import com.football.backend.entity.MatchResultVoteEntity;
import com.football.backend.entity.UserEntity;
import com.football.backend.model.MatchStatus;
import com.football.backend.model.ParticipantStatus;
import com.football.backend.model.ResultVoteDecision;
import com.football.backend.repository.MatchParticipantRepository;
import com.football.backend.repository.MatchRepository;
import com.football.backend.repository.MatchResultVoteRepository;
import com.football.backend.repository.PlayerStatsRepository;
import com.football.backend.repository.UserRepository;
import com.football.backend.util.MatchRatingCalculator;
import com.football.backend.util.PlayerSkillCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchResultServiceTest {

    @Mock private MatchRepository matchRepository;
    @Mock private MatchParticipantRepository participantRepository;
    @Mock private MatchResultVoteRepository resultVoteRepository;
    @Mock private PlayerStatsRepository playerStatsRepository;
    @Mock private UserRepository userRepository;
    @Mock private MatchRatingCalculator ratingCalculator;
    @Mock private PlayerSkillCalculator playerSkillCalculator;
    @Mock private NotificationService notificationService;
    @Mock private CacheManager cacheManager;

    private MatchResultService service;
    private MatchEntity match;
    private UserEntity voter;

    @BeforeEach
    void setUp() {
        service = new MatchResultService(
                matchRepository, participantRepository, resultVoteRepository,
                playerStatsRepository, userRepository, ratingCalculator,
                playerSkillCalculator, notificationService, cacheManager
        );
        voter = UserEntity.builder().id(10L).telegramId(100L).build();
        match = MatchEntity.builder()
                .id(20L)
                .status(MatchStatus.RESULT_PENDING)
                .resultVotingEndsAt(LocalDateTime.now().plusHours(2))
                .resultEligibleVoters(4)
                .resultConfirmationsRequired(3)
                .build();
        when(matchRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(match));
    }

    @Test
    void firstConfirmationNeverClosesVotingEarly() {
        MatchParticipantEntity participant = MatchParticipantEntity.builder()
                .match(match)
                .user(voter)
                .status(ParticipantStatus.REGISTERED)
                .build();
        when(participantRepository.findByMatchIdAndUserId(20L, 10L))
                .thenReturn(Optional.of(participant));
        when(resultVoteRepository.findByMatchIdAndUserId(20L, 10L))
                .thenReturn(Optional.empty());
        when(resultVoteRepository.countByMatchId(20L)).thenReturn(1L);

        ResultVotingSummaryDTO summary = service.vote(
                20L, 10L, new ResultVoteRequest(ResultVoteDecision.CONFIRM, null)
        );

        assertThat(match.getStatus()).isEqualTo(MatchStatus.RESULT_PENDING);
        assertThat(summary.canVote()).isTrue();
        assertThat(summary.confirmations()).isNull();
        verify(resultVoteRepository).save(any(MatchResultVoteEntity.class));
        verify(playerSkillCalculator, never()).updateSkills(any(), any(), any(Integer.class));
    }

    @Test
    void disagreementRequiresMeaningfulReason() {
        MatchParticipantEntity participant = MatchParticipantEntity.builder()
                .match(match)
                .user(voter)
                .status(ParticipantStatus.REGISTERED)
                .build();
        when(participantRepository.findByMatchIdAndUserId(20L, 10L))
                .thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> service.vote(
                20L, 10L, new ResultVoteRequest(ResultVoteDecision.DISAGREE, "неверно")
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("минимум в 10 символах");

        verify(resultVoteRepository, never()).save(any());
    }

    @Test
    void administratorCannotApproveBeforeDeadline() {
        assertThatThrownBy(() -> service.adminApprove(20L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("раньше трехчасового дедлайна");

        assertThat(match.getStatus()).isEqualTo(MatchStatus.RESULT_PENDING);
        verify(matchRepository, never()).save(any());
    }
}
