package com.football.backend.model;

public enum MatchStatus {
    DRAFT,      // Черновик (видит только организатор)
    OPEN,       // Опубликован (идет набор)
    IN_PROGRESS, // Матч идет прямо сейчас (составы заблокированы)
    CANCELLED,  // Отменен организатором
    COMPLETED   // Матч завершен
}