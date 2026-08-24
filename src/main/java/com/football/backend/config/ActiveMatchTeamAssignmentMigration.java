package com.football.backend.config;

import com.football.backend.model.MatchStatus;
import com.football.backend.repository.MatchRepository;
import com.football.backend.service.TeamBalancerService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** Assigns teams for active matches created before automatic assignment existed. */
@Component
public class ActiveMatchTeamAssignmentMigration implements ApplicationRunner {
    private final MatchRepository matchRepository;
    private final TeamBalancerService teamBalancerService;

    public ActiveMatchTeamAssignmentMigration(MatchRepository matchRepository,
                                               TeamBalancerService teamBalancerService) {
        this.matchRepository = matchRepository;
        this.teamBalancerService = teamBalancerService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<MatchStatus> activeStatuses = List.of(MatchStatus.DRAFT, MatchStatus.OPEN, MatchStatus.IN_PROGRESS);
        matchRepository.findAll().stream()
                .filter(match -> activeStatuses.contains(match.getStatus()))
                .forEach(match -> teamBalancerService.autoBalanceTeams(match.getId()));
    }
}
