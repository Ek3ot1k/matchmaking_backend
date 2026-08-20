package com.football.backend.controller;

import com.football.backend.dto.CreateMatchRequest;
import com.football.backend.dto.MatchDTO;
import com.football.backend.dto.MatchFilterRequest;
import com.football.backend.security.UserEntityDetails;
import com.football.backend.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {
    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping
    public Page<MatchDTO> getAllMatches(@ModelAttribute MatchFilterRequest filter,
                                        Pageable pageable){
        return matchService.getMatches(filter,pageable);
    }

    @PostMapping
    public MatchDTO createMatch(
            @AuthenticationPrincipal UserEntityDetails userDetails,
            @Valid @RequestBody CreateMatchRequest request){
        return matchService.createMatch(userDetails.getUserId(),request);
    }
}
