package com.football.backend.util;

import com.football.backend.dto.PlayerMatchStatDTO;
import com.football.backend.entity.MatchParticipantEntity;
import com.football.backend.model.Position;
import com.football.backend.model.TeamColor;
import org.springframework.stereotype.Component;

@Component
public class MatchRatingCalculator {
    // Базовые константы для формулы
    private static final double BASE_RATING = 6.0;
    private static final double WIN_BONUS = 1.0;
    private static final double DRAW_BONUS = 0.5;
    private static final double LOSE_PENALTY = -0.5;

    private static final double GOAL_BONUS = 1.0;
    private static final double ASSIST_BONUS = 0.5;
    private static final double MVP_VOTE_BONUS = 0.2;
    private static final double FASTEST_VOTE_BONUS = 0.1;

    // Бонусы для вратарей и защитников за игру на "ноль"
    private static final double CLEAN_SHEET_GK_BONUS = 1.5;
    private static final double CLEAN_SHEET_DEF_BONUS = 0.8;

    public Double calculateRating(
            PlayerMatchStatDTO stats,
            MatchParticipantEntity participant,
            int scoreWhite,
            int scoreDark
    ){
        double rating=BASE_RATING;
        TeamColor playerTeam = participant.getTeamColor();
        Position position = participant.getUser().getPosition();

        // 1. Учитываем результат матча
        if (playerTeam == TeamColor.WHITE) {
            rating += getResultModifier(scoreWhite, scoreDark);
        } else if (playerTeam == TeamColor.DARK) {
            rating += getResultModifier(scoreDark, scoreWhite);
        }

        // 2. Учитываем результативные действия
        rating += (stats.goals() * GOAL_BONUS);
        rating += (stats.assists() * ASSIST_BONUS);

        // 3. Учитываем голосования
        rating += (stats.mvpVotes() * MVP_VOTE_BONUS);
        rating += (stats.fastestPlayerVotes() * FASTEST_VOTE_BONUS);

        // 4. Бонус за "Сухарь" (Cleansheet) для Вратарей и Защитников
        boolean isCleanSheet = (playerTeam == TeamColor.WHITE && scoreDark == 0) ||
                (playerTeam == TeamColor.DARK && scoreWhite == 0);

        if (isCleanSheet) {
            if (position == Position.GOALKEEPER) {
                rating += CLEAN_SHEET_GK_BONUS;
            } else if (position == Position.DEFENDER) {
                rating += CLEAN_SHEET_DEF_BONUS;
            }
        }

        // Ограничиваем рейтинг диапазоном [1.0 ... 10.0]
        rating = Math.max(1.0, Math.min(10.0, rating));

        // Округляем до одного знака после запятой (например 7.4)
        return Math.round(rating * 10.0) / 10.0;
    }

    private double getResultModifier(int myTeamScore, int opponentScore) {
        if (myTeamScore > opponentScore) return WIN_BONUS;
        if (myTeamScore == opponentScore) return DRAW_BONUS;
        return LOSE_PENALTY;
    }
}
