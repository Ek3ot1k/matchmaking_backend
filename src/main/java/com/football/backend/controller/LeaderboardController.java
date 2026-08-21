package com.football.backend.controller;

import com.football.backend.dto.LeaderboardEntryDTO;
import com.football.backend.service.LeaderboardService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    // Топ бомбардиров
    @GetMapping("/goals")
    public ResponseEntity<List<LeaderboardEntryDTO>> getTopScorers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(leaderboardService.getTopScorers(
                resolveStartDate(startDate), resolveEndDate(endDate), limit));
    }

    // 2. Топ ассистентов
    @GetMapping("/assists")
    public ResponseEntity<List<LeaderboardEntryDTO>> getTopAssistants(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(leaderboardService.getTopAssistants(
                resolveStartDate(startDate), resolveEndDate(endDate), limit));
    }

    // 3. Топ MVP
    @GetMapping("/mvp")
    public ResponseEntity<List<LeaderboardEntryDTO>> getTopMvp(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(leaderboardService.getTopMvp(
                resolveStartDate(startDate), resolveEndDate(endDate), limit));
    }

    // 4. Топ по системе Гол+Пас
    @GetMapping("/ga")
    public ResponseEntity<List<LeaderboardEntryDTO>> getTopGoalPlusPass(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "10") int limit) {

        return ResponseEntity.ok(leaderboardService.getTopGA(
                resolveStartDate(startDate), resolveEndDate(endDate), limit));
    }

    // --- Вспомогательные методы для дефолтных дат (За всё время) ---

    private LocalDateTime resolveStartDate(LocalDateTime startDate) {
        // Если дату не передали, берем 2000 год (считай, за всю историю)
        return startDate != null ? startDate : LocalDateTime.of(2000, 1, 1, 0, 0);
    }

    private LocalDateTime resolveEndDate(LocalDateTime endDate) {
        // Если дату не передали, берем плюс 1 год от текущего момента
        return endDate != null ? endDate : LocalDateTime.now().plusYears(1);
    }
}