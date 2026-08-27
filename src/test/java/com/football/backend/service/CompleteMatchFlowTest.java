package com.football.backend.service;

import com.football.backend.dto.CreateMatchRequest;
import com.football.backend.dto.FinishMatchRequest;
import com.football.backend.dto.PlayerMatchStatDTO;
import com.football.backend.dto.ResultVoteRequest;
import com.football.backend.dto.VoteRequestDTO;
import com.football.backend.entity.MatchEntity;
import com.football.backend.entity.MatchParticipantEntity;
import com.football.backend.entity.MatchResultVoteEntity;
import com.football.backend.entity.PlayerStatsEntity;
import com.football.backend.entity.UserEntity;
import com.football.backend.entity.VoteEntity;
import com.football.backend.model.MatchStatus;
import com.football.backend.model.ParticipantStatus;
import com.football.backend.model.Position;
import com.football.backend.model.ResultVoteDecision;
import com.football.backend.model.TeamColor;
import com.football.backend.model.VoteCategory;
import com.football.backend.repository.MatchParticipantRepository;
import com.football.backend.repository.MatchRepository;
import com.football.backend.repository.MatchResultVoteRepository;
import com.football.backend.repository.MatchWaitlistRepository;
import com.football.backend.repository.PlayerStatsRepository;
import com.football.backend.repository.UserRepository;
import com.football.backend.repository.VoteRepository;
import com.football.backend.scheduler.ResultVotingScheduler;
import com.football.backend.scheduler.VotingScheduler;
import com.football.backend.util.MatchRatingCalculator;
import com.football.backend.util.PlayerSkillCalculator;
import org.junit.jupiter.api.Test;
import org.modelmapper.ModelMapper;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Сквозной сценарий официального матча без подключения к боевой базе:
 * создание -> набор -> балансировка -> старт -> протокол -> подтверждение
 * результата -> начисление статистики -> номинации -> закрытие голосования.
 */
class CompleteMatchFlowTest {

    @Test
    void completesOfficialMatchAndPersistsStatsAndNominations() {
        MatchRepository matchRepository = mock(MatchRepository.class);
        MatchParticipantRepository participantRepository = mock(MatchParticipantRepository.class);
        MatchWaitlistRepository waitlistRepository = mock(MatchWaitlistRepository.class);
        MatchResultVoteRepository resultVoteRepository = mock(MatchResultVoteRepository.class);
        PlayerStatsRepository statsRepository = mock(PlayerStatsRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        VoteRepository voteRepository = mock(VoteRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        UserDisciplineService disciplineService = mock(UserDisciplineService.class);
        CacheManager cacheManager = mock(CacheManager.class);

        Map<Long, UserEntity> users = testUsers();
        AtomicReference<MatchEntity> storedMatch = new AtomicReference<>();
        List<MatchParticipantEntity> participants = new ArrayList<>();
        List<PlayerStatsEntity> stats = new ArrayList<>();
        Map<Long, MatchResultVoteEntity> resultVotes = new LinkedHashMap<>();
        List<VoteEntity> nominationVotes = new ArrayList<>();
        AtomicLong participantSequence = new AtomicLong(1);

        when(userRepository.findByIdForUpdate(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(users.get(invocation.getArgument(0))));
        when(userRepository.findById(anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(users.get(invocation.getArgument(0))));
        when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(matchRepository.save(any(MatchEntity.class))).thenAnswer(invocation -> {
            MatchEntity match = invocation.getArgument(0);
            if (match.getId() == null) ReflectionTestUtils.setField(match, "id", 900L);
            storedMatch.set(match);
            return match;
        });
        when(matchRepository.findById(anyLong())).thenAnswer(invocation -> matchById(storedMatch, invocation));
        when(matchRepository.findByIdForUpdate(anyLong())).thenAnswer(invocation -> matchById(storedMatch, invocation));
        when(matchRepository.existsById(anyLong())).thenAnswer(invocation ->
                storedMatch.get() != null && storedMatch.get().getId().equals(invocation.getArgument(0)));
        when(matchRepository.findByCreationRequestId(any())).thenReturn(Optional.empty());
        when(matchRepository.countCreatedByOrganizerBetween(anyLong(), any(), any())).thenReturn(0L);
        when(matchRepository.findVenueConflictCandidates(anyLong(), anyList(), any(), any())).thenReturn(List.of());
        when(matchRepository.findByStatusAndDateTimeLessThanEqual(any(), any())).thenAnswer(invocation -> {
            MatchEntity match = storedMatch.get();
            LocalDateTime cutoff = invocation.getArgument(1);
            return match != null && match.getStatus() == invocation.getArgument(0)
                    && !match.getDateTime().isAfter(cutoff) ? List.of(match) : List.of();
        });
        when(matchRepository.findByStatusAndResultVotingEndsAtLessThanEqual(any(), any())).thenAnswer(invocation -> {
            MatchEntity match = storedMatch.get();
            LocalDateTime cutoff = invocation.getArgument(1);
            return match != null && match.getStatus() == invocation.getArgument(0)
                    && match.getResultVotingEndsAt() != null
                    && !match.getResultVotingEndsAt().isAfter(cutoff) ? List.of(match) : List.of();
        });
        when(matchRepository.findByStatusAndVotingClosedFalseAndFinishedAtBefore(any(), any())).thenAnswer(invocation -> {
            MatchEntity match = storedMatch.get();
            LocalDateTime cutoff = invocation.getArgument(1);
            return match != null && match.getStatus() == invocation.getArgument(0)
                    && !Boolean.TRUE.equals(match.getVotingClosed())
                    && match.getFinishedAt() != null && match.getFinishedAt().isBefore(cutoff)
                    ? List.of(match) : List.of();
        });

        when(participantRepository.save(any(MatchParticipantEntity.class))).thenAnswer(invocation -> {
            MatchParticipantEntity participant = invocation.getArgument(0);
            if (participant.getId() == null) {
                ReflectionTestUtils.setField(participant, "id", participantSequence.getAndIncrement());
                participants.add(participant);
            }
            return participant;
        });
        when(participantRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(participantRepository.findByMatchId(anyLong())).thenAnswer(invocation ->
                participantsFor(participants, invocation.getArgument(0), null));
        when(participantRepository.findByMatchIdAndStatusNot(anyLong(), any())).thenAnswer(invocation ->
                participantsFor(participants, invocation.getArgument(0), invocation.getArgument(1)));
        when(participantRepository.existsByMatchIdAndUserId(anyLong(), anyLong())).thenAnswer(invocation ->
                findParticipant(participants, invocation.getArgument(0), invocation.getArgument(1)).isPresent());
        when(participantRepository.findByMatchIdAndUserId(anyLong(), anyLong())).thenAnswer(invocation ->
                findParticipant(participants, invocation.getArgument(0), invocation.getArgument(1)));
        when(participantRepository.existsByMatchIdAndTeamColorNot(anyLong(), any())).thenAnswer(invocation -> {
            Long matchId = invocation.getArgument(0);
            TeamColor color = invocation.getArgument(1);
            return participants.stream().anyMatch(p -> p.getMatch().getId().equals(matchId)
                    && p.getTeamColor() != color);
        });
        when(waitlistRepository.existsByMatchIdAndUserId(anyLong(), anyLong())).thenReturn(false);

        when(statsRepository.findByMatchId(anyLong())).thenAnswer(invocation -> stats.stream()
                .filter(stat -> stat.getMatch().getId().equals(invocation.getArgument(0)))
                .toList());
        when(statsRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<PlayerStatsEntity> saved = invocation.getArgument(0);
            for (PlayerStatsEntity stat : saved) {
                boolean alreadyStored = stats.stream().anyMatch(existing ->
                        existing.getMatch().getId().equals(stat.getMatch().getId())
                                && existing.getUser().getId().equals(stat.getUser().getId()));
                if (!alreadyStored) stats.add(stat);
            }
            return saved;
        });

        when(resultVoteRepository.findByMatchIdAndUserId(anyLong(), anyLong())).thenAnswer(invocation ->
                Optional.ofNullable(resultVotes.get(invocation.getArgument(1))));
        when(resultVoteRepository.save(any(MatchResultVoteEntity.class))).thenAnswer(invocation -> {
            MatchResultVoteEntity vote = invocation.getArgument(0);
            resultVotes.put(vote.getUser().getId(), vote);
            return vote;
        });
        when(resultVoteRepository.countByMatchId(anyLong())).thenAnswer(invocation -> (long) resultVotes.size());
        when(resultVoteRepository.countByMatchIdAndDecision(anyLong(), any())).thenAnswer(invocation -> {
            ResultVoteDecision decision = invocation.getArgument(1);
            return resultVotes.values().stream().filter(vote -> vote.getDecision() == decision).count();
        });

        when(voteRepository.existsByMatchIdAndVoterIdAndCategory(anyLong(), anyLong(), any())).thenAnswer(invocation ->
                nominationVotes.stream().anyMatch(vote ->
                        vote.getMatch().getId().equals(invocation.getArgument(0))
                                && vote.getVoter().getId().equals(invocation.getArgument(1))
                                && vote.getCategory() == invocation.getArgument(2)));
        when(voteRepository.save(any(VoteEntity.class))).thenAnswer(invocation -> {
            VoteEntity vote = invocation.getArgument(0);
            nominationVotes.add(vote);
            return vote;
        });
        when(voteRepository.findByMatchId(anyLong())).thenAnswer(invocation -> nominationVotes.stream()
                .filter(vote -> vote.getMatch().getId().equals(invocation.getArgument(0)))
                .toList());

        MatchRatingCalculator ratingCalculator = new MatchRatingCalculator();
        PlayerSkillCalculator skillCalculator = new PlayerSkillCalculator();
        TeamBalancerService teamBalancer = new TeamBalancerService(matchRepository, participantRepository);
        MatchResultService resultService = new MatchResultService(
                matchRepository, participantRepository, resultVoteRepository, statsRepository,
                userRepository, ratingCalculator, skillCalculator, notificationService, cacheManager
        );
        ReflectionTestUtils.setField(resultService, "votingHours", 3L);
        ReflectionTestUtils.setField(resultService, "confirmationPercent", 70);
        ReflectionTestUtils.setField(resultService, "minimumParticipants", 4);

        MatchService matchService = new MatchService(
                matchRepository, mock(ModelMapper.class), userRepository, participantRepository,
                waitlistRepository, eventPublisher, teamBalancer, ratingCalculator, statsRepository,
                skillCalculator, notificationService, resultService, disciplineService
        );
        MatchLifecycleService lifecycleService = new MatchLifecycleService(
                matchRepository, participantRepository, teamBalancer, notificationService
        );
        VoteService voteService = new VoteService(
                voteRepository, matchRepository, userRepository, participantRepository,
                statsRepository, skillCalculator
        );

        matchService.createDraft(1L, new CreateMatchRequest(
                "5×5", "Тестовая арена", 55.751244, 37.618423,
                LocalDateTime.now().plusHours(2), 10, Position.FORWARD,
                null, "complete-flow-900"
        ));
        for (long userId = 2; userId <= 10; userId++) {
            matchService.joinMatch(900L, userId, users.get(userId).getPosition());
        }

        MatchEntity match = storedMatch.get();
        assertThat(match.getCurrentPlayers()).isEqualTo(10);
        assertThat(participants).hasSize(10);
        assertThat(participants).filteredOn(p -> p.getTeamColor() == TeamColor.WHITE).hasSize(5);
        assertThat(participants).filteredOn(p -> p.getTeamColor() == TeamColor.DARK).hasSize(5);
        assertThat(participants).anyMatch(p -> p.getUser().getId().equals(1L)
                && p.getTeamColor() == TeamColor.WHITE);

        match.setDateTime(LocalDateTime.now().minusMinutes(20));
        lifecycleService.processDueMatches();
        assertThat(match.getStatus()).isEqualTo(MatchStatus.IN_PROGRESS);

        List<Long> whiteIds = teamUserIds(participants, TeamColor.WHITE);
        List<Long> darkIds = teamUserIds(participants, TeamColor.DARK);
        Map<Long, int[]> actions = new LinkedHashMap<>();
        users.keySet().forEach(id -> actions.put(id, new int[]{0, 0}));
        actions.put(whiteIds.get(0), new int[]{2, 1});
        actions.put(whiteIds.get(1), new int[]{1, 1});
        actions.put(darkIds.get(0), new int[]{1, 1});
        actions.put(darkIds.get(1), new int[]{1, 1});
        List<PlayerMatchStatDTO> protocolRows = actions.entrySet().stream()
                .map(entry -> new PlayerMatchStatDTO(
                        entry.getKey(), entry.getValue()[0], entry.getValue()[1], 0, 0))
                .toList();

        resultService.submitProtocol(900L, 1L, new FinishMatchRequest(3, 2, protocolRows));
        assertThat(match.getStatus()).isEqualTo(MatchStatus.RESULT_PENDING);
        assertThat(match.getResultEligibleVoters()).isEqualTo(10);
        assertThat(match.getResultConfirmationsRequired()).isEqualTo(7);
        assertThat(stats).hasSize(10);
        assertThat(stats).allMatch(stat -> stat.getMatchRating() != null);

        for (long voterId = 1; voterId <= 7; voterId++) {
            resultService.vote(900L, voterId,
                    new ResultVoteRequest(ResultVoteDecision.CONFIRM, null));
        }
        match.setResultVotingEndsAt(LocalDateTime.now().minusSeconds(1));
        new ResultVotingScheduler(resultService).finalizeExpiredResultVotings();

        assertThat(match.getStatus()).isEqualTo(MatchStatus.COMPLETED);
        assertThat(match.getScoreWhite()).isEqualTo(3);
        assertThat(match.getScoreDark()).isEqualTo(2);
        assertThat(match.getFinishedAt()).isNotNull();
        assertThat(stats).extracting(PlayerStatsEntity::getGoals).containsExactlyInAnyOrder(2, 1, 1, 1, 0, 0, 0, 0, 0, 0);
        assertThat(stats).extracting(PlayerStatsEntity::getAssists).containsExactlyInAnyOrder(1, 1, 1, 1, 0, 0, 0, 0, 0, 0);
        assertThat(users.get(whiteIds.get(0)).getShoot()).isGreaterThan(65);

        voteService.submitVote(900L, 2L, new VoteRequestDTO(1L, VoteCategory.MVP));
        voteService.submitVote(900L, 3L, new VoteRequestDTO(1L, VoteCategory.MVP));
        voteService.submitVote(900L, 4L, new VoteRequestDTO(5L, VoteCategory.FASTEST_PLAYER));
        voteService.submitVote(900L, 7L, new VoteRequestDTO(6L, VoteCategory.BEST_DEFENDER));
        match.setFinishedAt(LocalDateTime.now().minusHours(25));
        new VotingScheduler(matchRepository, voteService).closeExpiredVotings();

        PlayerStatsEntity organizerStats = statFor(stats, 1L);
        PlayerStatsEntity fastestStats = statFor(stats, 5L);
        assertThat(nominationVotes).hasSize(4);
        assertThat(nominationVotes).extracting(VoteEntity::getCategory)
                .contains(VoteCategory.MVP, VoteCategory.FASTEST_PLAYER, VoteCategory.BEST_DEFENDER);
        assertThat(organizerStats.getMvpVotes()).isEqualTo(2);
        assertThat(fastestStats.getFastestPlayerVotes()).isEqualTo(1);
        assertThat(match.getVotingClosed()).isTrue();
    }

    private static Map<Long, UserEntity> testUsers() {
        Position[] positions = {
                Position.FORWARD, Position.GOALKEEPER, Position.GOALKEEPER,
                Position.DEFENDER, Position.DEFENDER, Position.MIDFIELDER,
                Position.MIDFIELDER, Position.FORWARD, Position.WINGER, Position.WINGER
        };
        Map<Long, UserEntity> users = new LinkedHashMap<>();
        for (int index = 0; index < positions.length; index++) {
            long id = index + 1L;
            users.put(id, UserEntity.builder()
                    .id(id)
                    .telegramId(10_000L + id)
                    .username("test_player_" + id)
                    .firstName("Игрок " + id)
                    .position(positions[index])
                    .ovr(65 + index)
                    .pace(65).shoot(65).pass(65).dribbling(65).defend(65).physic(65)
                    .build());
        }
        return users;
    }

    private static Optional<MatchEntity> matchById(AtomicReference<MatchEntity> storedMatch,
                                                    org.mockito.invocation.InvocationOnMock invocation) {
        MatchEntity match = storedMatch.get();
        Long requestedId = invocation.getArgument(0);
        return match != null && match.getId().equals(requestedId) ? Optional.of(match) : Optional.empty();
    }

    private static List<MatchParticipantEntity> participantsFor(List<MatchParticipantEntity> participants,
                                                                 Long matchId,
                                                                 ParticipantStatus excludedStatus) {
        return participants.stream()
                .filter(participant -> participant.getMatch().getId().equals(matchId))
                .filter(participant -> excludedStatus == null || participant.getStatus() != excludedStatus)
                .toList();
    }

    private static Optional<MatchParticipantEntity> findParticipant(List<MatchParticipantEntity> participants,
                                                                     Long matchId,
                                                                     Long userId) {
        return participants.stream()
                .filter(participant -> participant.getMatch().getId().equals(matchId))
                .filter(participant -> participant.getUser().getId().equals(userId))
                .findFirst();
    }

    private static List<Long> teamUserIds(List<MatchParticipantEntity> participants, TeamColor color) {
        return participants.stream()
                .filter(participant -> participant.getTeamColor() == color)
                .map(participant -> participant.getUser().getId())
                .toList();
    }

    private static PlayerStatsEntity statFor(List<PlayerStatsEntity> stats, Long userId) {
        return stats.stream()
                .filter(stat -> stat.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow();
    }
}
