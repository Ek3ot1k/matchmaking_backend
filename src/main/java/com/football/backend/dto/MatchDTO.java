package com.football.backend.dto;

import com.football.backend.model.MatchStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MatchDTO {
    private Long id;

    private String format;          // Формат: "5x5", "8x8" и т.д.
    private String location;        // Адрес поля/манежа
    private LocalDateTime dateTime; // Дата и время проведения игры

    private Integer currentPlayers; // Сколько человек уже в составе
    private Integer maxPlayers;     // Максимальное количество игроков (например, 10)

    private MatchStatus status;     // Статус: OPEN, COMPLETED, CANCELLED

    private Integer scoreWhite;
    private Integer scoreDark;

    // Организатор матча
    private UserDTO organizer;
}
