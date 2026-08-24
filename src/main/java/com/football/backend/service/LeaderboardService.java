package com.football.backend.service;

import com.football.backend.dto.LeaderboardEntryDTO;
import com.football.backend.repository.PlayerStatsRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LeaderboardService {

    private final PlayerStatsRepository playerStatsRepository;

    public LeaderboardService(PlayerStatsRepository playerStatsRepository) {
        this.playerStatsRepository = playerStatsRepository;
    }

    // @Cacheable означает: "Если метод вызовут с такими же параметрами,
    // не выполняй код внутри, а просто отдай результат из кэша 'leaderboard_goals'"
    @Cacheable(value = "leaderboard_goals", key = "#seasonStart.toString() + '-' + #seasonEnd.toString()")
    public List<LeaderboardEntryDTO> getTopScorers(LocalDateTime seasonStart, LocalDateTime seasonEnd, int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);

        return playerStatsRepository.getTopScorers(seasonStart, seasonEnd, pageRequest);
    }

    // ТВОЯ ЗАДАЧА:
    // Напиши аналогичные методы для:
    // 1. getTopAssistants (используй value = "leaderboard_assists")
    // 2. getTopMvp (используй value = "leaderboard_mvp")
    // 3. getTopGA (используй value = "leaderboard_ga")
    @Cacheable(value = "leaderboard_assists",key = "#seasonStart.toString() + '-' + #seasonEnd.toString()")
    public List<LeaderboardEntryDTO> getTopAssistants(LocalDateTime seasonStart, LocalDateTime seasonEnd, int limit){
        PageRequest pageRequest=PageRequest.of(0,limit);

        return playerStatsRepository.getTopAssistant(seasonStart,seasonEnd,pageRequest);
    }

    @Cacheable(value = "leaderboard_mvp",key = "#seasonStart.toString() + '-' + #seasonEnd.toString()")
    public List<LeaderboardEntryDTO> getTopMvp(LocalDateTime seasonStart, LocalDateTime seasonEnd, int limit){
        PageRequest pageRequest=PageRequest.of(0,limit);

        return playerStatsRepository.getTopMVP(seasonStart,seasonEnd,pageRequest);
    }

    @Cacheable(value = "leaderboard_ga",key = "#seasonStart.toString() + '-' + #seasonEnd.toString()")
    public List<LeaderboardEntryDTO> getTopGA(LocalDateTime seasonStart, LocalDateTime seasonEnd, int limit){
        PageRequest pageRequest=PageRequest.of(0,limit);

        return playerStatsRepository.getTopGA(seasonStart,seasonEnd,pageRequest);
    }

    @Cacheable(value = "leaderboard_matches", key = "#seasonStart.toString() + '-' + #seasonEnd.toString()")
    public List<LeaderboardEntryDTO> getTopByMatches(LocalDateTime seasonStart, LocalDateTime seasonEnd, int limit) {
        return playerStatsRepository.getTopByMatches(seasonStart, seasonEnd, PageRequest.of(0, limit));
    }

}
