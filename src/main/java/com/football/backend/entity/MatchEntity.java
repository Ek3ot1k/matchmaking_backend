package com.football.backend.entity;

import com.football.backend.model.MatchStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "matches")
@Getter
@Setter
public class MatchEntity {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "date",nullable = false)
    private LocalDateTime dateTime;

    @Column(name = "location",nullable = false)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "status",nullable = false)
    private MatchStatus status;

    @OneToMany(mappedBy = "match")
    private List<PlayerStatsEntity> playerStats;

    @OneToMany(mappedBy = "match")
    private List<MatchParticipantEntity> matchParticipants;
}
