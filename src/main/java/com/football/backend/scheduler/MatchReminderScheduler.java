package com.football.backend.scheduler;

import com.football.backend.entity.MatchEntity;
import com.football.backend.entity.MatchParticipantEntity;
import com.football.backend.model.MatchStatus;
import com.football.backend.repository.MatchParticipantRepository;
import com.football.backend.repository.MatchRepository;
import com.football.backend.service.NotificationService;
import org.apache.catalina.LifecycleState;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class MatchReminderScheduler {
    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final NotificationService notificationService;

    public MatchReminderScheduler(MatchRepository matchRepository,
                                  MatchParticipantRepository matchParticipantRepository,
                                  NotificationService notificationService) {
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void checkAndSendReminders(){
        LocalDateTime now=LocalDateTime.now();

        List<MatchEntity> openMatches=matchRepository.findByStatus(MatchStatus.OPEN);

        for(MatchEntity match:openMatches){
            LocalDateTime matchTime=match.getDateTime();
            if (matchTime==null) continue;

            List<MatchParticipantEntity> participants=matchParticipantRepository.findByMatchId(match.getId());
            List<Long> telegramIds=participants.stream()
                    .map(p->p.getUser().getId())
                    .filter(id->id!=null)
                    .toList();

            if (telegramIds.isEmpty()) continue;

            if (!match.isReminder24hSent()
                    && now.plusHours(23).isBefore(matchTime)
                    && now.plusHours(24).isAfter(matchTime)){
                String text = String.format("⏰ Напоминание: Завтра в %s состоится матч на арене \"%s\". Готовьте бутсы!",
                        matchTime.toLocalTime(), match.getLocation());

                notificationService.sendToUsers(telegramIds, text);
                match.setReminder24hSent(true);
            }

            if (!match.isReminder2hSent() &&
                    now.plusHours(1).isBefore(matchTime) &&
                    now.plusHours(2).isAfter(matchTime)) {

                String text = String.format("⚡ До матча на арене \"%s\" осталось всего 2 часа! Время собираться.",
                        match.getLocation());

                notificationService.sendToUsers(telegramIds, text);
                match.setReminder2hSent(true);
            }

            if (!match.isReminder30minSent() &&
                    now.plusMinutes(25).isBefore(matchTime) &&
                    now.plusMinutes(30).isAfter(matchTime)) {

                String text = String.format("🚨 Внимание! Матч начнется через 30 минут на арене \"%s\". Ждем всех на поле!",
                        match.getLocation());

                notificationService.sendToUsers(telegramIds, text);
                match.setReminder30minSent(true);
            }

            // Сохраняем обновленные флаги в базу
            matchRepository.save(match);
        }
    }
}
