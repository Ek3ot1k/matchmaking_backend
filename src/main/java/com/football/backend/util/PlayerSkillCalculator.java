package com.football.backend.util;

import com.football.backend.entity.PlayerStatsEntity;
import com.football.backend.entity.UserEntity;
import com.football.backend.model.Position;
import org.springframework.stereotype.Component;

@Component
public class PlayerSkillCalculator {

    // Жесткие лимиты рейтинга (как в FIFA)
    private static final int MIN_RATING = 1;
    private static final int MAX_RATING = 99;

    public void updateSkills(UserEntity user, PlayerStatsEntity stats, int concededGoals) {

        // 1. Считаем изменения (Дельты) на основе статы матча
        int deltaSho = calculateShoDelta(stats);
        int deltaPas = calculatePasDelta(stats);
        int deltaPac = stats.getFastestPlayerVotes() * 2;
        int deltaPhy = stats.getMvpVotes();
        int deltaDri = calculateDriDelta(stats.getMatchRating());
        int deltaDef = calculateDefDelta(user.getPosition(), concededGoals, stats.getMatchRating());

        // 2. Применяем изменения к текущим скиллам пользователя с учетом границ 1-99
        user.setShoot(applyBounds(user.getShoot() + deltaSho));
        user.setPass(applyBounds(user.getPass() + deltaPas));
        user.setPace(applyBounds(user.getPace() + deltaPac));
        user.setPhysic(applyBounds(user.getPhysic() + deltaPhy));
        user.setDribbling(applyBounds(user.getDribbling() + deltaDri));
        user.setDefend(applyBounds(user.getDefend() + deltaDef));

        // 3. Пересчитываем OVR на основе новых показателей
        user.setOvr(calculateOvr(user));
    }

    // --- Внутренние формулы ---

    private int calculateShoDelta(PlayerStatsEntity stats) {
        if (stats.getGoals() > 0) {
            return stats.getGoals(); // +1 за каждый гол
        }
        return stats.getMatchRating() < 6.0 ? -1 : 0;
    }

    private int calculatePasDelta(PlayerStatsEntity stats) {
        int delta = stats.getAssists(); // +1 за каждый ассист
        if (stats.getMatchRating() > 7.5) delta += 1;
        if (stats.getMatchRating() < 5.5) delta -= 1;
        return delta;
    }

    private int calculateDriDelta(Double rating) {
        if (rating == null) return 0;
        if (rating >= 8.5) return 2;
        if (rating >= 7.0) return 1;
        if (rating < 5.0) return -1;
        return 0;
    }

    private int calculateDefDelta(Position position, int concededGoals, Double rating) {
        int delta = 0;
        // Защита качается активнее у защитников и вратарей
        if (position == Position.DEFENDER || position == Position.GOALKEEPER) {
            if (concededGoals == 0) delta += 2;
            else if (concededGoals > 3) delta -= 1;
        }
        // Общий бонус за хорошую игру в обороне (по оценке)
        if (rating != null && rating > 7.5) delta += 1;
        return delta;
    }

    // --- Взвешенный расчет OVR по позициям ---
    private Integer calculateOvr(UserEntity user) {
        double calculatedOvr = 0;

        switch (user.getPosition()) {
            case FORWARD:
                // Для чистого нападающего критически важен Удар и Скорость
                calculatedOvr = (user.getShoot() * 0.45) + (user.getPace() * 0.25) + (user.getDribbling() * 0.20) + (user.getPhysic() * 0.10);
                break;
            case MIDFIELDER:
                // Плеймейкеры живут пасом и дриблингом
                calculatedOvr = (user.getPass() * 0.40) + (user.getDribbling() * 0.30) + (user.getShoot() * 0.15) + (user.getDefend() * 0.15);
                break;
            case DEFENDER:
                // Сзади нужна мощь и чтение игры
                calculatedOvr = (user.getDefend() * 0.45) + (user.getPhysic() * 0.30) + (user.getPace() * 0.15) + (user.getPass() * 0.10);
                break;
            case GOALKEEPER:
                calculatedOvr = (user.getDefend() * 0.70) + (user.getPhysic() * 0.30);
                break;
            default:
                calculatedOvr = (user.getShoot() + user.getPass() + user.getPace() + user.getDribbling() + user.getDefend() + user.getPhysic()) / 6.0;
        }

        return applyBounds((int) Math.round(calculatedOvr));
    }

    public void applyVotingBonuses(UserEntity user, int mvpVotes, int fastestVotes) {
        if (mvpVotes == 0 && fastestVotes == 0) return; // Если голосов нет, ничего не делаем

        // +1 PHY за каждый голос MVP, +2 PAC за каждый голос Fastest
        user.setPhysic(applyBounds(user.getPhysic() + mvpVotes));
        user.setPace(applyBounds(user.getPace() + (fastestVotes * 2)));

        // Обязательно пересчитываем общий OVR!
        user.setOvr(calculateOvr(user));
    }

    // Утилита для удержания рейтинга в рамках [1 ... 99]
    private int applyBounds(int value) {
        return Math.max(MIN_RATING, Math.min(MAX_RATING, value));
    }
}