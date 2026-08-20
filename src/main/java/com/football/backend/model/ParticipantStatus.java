package com.football.backend.model;

public enum ParticipantStatus {
    REGISTERED, // Записался (статус по умолчанию)
    CONFIRMED,  // Организатор подтвердил участие
    NO_SHOW     // Неявка (игрок не пришел на игру)
}