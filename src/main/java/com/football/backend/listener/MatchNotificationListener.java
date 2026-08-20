package com.football.backend.listener;

import com.football.backend.dto.MatchCancelledEvent;
import com.football.backend.dto.MatchRescheduledEvent;
import com.football.backend.entity.MatchParticipantEntity;
import com.football.backend.repository.MatchParticipantRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MatchNotificationListener {
    private final MatchParticipantRepository matchParticipantRepository;

    // TODO: В будущем сюда можно будет заинжектить сервис отправки сообщений в Телеграм
    // private final TelegramBotService telegramBotService;

    public MatchNotificationListener(MatchParticipantRepository matchParticipantRepository) {
        this.matchParticipantRepository = matchParticipantRepository;
    }

    @EventListener
    public void onMatchCancelled(MatchCancelledEvent event){
        List<MatchParticipantEntity> participants=matchParticipantRepository.findByMatchId(event.matchId());

        for (MatchParticipantEntity participant : participants) {
            Long telegramId = participant.getUser().getTelegramId();
            String username = participant.getUser().getUsername();

            // В будущем тут будет что-то вроде:
            // telegramBotService.sendMessage(telegramId, "Матч отменен!");

            System.out.println("-> [TELEGRAM BOT] Отправка сообщения юзеру @" + username + " (ID: " + telegramId + "): Матч " + event.matchId() + " ОТМЕНЕН!");
        }
    }

    @EventListener
    public void onMatchRescheduled(MatchRescheduledEvent event) {
        List<MatchParticipantEntity> participants = matchParticipantRepository.findByMatchId(event.matchId());

        for (MatchParticipantEntity participant : participants) {
            Long telegramId = participant.getUser().getTelegramId();

            System.out.println("-> [TELEGRAM BOT] Отправка сообщения в TG (ID: " + telegramId + "): Матч " + event.matchId() + " ПЕРЕНЕСЕН!");
            System.out.println("   Старое время: " + event.oldTime() + " | Новое: " + event.newTime());
            System.out.println("   Старое место: " + event.oldLocation() + " | Новое: " + event.newLocation());
        }
    }
}
