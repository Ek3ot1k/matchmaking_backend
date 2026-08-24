package com.football.backend.service;

import com.football.backend.dto.*;
import com.football.backend.entity.*;
import com.football.backend.model.*;
import com.football.backend.repository.*;
import com.football.backend.util.MatchRatingCalculator;
import com.football.backend.util.PlayerSkillCalculator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MatchResultService {
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Moscow");
    private static final List<String> LEADERBOARD_CACHES = List.of(
            "leaderboard_goals", "leaderboard_assists", "leaderboard_mvp", "leaderboard_ga"
    );

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository participantRepository;
    private final MatchResultVoteRepository resultVoteRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final UserRepository userRepository;
    private final MatchRatingCalculator ratingCalculator;
    private final PlayerSkillCalculator playerSkillCalculator;
    private final NotificationService notificationService;
    private final CacheManager cacheManager;

    @Value("${app.results.voting-hours:3}")
    private long votingHours;

    @Value("${app.results.confirmation-percent:70}")
    private int confirmationPercent;

    @Value("${app.results.min-participants:4}")
    private int minimumParticipants;

    public MatchResultService(MatchRepository matchRepository,
                              MatchParticipantRepository participantRepository,
                              MatchResultVoteRepository resultVoteRepository,
                              PlayerStatsRepository playerStatsRepository,
                              UserRepository userRepository,
                              MatchRatingCalculator ratingCalculator,
                              PlayerSkillCalculator playerSkillCalculator,
                              NotificationService notificationService,
                              CacheManager cacheManager) {
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
        this.resultVoteRepository = resultVoteRepository;
        this.playerStatsRepository = playerStatsRepository;
        this.userRepository = userRepository;
        this.ratingCalculator = ratingCalculator;
        this.playerSkillCalculator = playerSkillCalculator;
        this.notificationService = notificationService;
        this.cacheManager = cacheManager;
    }

    /**
     * Сохраняет предложенный протокол, но не начисляет статистику и рейтинг.
     * Официальным результат станет только после полного трехчасового окна голосования.
     */
    @Transactional
    public void submitProtocol(Long matchId, Long organizerId, FinishMatchRequest request) {
        MatchEntity match = lockedMatch(matchId);
        validateOrganizer(match, organizerId);
        if (match.getOrganizer().isOfficiallyBanned()) {
            throw new AccessDeniedException("Во время блокировки нельзя отправлять официальный протокол");
        }
        if (match.getStatus() != MatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Протокол доступен только для начатого матча");
        }

        int duration = Math.min(
                Optional.ofNullable(match.getDuration()).orElse(MatchEntity.MAX_DURATION_MINUTES),
                MatchEntity.MAX_DURATION_MINUTES
        );
        if (LocalDateTime.now(APP_ZONE).isBefore(match.getDateTime().plusMinutes(duration))) {
            throw new IllegalStateException("Протокол можно отправить только после завершения матча");
        }

        List<MatchParticipantEntity> eligible = participantRepository
                .findByMatchIdAndStatusNot(matchId, ParticipantStatus.NO_SHOW)
                .stream()
                .filter(p -> !p.getUser().isOfficiallyBanned())
                .toList();
        if (eligible.size() < minimumParticipants) {
            throw new IllegalStateException("Для рейтингового результата нужно минимум "
                    + minimumParticipants + " участника. Сейчас: " + eligible.size());
        }
        if (eligible.stream().anyMatch(p -> p.getTeamColor() == TeamColor.NONE)) {
            throw new IllegalStateException("Сначала распределите всех участников по командам");
        }

        Map<Long, PlayerMatchStatDTO> submitted = validateProtocol(request, eligible);
        saveProvisionalStats(match, request, eligible, submitted);

        LocalDateTime now = LocalDateTime.now();
        int required = (int) Math.ceil(eligible.size() * (confirmationPercent / 100.0));
        match.setScoreWhite(request.scoreWhite());
        match.setScoreDark(request.scoreDark());
        match.setStatus(MatchStatus.RESULT_PENDING);
        match.setFinishedAt(null);
        match.setUpdatedByOrganizerId(organizerId);
        match.setResultVotingStartedAt(now);
        match.setResultVotingEndsAt(now.plusHours(votingHours));
        match.setResultEligibleVoters(eligible.size());
        match.setResultConfirmationsRequired(required);
        resultVoteRepository.deleteByMatchId(matchId);
        matchRepository.save(match);

        notifyParticipants(eligible, String.format(
                "🗳 Организатор отправил протокол матча на арене %s: %d:%d. " +
                        "Голосование продлится %d часа. Подтвердите результат в Mini App.",
                match.getLocation(), request.scoreWhite(), request.scoreDark(), votingHours
        ));
    }

    @Transactional
    public ResultVotingSummaryDTO vote(Long matchId, Long userId, ResultVoteRequest request) {
        MatchEntity match = lockedMatch(matchId);
        if (match.getStatus() != MatchStatus.RESULT_PENDING) {
            throw new IllegalStateException("Голосование по этому результату не идет");
        }
        if (!LocalDateTime.now().isBefore(match.getResultVotingEndsAt())) {
            finalizeLocked(match);
            throw new IllegalStateException("Время голосования истекло");
        }

        MatchParticipantEntity participant = participantRepository.findByMatchIdAndUserId(matchId, userId)
                .filter(p -> p.getStatus() != ParticipantStatus.NO_SHOW && !p.getUser().isOfficiallyBanned())
                .orElseThrow(() -> new AccessDeniedException("Голосовать могут только участники матча"));

        String reason = request.reason() == null ? null : request.reason().trim();
        if (request.decision() == ResultVoteDecision.DISAGREE && (reason == null || reason.length() < 10)) {
            throw new IllegalArgumentException("При несогласии опишите причину минимум в 10 символах");
        }
        if (request.decision() == ResultVoteDecision.CONFIRM) {
            reason = null;
        }

        MatchResultVoteEntity vote = resultVoteRepository.findByMatchIdAndUserId(matchId, userId)
                .orElseGet(MatchResultVoteEntity::new);
        vote.setMatch(match);
        vote.setUser(participant.getUser());
        vote.setDecision(request.decision());
        vote.setReason(reason);
        resultVoteRepository.save(vote);

        // Намеренно не завершаем раньше срока, даже если уже проголосовали все.
        return buildSummary(match, userId);
    }

    @Transactional
    public ResultVotingSummaryDTO getSummary(Long matchId, Long userId) {
        MatchEntity match = lockedMatch(matchId);
        if (match.getStatus() == MatchStatus.RESULT_PENDING
                && !LocalDateTime.now().isBefore(match.getResultVotingEndsAt())) {
            finalizeLocked(match);
        }
        return buildSummary(match, userId);
    }

    @Transactional
    public void finalizeIfExpired(Long matchId) {
        MatchEntity match = lockedMatch(matchId);
        if (match.getStatus() == MatchStatus.RESULT_PENDING
                && match.getResultVotingEndsAt() != null
                && !LocalDateTime.now().isBefore(match.getResultVotingEndsAt())) {
            finalizeLocked(match);
        }
    }

    @Transactional(readOnly = true)
    public List<Long> findExpiredResultIds() {
        return matchRepository.findByStatusAndResultVotingEndsAtLessThanEqual(
                        MatchStatus.RESULT_PENDING, LocalDateTime.now())
                .stream()
                .map(MatchEntity::getId)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminResultReviewDTO> getAdminQueue() {
        return matchRepository.findByStatusInOrderByResultVotingEndsAtAsc(List.of(
                        MatchStatus.RESULT_PENDING,
                        MatchStatus.RESULT_DISPUTED,
                        MatchStatus.RESULT_REJECTED
                )).stream()
                .map(this::toAdminReview)
                .toList();
    }

    @Transactional
    public void adminApprove(Long matchId) {
        MatchEntity match = lockedMatch(matchId);
        if (match.getStatus() == MatchStatus.RESULT_PENDING) {
            throw new IllegalStateException("Нельзя завершать голосование раньше трехчасового дедлайна");
        }
        if (match.getStatus() != MatchStatus.RESULT_DISPUTED
                && match.getStatus() != MatchStatus.RESULT_REJECTED) {
            throw new IllegalStateException("Этот протокол не требует решения администратора");
        }
        acceptResult(match);
    }

    @Transactional
    public void adminReject(Long matchId) {
        MatchEntity match = lockedMatch(matchId);
        if (match.getStatus() == MatchStatus.COMPLETED) {
            throw new IllegalStateException("Подтвержденный результат нельзя отклонить этим действием");
        }
        if (match.getStatus() != MatchStatus.RESULT_DISPUTED
                && match.getStatus() != MatchStatus.RESULT_REJECTED) {
            throw new IllegalStateException("Сначала дождитесь окончания голосования");
        }
        match.setStatus(MatchStatus.RESULT_REJECTED);
        matchRepository.save(match);
    }

    @Transactional
    public void adminReopen(Long matchId) {
        MatchEntity match = lockedMatch(matchId);
        if (match.getStatus() != MatchStatus.RESULT_DISPUTED
                && match.getStatus() != MatchStatus.RESULT_REJECTED) {
            throw new IllegalStateException("Повторно открыть можно только спорный или отклоненный протокол");
        }
        LocalDateTime now = LocalDateTime.now();
        resultVoteRepository.deleteByMatchId(matchId);
        match.setStatus(MatchStatus.RESULT_PENDING);
        match.setResultVotingStartedAt(now);
        match.setResultVotingEndsAt(now.plusHours(votingHours));
        matchRepository.save(match);
    }

    private Map<Long, PlayerMatchStatDTO> validateProtocol(FinishMatchRequest request,
                                                            List<MatchParticipantEntity> eligible) {
        List<PlayerMatchStatDTO> rows = request.playersStats() == null ? List.of() : request.playersStats();
        Map<Long, PlayerMatchStatDTO> byUser = new HashMap<>();
        for (PlayerMatchStatDTO row : rows) {
            if (byUser.put(row.userId(), row) != null) {
                throw new IllegalArgumentException("Игрок продублирован в протоколе: " + row.userId());
            }
            if (row.mvpVotes() != 0 || row.fastestPlayerVotes() != 0) {
                throw new IllegalArgumentException("Организатор не может назначать себе или игрокам голоса MVP");
            }
        }

        Set<Long> eligibleIds = eligible.stream().map(p -> p.getUser().getId()).collect(Collectors.toSet());
        if (!byUser.keySet().equals(eligibleIds)) {
            throw new IllegalArgumentException("В протоколе должна быть строка для каждого пришедшего участника");
        }

        Map<Long, MatchParticipantEntity> participantByUser = eligible.stream().collect(Collectors.toMap(
                p -> p.getUser().getId(), Function.identity()
        ));
        int whiteGoals = 0;
        int darkGoals = 0;
        int whiteAssists = 0;
        int darkAssists = 0;
        for (PlayerMatchStatDTO row : rows) {
            TeamColor color = participantByUser.get(row.userId()).getTeamColor();
            if (color == TeamColor.WHITE) {
                whiteGoals += row.goals();
                whiteAssists += row.assists();
            } else if (color == TeamColor.DARK) {
                darkGoals += row.goals();
                darkAssists += row.assists();
            }
        }
        if (whiteGoals > request.scoreWhite() || darkGoals > request.scoreDark()) {
            throw new IllegalArgumentException("Сумма голов игроков не может превышать счет их команды");
        }
        if (whiteAssists > request.scoreWhite() || darkAssists > request.scoreDark()) {
            throw new IllegalArgumentException("Сумма ассистов не может превышать число голов команды");
        }
        return byUser;
    }

    private void saveProvisionalStats(MatchEntity match,
                                      FinishMatchRequest request,
                                      List<MatchParticipantEntity> eligible,
                                      Map<Long, PlayerMatchStatDTO> submitted) {
        Map<Long, PlayerStatsEntity> existing = playerStatsRepository.findByMatchId(match.getId()).stream()
                .collect(Collectors.toMap(s -> s.getUser().getId(), Function.identity()));

        List<PlayerStatsEntity> stats = eligible.stream().map(participant -> {
            PlayerMatchStatDTO row = submitted.get(participant.getUser().getId());
            PlayerStatsEntity stat = existing.getOrDefault(row.userId(), new PlayerStatsEntity());
            stat.setMatch(match);
            stat.setUser(participant.getUser());
            stat.setGoals(row.goals());
            stat.setAssists(row.assists());
            stat.setMvpVotes(0);
            stat.setFastestPlayerVotes(0);
            stat.setMatchRating(ratingCalculator.calculateRating(
                    row, participant, request.scoreWhite(), request.scoreDark()));
            return stat;
        }).toList();
        playerStatsRepository.saveAll(stats);
    }

    private void finalizeLocked(MatchEntity match) {
        long confirmations = resultVoteRepository.countByMatchIdAndDecision(
                match.getId(), ResultVoteDecision.CONFIRM);
        int required = Optional.ofNullable(match.getResultConfirmationsRequired()).orElse(Integer.MAX_VALUE);
        if (confirmations >= required) {
            acceptResult(match);
        } else {
            match.setStatus(MatchStatus.RESULT_DISPUTED);
            matchRepository.save(match);
            notifyMatchParticipants(match.getId(),
                    "⚠️ Результат матча не набрал достаточно подтверждений и отправлен администратору на проверку.");
        }
    }

    private void acceptResult(MatchEntity match) {
        if (match.getStatus() == MatchStatus.COMPLETED) return;

        List<MatchParticipantEntity> participants = participantRepository.findByMatchId(match.getId());
        Map<Long, MatchParticipantEntity> participantByUser = participants.stream().collect(Collectors.toMap(
                p -> p.getUser().getId(), Function.identity()
        ));
        for (PlayerStatsEntity stat : playerStatsRepository.findByMatchId(match.getId())) {
            MatchParticipantEntity participant = participantByUser.get(stat.getUser().getId());
            if (participant == null
                    || participant.getStatus() == ParticipantStatus.NO_SHOW
                    || stat.getUser().isOfficiallyBanned()) continue;
            int conceded = participant.getTeamColor() == TeamColor.WHITE
                    ? match.getScoreDark()
                    : match.getScoreWhite();
            playerSkillCalculator.updateSkills(stat.getUser(), stat, conceded);
            userRepository.save(stat.getUser());
        }

        match.setStatus(MatchStatus.COMPLETED);
        match.setFinishedAt(LocalDateTime.now());
        match.setVotingClosed(false);
        matchRepository.save(match);
        clearLeaderboardCaches();
        notifyParticipants(participants, String.format(
                "✅ Результат матча подтвержден: %d:%d. Статистика и рейтинг начислены.",
                match.getScoreWhite(), match.getScoreDark()
        ));
    }

    private ResultVotingSummaryDTO buildSummary(MatchEntity match, Long userId) {
        MatchResultVoteEntity current = resultVoteRepository.findByMatchIdAndUserId(match.getId(), userId)
                .orElse(null);
        int cast = Math.toIntExact(resultVoteRepository.countByMatchId(match.getId()));
        boolean open = match.getStatus() == MatchStatus.RESULT_PENDING
                && match.getResultVotingEndsAt() != null
                && LocalDateTime.now().isBefore(match.getResultVotingEndsAt());
        boolean eligible = participantRepository.findByMatchIdAndUserId(match.getId(), userId)
                .map(p -> p.getStatus() != ParticipantStatus.NO_SHOW && !p.getUser().isOfficiallyBanned())
                .orElse(false);
        boolean reveal = match.getStatus() != MatchStatus.RESULT_PENDING;
        Integer confirmations = reveal ? Math.toIntExact(resultVoteRepository.countByMatchIdAndDecision(
                match.getId(), ResultVoteDecision.CONFIRM)) : null;
        Integer disagreements = reveal ? Math.toIntExact(resultVoteRepository.countByMatchIdAndDecision(
                match.getId(), ResultVoteDecision.DISAGREE)) : null;

        return new ResultVotingSummaryDTO(
                match.getId(), match.getStatus(), match.getResultVotingEndsAt(),
                match.getResultEligibleVoters(), match.getResultConfirmationsRequired(), cast,
                confirmations, disagreements,
                current == null ? null : current.getDecision(),
                current == null ? null : current.getReason(),
                open && eligible
        );
    }

    private AdminResultReviewDTO toAdminReview(MatchEntity match) {
        List<AdminResultVoteDTO> votes = resultVoteRepository.findByMatchIdOrderByUpdatedAtAsc(match.getId())
                .stream()
                .map(v -> new AdminResultVoteDTO(
                        v.getUser().getId(), displayName(v.getUser()), v.getDecision(),
                        v.getReason(), v.getUpdatedAt()
                ))
                .toList();
        return new AdminResultReviewDTO(
                match.getId(), match.getLocation(), match.getFormat(), match.getStatus(),
                match.getScoreWhite(), match.getScoreDark(), match.getResultVotingEndsAt(),
                match.getResultEligibleVoters(), match.getResultConfirmationsRequired(),
                resultVoteRepository.countByMatchIdAndDecision(match.getId(), ResultVoteDecision.CONFIRM),
                resultVoteRepository.countByMatchIdAndDecision(match.getId(), ResultVoteDecision.DISAGREE),
                votes
        );
    }

    private MatchEntity lockedMatch(Long matchId) {
        return matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));
    }

    private void validateOrganizer(MatchEntity match, Long organizerId) {
        if (match.getOrganizer() == null || !match.getOrganizer().getId().equals(organizerId)) {
            throw new AccessDeniedException("Только организатор может отправить протокол матча");
        }
    }

    private void clearLeaderboardCaches() {
        for (String name : LEADERBOARD_CACHES) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
    }

    private void notifyMatchParticipants(Long matchId, String text) {
        notifyParticipants(participantRepository.findByMatchId(matchId), text);
    }

    private void notifyParticipants(List<MatchParticipantEntity> participants, String text) {
        List<Long> telegramIds = participants.stream()
                .map(MatchParticipantEntity::getUser)
                .map(UserEntity::getTelegramId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        notificationService.sendToUsers(telegramIds, text);
    }

    private String displayName(UserEntity user) {
        String fullName = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        if (!fullName.isBlank()) return fullName;
        return user.getUsername() == null ? "Игрок #" + user.getId() : "@" + user.getUsername();
    }
}
