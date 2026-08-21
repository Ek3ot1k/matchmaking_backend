package com.football.backend.scheduler;

import com.football.backend.entity.MatchEntity;
import com.football.backend.model.MatchStatus;
import com.football.backend.repository.MatchRepository;
import com.football.backend.service.VoteService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class VotingScheduler {

    private final MatchRepository matchRepository;
    private final VoteService voteService;

    public VotingScheduler(MatchRepository matchRepository, VoteService voteService) {
        this.matchRepository = matchRepository;
        this.voteService = voteService;
    }

    // Запускается каждый час (cron выражение)
    @Scheduled(cron = "0 0 * * * *")
    public void closeExpiredVotings() {
        System.out.println("[SCHEDULER] Проверка завершенных голосований...");
        LocalDateTime cutoff=LocalDateTime.now().minusHours(24);

        List<MatchEntity> matches=matchRepository
                .findByStatusAndVotingClosedFalseAndFinishedAtBefore(MatchStatus.COMPLETED,cutoff);

        for(MatchEntity match:matches){
            voteService.processVotingResults(match);
        }
    }


}




