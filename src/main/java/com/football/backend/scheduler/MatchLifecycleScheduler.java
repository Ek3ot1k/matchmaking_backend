package com.football.backend.scheduler;

import com.football.backend.service.MatchLifecycleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Проверяет старт матчей сразу после запуска приложения и затем раз в минуту. */
@Component
public class MatchLifecycleScheduler {
    private final MatchLifecycleService matchLifecycleService;

    public MatchLifecycleScheduler(MatchLifecycleService matchLifecycleService) {
        this.matchLifecycleService = matchLifecycleService;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 0)
    public void processDueMatches() {
        matchLifecycleService.processDueMatches();
    }
}
