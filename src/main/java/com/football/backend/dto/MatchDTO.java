package com.football.backend.dto;

import com.football.backend.model.MatchStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MatchDTO {
    private Long id;

    private String format;          // Формат: "5×5", "6×6" или "7×7"
    private String location;        // Адрес поля/манежа
    private Double latitude;
    private Double longitude;
    private LocalDateTime dateTime; // Дата и время проведения игры
    private Integer duration;       // Фиксированная длительность: 15 минут

    private Integer currentPlayers; // Сколько человек уже в составе
    private Integer maxPlayers;     // Максимальное количество игроков (например, 10)

    private MatchStatus status;     // Статус: OPEN, COMPLETED, CANCELLED

    private Integer scoreWhite;
    private Integer scoreDark;

    private LocalDateTime resultVotingEndsAt;
    private Integer resultEligibleVoters;
    private Integer resultConfirmationsRequired;
    private String chatLink;

    // Организатор матча
    private Long organizerId;
    private UserDTO organizer;
}
