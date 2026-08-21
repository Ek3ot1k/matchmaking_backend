package com.football.backend.service;

import com.football.backend.dto.VoteRequestDTO;
import com.football.backend.entity.MatchEntity;
import com.football.backend.entity.PlayerStatsEntity;
import com.football.backend.entity.UserEntity;
import com.football.backend.entity.VoteEntity;
import com.football.backend.model.MatchStatus;
import com.football.backend.model.VoteCategory;
import com.football.backend.repository.*;
import com.football.backend.util.PlayerSkillCalculator;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VoteService {

    private final VoteRepository voteRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final PlayerSkillCalculator playerSkillCalculator;


    public VoteService(VoteRepository voteRepository,
                       MatchRepository matchRepository,
                       UserRepository userRepository,
                       MatchParticipantRepository matchParticipantRepository,
                       PlayerStatsRepository playerStatsRepository,
                       PlayerSkillCalculator playerSkillCalculator) {
        this.voteRepository = voteRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.playerStatsRepository = playerStatsRepository;
        this.playerSkillCalculator = playerSkillCalculator;
    }

    @Transactional
    public void submitVote(Long matchId, Long voterId, VoteRequestDTO request) {
        // Достаем участников
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));

        UserEntity voter = userRepository.findById(voterId)
                .orElseThrow(() -> new EntityNotFoundException("Голосующий не найден"));

        UserEntity target = userRepository.findById(request.targetId())
                .orElseThrow(() -> new EntityNotFoundException("Кандидат не найден"));

        if (match.getStatus()!=MatchStatus.COMPLETED){
            throw new IllegalStateException("Матч не завершен");
        }

        if(voterId.equals(target.getId())){
            throw new IllegalStateException("Нельзя голосовать за самого себя");
        }

        if (!matchParticipantRepository.existsByMatchIdAndUserId(matchId,voterId)){
            throw new IllegalStateException("Вы не играли в этом матче");
        }

        if (!matchParticipantRepository.existsByMatchIdAndUserId(matchId,target.getId())){
            throw new IllegalStateException("Данный игрок не играл в матче");
        }

        if(voteRepository.existsByMatchIdAndVoterIdAndCategory(matchId,voterId, request.category())){
            throw new IllegalStateException("Ваш голос уже отдан");
        }
        // 6. Если все проверки пройдены, собери VoteEntity через builder (передай match, voter, target, category)
        // и сохрани через voteRepository.save(...)
        VoteEntity voteEntity=VoteEntity.builder()
                .match(match)
                .voter(voter)
                .target(target)
                .category(request.category())
                .build();

        voteRepository.save(voteEntity);
    }

    @CacheEvict(value = {"leaderboard_goals", "leaderboard_assists", "leaderboard_mvp", "leaderboard_ga"}, allEntries = true)
    @Transactional
    public void processVotingResults(MatchEntity match){
        List<VoteEntity> votes=voteRepository.findByMatchId(match.getId());
        List<PlayerStatsEntity> stats=playerStatsRepository.findByMatchId(match.getId());

        for(PlayerStatsEntity stat:stats){
            Long userId=stat.getUser().getId();

            // Считаем сколько раз этот юзер встречается как target в категории MVP
            int mvpVotes = (int) votes.stream()
                    .filter(v -> v.getCategory() == VoteCategory.MVP && v.getTarget().getId().equals(userId))
                    .count();

            // Считаем сколько раз как Fastest
            int fastestVotes = (int) votes.stream()
                    .filter(v -> v.getCategory() == VoteCategory.FASTEST_PLAYER && v.getTarget().getId().equals(userId))
                    .count();

            // 3. Если за игрока голосовали — обновляем стату и даем бонус к OVR
            if (mvpVotes > 0 || fastestVotes > 0) {
                stat.setMvpVotes(stat.getMvpVotes() + mvpVotes);
                stat.setFastestPlayerVotes(stat.getFastestPlayerVotes() + fastestVotes);

                playerSkillCalculator.applyVotingBonuses(stat.getUser(), mvpVotes, fastestVotes);

                // Сохраняем обновленного юзера
                userRepository.save(stat.getUser());
            }
        }

        playerStatsRepository.saveAll(stats);

        // Закрываем голосование для этого матча, чтобы Scheduler больше его не трогал
        match.setVotingClosed(true);
        matchRepository.save(match);

        System.out.println("Голосование закрыто для матча: " + match.getId());
    }
}