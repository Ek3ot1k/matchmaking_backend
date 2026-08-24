package com.football.backend.scheduler;

import com.football.backend.service.MatchResultService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ResultVotingScheduler {
    private static final Logger log = LoggerFactory.getLogger(ResultVotingScheduler.class);
    private final MatchResultService matchResultService;

    public ResultVotingScheduler(MatchResultService matchResultService) {
        this.matchResultService = matchResultService;
    }

    @Scheduled(fixedDelayString = "${app.results.scheduler-delay-ms:60000}")
    public void finalizeExpiredResultVotings() {
        for (Long matchId : matchResultService.findExpiredResultIds()) {
            try {
                matchResultService.finalizeIfExpired(matchId);
            } catch (RuntimeException exception) {
                log.error("Не удалось завершить голосование по матчу {}", matchId, exception);
            }
        }
    }
}
