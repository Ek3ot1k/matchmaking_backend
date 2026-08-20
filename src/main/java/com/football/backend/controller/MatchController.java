package com.football.backend.controller;

import com.football.backend.dto.CreateMatchRequest;
import com.football.backend.dto.MatchDTO;
import com.football.backend.dto.MatchFilterRequest;
import com.football.backend.dto.UpdateMatchRequest;
import com.football.backend.model.ParticipantStatus;
import com.football.backend.security.UserEntityDetails;
import com.football.backend.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
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
    public MatchDTO createDraft(
            @AuthenticationPrincipal UserEntityDetails userDetails,
            @Valid @RequestBody CreateMatchRequest request){
        return matchService.createDraft(userDetails.getUserId(),request);
    }

    @PostMapping("/{id}/publish")
    public MatchDTO publishMatch(
            @PathVariable("id") Long matchId,
            @AuthenticationPrincipal UserEntityDetails userDetails
    ){
        return matchService.publishMatch(matchId,userDetails.getUserId());
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<String> joinMatch(
            @PathVariable("id") Long matchId,
            @AuthenticationPrincipal UserEntityDetails userDetails
    ){
        matchService.joinMatch(matchId,userDetails.getUserId());
        return ResponseEntity.ok("Вы успешно записаны на матч!");
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<String> leaveMatch(
            @PathVariable("id") Long matchId,
            @AuthenticationPrincipal UserEntityDetails userDetails
    ){
        matchService.leaveMatch(matchId,userDetails.getUserId());
        return ResponseEntity.ok("Вы успешно отписались от матча");
    }

    @PostMapping("/{id}/waitlist/join")
    public ResponseEntity<String> joinWaitList(
            @PathVariable("id") Long matchId,
            @AuthenticationPrincipal UserEntityDetails userDetails
    ){
        matchService.joinWaitList(matchId,userDetails.getUserId());
        return ResponseEntity.ok("Вы успешно добавлены в лист ожидания!");
    }

    @PatchMapping("/{id}")
    public MatchDTO updateMatch(
            @PathVariable("id") Long matchId,
            @AuthenticationPrincipal UserEntityDetails userDetails,
            @Valid @RequestBody UpdateMatchRequest request
            ){
        return matchService.updateMatch(matchId, userDetails.getUserId(),request);
    }

    @PatchMapping("/{matchId}/participants/{userId}/confirm")
    public ResponseEntity<String> confirmParticipant(
            @PathVariable("matchId") Long matchId,
            @PathVariable("userId") Long userId,
            @AuthenticationPrincipal UserEntityDetails userEntityDetails
    ){
        matchService.updateParticipantStatus(matchId,userId,userEntityDetails.getUserId(), ParticipantStatus.CONFIRMED);
        return ResponseEntity.ok("Участие игрока подтверждено");
    }


    @PatchMapping("/{matchId}/participants/{userId}/no-show")
    public ResponseEntity<String> markNoShow(
            @PathVariable("matchId") Long matchId,
            @PathVariable("userId") Long userId,
            @AuthenticationPrincipal UserEntityDetails userEntityDetails
    ){
        matchService.updateParticipantStatus(matchId,userId,userEntityDetails.getUserId(), ParticipantStatus.NO_SHOW);
        return ResponseEntity.ok("Игроку выставлена неявка");
    }



    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteMatch(
            @PathVariable("id") Long matchId,
            @AuthenticationPrincipal UserEntityDetails userDetails

    ){
        matchService.cancelMatch(matchId, userDetails.getUserId());
        return ResponseEntity.ok("Матч успешно отменен");
    }




}
