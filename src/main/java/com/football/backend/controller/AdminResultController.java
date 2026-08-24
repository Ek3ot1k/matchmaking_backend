package com.football.backend.controller;

import com.football.backend.dto.AdminResultReviewDTO;
import com.football.backend.service.MatchResultService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/results")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminResultController {
    private final MatchResultService matchResultService;

    public AdminResultController(MatchResultService matchResultService) {
        this.matchResultService = matchResultService;
    }

    @GetMapping
    public List<AdminResultReviewDTO> queue() {
        return matchResultService.getAdminQueue();
    }

    @PostMapping("/{matchId}/approve")
    public ResponseEntity<String> approve(@PathVariable Long matchId) {
        matchResultService.adminApprove(matchId);
        return ResponseEntity.ok("Результат подтвержден администратором");
    }

    @PostMapping("/{matchId}/reject")
    public ResponseEntity<String> reject(@PathVariable Long matchId) {
        matchResultService.adminReject(matchId);
        return ResponseEntity.ok("Результат отклонен");
    }

    @PostMapping("/{matchId}/reopen")
    public ResponseEntity<String> reopen(@PathVariable Long matchId) {
        matchResultService.adminReopen(matchId);
        return ResponseEntity.ok("Голосование открыто повторно на три часа");
    }
}
