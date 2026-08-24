package com.football.backend.service;

import com.football.backend.entity.MatchEntity;
import com.football.backend.entity.MatchParticipantEntity;
import com.football.backend.model.MatchStatus;
import com.football.backend.model.ParticipantStatus;
import com.football.backend.repository.MatchParticipantRepository;
import com.football.backend.repository.MatchRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Переводит опубликованные матчи из набора в игру ровно в назначенное время.
 * Этот код находится на сервере: открыть старую версию Mini App или напрямую
 * вызвать API не позволит обойти правила старта.
 */
@Service
public class MatchLifecycleService {
    public static final int MINIMUM_PLAYERS_TO_START = 10;
    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Moscow");

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository participantRepository;
    private final TeamBalancerService teamBalancerService;
    private final NotificationService notificationService;

    public MatchLifecycleService(MatchRepository matchRepository,
                                 MatchParticipantRepository participantRepository,
                                 TeamBalancerService teamBalancerService,
                                 NotificationService notificationService) {
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
        this.teamBalancerService = teamBalancerService;
        this.notificationService = notificationService;
    }

    @Transactional
    public void processDueMatches() {
        LocalDateTime now = LocalDateTime.now(APP_ZONE);
        List<Long> dueMatchIds = matchRepository
                .findByStatusAndDateTimeLessThanEqual(MatchStatus.OPEN, now)
                .stream()
                .map(MatchEntity::getId)
                .toList();

        for (Long matchId : dueMatchIds) {
            processOne(matchId, now);
        }
    }

    private void processOne(Long matchId, LocalDateTime now) {
        MatchEntity match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));
        if (match.getStatus() != MatchStatus.OPEN || match.getDateTime().isAfter(now)) {
            return;
        }

        List<MatchParticipantEntity> participants = participantRepository
                .findByMatchIdAndStatusNot(matchId, ParticipantStatus.NO_SHOW);

        if (participants.size() < MINIMUM_PLAYERS_TO_START) {
            match.setStatus(MatchStatus.CANCELLED);
            matchRepository.save(match);
            notifyParticipants(participants, String.format(
                    "⚠️ Матч на площадке %s отменён: к началу собралось %d из минимум %d игроков.",
                    match.getLocation(), participants.size(), MINIMUM_PLAYERS_TO_START
            ));
            return;
        }

        resizeFormatToRoster(match, participants.size());
        teamBalancerService.autoBalanceTeams(matchId);
        match.setStatus(MatchStatus.IN_PROGRESS);
        matchRepository.save(match);
        notifyParticipants(participants, String.format(
                "⚽ Матч на площадке %s начался. Формат: %s. Хорошей игры!",
                match.getLocation(), match.getFormat()
        ));
    }

    /**
     * В приложении есть только 5×5, 6×6 и 7×7. При старте подбирается
     * наибольший из этих форматов, который можно провести собравшимся составом.
     */
    private void resizeFormatToRoster(MatchEntity match, int players) {
        int requestedTeamSize = readTeamSize(match);
        int availableTeamSize = (int) Math.ceil(players / 2.0);
        int effectiveTeamSize = Math.max(5, Math.min(7, Math.min(requestedTeamSize, availableTeamSize)));

        match.setFormat(effectiveTeamSize + "×" + effectiveTeamSize);
        match.setMaxPlayers(effectiveTeamSize * 2);
        match.setMinPlayers(MINIMUM_PLAYERS_TO_START);
    }

    private int readTeamSize(MatchEntity match) {
        try {
            int parsed = Integer.parseInt(String.valueOf(match.getFormat()).split("[×xX]")[0].trim());
            return parsed >= 5 ? Math.min(7, parsed) : Math.max(5, Math.min(7, match.getMaxPlayers() / 2));
        } catch (RuntimeException ignored) {
            return Math.max(5, Math.min(7, match.getMaxPlayers() / 2));
        }
    }

    private void notifyParticipants(List<MatchParticipantEntity> participants, String message) {
        notificationService.sendToUsers(participants.stream()
                .map(participant -> participant.getUser().getTelegramId())
                .filter(telegramId -> telegramId != null)
                .toList(), message);
    }
}
