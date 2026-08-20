package com.football.backend.model;

public enum MatchStatus {
    DRAFT,      // Черновик (видит только организатор)
    OPEN,       // Опубликован (идет набор)
    CANCELLED,  // Отменен организатором
    COMPLETED   // Матч завершен
}