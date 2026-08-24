package com.football.backend.service;

import com.football.backend.entity.MatchEntity;
import com.football.backend.entity.MatchParticipantEntity;
import com.football.backend.model.MatchStatus;
import com.football.backend.model.ParticipantStatus;
import com.football.backend.model.Position;
import com.football.backend.model.TeamColor;
import com.football.backend.repository.MatchParticipantRepository;
import com.football.backend.repository.MatchRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeamBalancerService {

    private final MatchRepository matchRepository;
    private final MatchParticipantRepository matchParticipantRepository;

    public TeamBalancerService(MatchRepository matchRepository, MatchParticipantRepository matchParticipantRepository) {
        this.matchRepository = matchRepository;
        this.matchParticipantRepository = matchParticipantRepository;
    }

    @Transactional
    public void balanceTeams(Long matchId, Long organizerId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));

        if (!match.getOrganizer().getId().equals(organizerId)) {
            throw new AccessDeniedException("Только организатор может распределять составы");
        }
        if (match.getStatus() != MatchStatus.OPEN) {
            throw new IllegalStateException("Распределять игроков можно только в активном матче");
        }

        distribute(matchId);
    }

    /**
     * Автоматически распределяет игроков сразу после записи/отмены записи.
     * Команды всегда отличаются по числу игроков максимум на одного, а при равенстве
     * приоритет — одинаковому числу позиций и близкому суммарному OVR.
     */
    @Transactional
    public void autoBalanceTeams(Long matchId) {
        if (!matchRepository.existsById(matchId)) {
            throw new EntityNotFoundException("Матч не найден");
        }
        distribute(matchId);
    }

    private void distribute(Long matchId) {
        List<MatchParticipantEntity> activeParticipants = matchParticipantRepository
                .findByMatchIdAndStatusNot(matchId, ParticipantStatus.NO_SHOW);

        if (activeParticipants.isEmpty()) {
            return;
        }

        int maxPerTeam = (int) Math.ceil(activeParticipants.size() / 2.0);

        List<MatchParticipantEntity> teamWhite = new ArrayList<>();
        List<MatchParticipantEntity> teamDark = new ArrayList<>();

        int whiteOvr = 0;
        int darkOvr = 0;
        int[] whitePosCounts = new int[Position.values().length];
        int[] darkPosCounts = new int[Position.values().length];

        // 1. Сортировка вратарей
        List<MatchParticipantEntity> goalkeepers = activeParticipants.stream()
                .filter(p -> positionOf(p) == Position.GOALKEEPER)
                .sorted(Comparator.comparingInt((MatchParticipantEntity p) -> p.getUser().getOvr()).reversed())
                .collect(Collectors.toList());

        for (MatchParticipantEntity gk : goalkeepers) {
            if (whiteOvr <= darkOvr && teamWhite.size() < maxPerTeam) {
                addToTeam(teamWhite, gk, whitePosCounts);
                whiteOvr += gk.getUser().getOvr();
            } else {
                addToTeam(teamDark, gk, darkPosCounts);
                darkOvr += gk.getUser().getOvr();
            }
        }

        // 2. Сортировка полевых
        List<MatchParticipantEntity> fieldPlayers = activeParticipants.stream()
                .filter(p -> positionOf(p) != Position.GOALKEEPER)
                .sorted(Comparator.comparingInt((MatchParticipantEntity p) -> p.getUser().getOvr()).reversed())
                .collect(Collectors.toList());

        for (MatchParticipantEntity player : fieldPlayers) {
            if (teamWhite.size() >= maxPerTeam) {
                addToTeam(teamDark, player, darkPosCounts);
                darkOvr += player.getUser().getOvr();
                continue;
            }
            if (teamDark.size() >= maxPerTeam) {
                addToTeam(teamWhite, player, whitePosCounts);
                whiteOvr += player.getUser().getOvr();
                continue;
            }

            int posIndex = positionOf(player).ordinal();

            if (whitePosCounts[posIndex] < darkPosCounts[posIndex]) {
                addToTeam(teamWhite, player, whitePosCounts);
                whiteOvr += player.getUser().getOvr();
            } else if (darkPosCounts[posIndex] < whitePosCounts[posIndex]) {
                addToTeam(teamDark, player, darkPosCounts);
                darkOvr += player.getUser().getOvr();
            } else {
                if (whiteOvr <= darkOvr) {
                    addToTeam(teamWhite, player, whitePosCounts);
                    whiteOvr += player.getUser().getOvr();
                } else {
                    addToTeam(teamDark, player, darkPosCounts);
                    darkOvr += player.getUser().getOvr();
                }
            }
        }

        // 3. ФАЗА МУТАЦИИ И СОХРАНЕНИЯ
        // Только когда расчет полностью и успешно завершен, мы применяем цвета к сущностям
        teamWhite.forEach(p -> p.setTeamColor(TeamColor.WHITE));
        teamDark.forEach(p -> p.setTeamColor(TeamColor.DARK));

        matchParticipantRepository.saveAll(activeParticipants);
    }

    // Метод для автоматического пересчета
    @Transactional
    public void rebalanceIfAlreadyBalanced(Long matchId, Long organizerId) {
        // Проверяем, есть ли в матче игроки, которым уже присвоен цвет команды
        boolean isAlreadyBalanced = matchParticipantRepository
                .existsByMatchIdAndTeamColorNot(matchId, TeamColor.NONE);

        // Если составы уже были распределены — запускаем пересчет
        if (isAlreadyBalanced) {
            System.out.println("-> [AUTO-REBALANCE] Состав изменился. Запускаем автоматический пересчет для матча " + matchId);
            autoBalanceTeams(matchId);
        }
    }

    private void addToTeam(List<MatchParticipantEntity> team, MatchParticipantEntity player, int[] posCounters) {
        team.add(player);
        posCounters[positionOf(player).ordinal()]++;
    }

    private Position positionOf(MatchParticipantEntity participant) {
        // Старые записи до появления снимка не содержат исторической позиции.
        // Для них сохраняем прежнее отображение до следующей записи в матч.
        return participant.getPosition() != null
                ? participant.getPosition()
                : participant.getUser().getPosition();
    }
}
