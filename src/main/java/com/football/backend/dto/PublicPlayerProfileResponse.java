package com.football.backend.dto;

import com.football.backend.model.Position;
import java.util.List;

public record PublicPlayerProfileResponse(
        Long id,
        String username,
        Position position,

        // Характеристики (как в EA FC)
        Integer ovr,
        Integer pac,
        Integer sho,
        Integer pas,
        Integer dri,
        Integer def,
        Integer phy,

        Boolean isVip,

        // История последних матчей
        List<PlayerMatchHistoryDTO> recentMatches
) {}