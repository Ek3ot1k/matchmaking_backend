package com.football.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record FinishMatchRequest(
        @NotNull(message = "Счет белых не может быть пустым")
        @Min(0)
        Integer scoreWhite,

        @NotNull(message = "Счет темных не может быть пустым")
        @Min(0)
        Integer scoreDark,

        // @Valid заставит Spring проверить все аннотации @Min и @NotNull внутри каждого PlayerMatchStatDTO
        @Valid
        List<PlayerMatchStatDTO> playersStats
) {}