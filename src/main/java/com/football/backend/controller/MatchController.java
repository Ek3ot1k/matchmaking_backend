package com.football.backend.controller;

import com.football.backend.dto.*;
import com.football.backend.model.ParticipantStatus;
import com.football.backend.security.UserEntityDetails;
import com.football.backend.service.MatchService;
import com.football.backend.service.TeamBalancerService;
import com.football.backend.service.VoteService;
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
    private final TeamBalancerService teamBalancerService;
    private final VoteService voteService;

    public MatchController(MatchService matchService,
                           TeamBalancerService teamBalancerService,
                           VoteService voteService) {
        this.matchService = matchService;
        this.teamBalancerService = teamBalancerService;
        this.voteService = voteService;
    }

    @GetMapping
    public Page<MatchDTO> getAllMatches(@ModelAttribute MatchFilterRequest filter,
                                        Pageable pageable){
        return matchService.getMatches(filter,pageable);
    }

    @GetMapping("/{id}")
    public MatchDetailsDTO getMatch(@PathVariable("id") Long matchId) {
        return matchService.getMatchDetails(matchId);
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

    @PostMapping("/{id}/balance")
    public ResponseEntity<String> balanceTeams(
            @PathVariable("id") Long matchId,
            @AuthenticationPrincipal UserEntityDetails userEntityDetails
    ){
        teamBalancerService.balanceTeams(matchId,userEntityDetails.getUserId());
        return ResponseEntity.ok("Составы успешно распределены!");
    }

    @PostMapping("/{id}/finish")
    public MatchDetailsDTO finishMatch(
            @PathVariable("id") Long matchId,
            @AuthenticationPrincipal UserEntityDetails userDetails,
            @Valid @RequestBody FinishMatchRequest request
    ) {
        matchService.finishMatch(matchId, userDetails.getUserId(), request);
        return matchService.getMatchDetails(matchId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteMatch(
            @PathVariable("id") Long matchId,
            @AuthenticationPrincipal UserEntityDetails userDetails

    ){
        matchService.cancelMatch(matchId, userDetails.getUserId());
        return ResponseEntity.ok("Матч успешно отменен");
    }

    @PostMapping("/{id}/vote")
    public ResponseEntity<String> submitVote(
            @PathVariable("id") Long matchId,
            @RequestBody @Valid VoteRequestDTO requestDTO,
            @AuthenticationPrincipal UserEntityDetails userEntityDetails
            ){
        voteService.submitVote(matchId,userEntityDetails.getUserId(),requestDTO);
        return ResponseEntity.ok("Голос успешно учтен!");
    }

}
