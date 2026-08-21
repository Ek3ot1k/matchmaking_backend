package com.football.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;

@Entity
@Table(name = "player_stats", uniqueConstraints = {
        @UniqueConstraint(name = "uq_player_match_stats", columnNames = {"user_id", "match_id"})
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PlayerStatsEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id",nullable = false)
    private MatchEntity match;

    @Builder.Default
    @Min(0)
    @Column(name = "goals",nullable = false)
    private Integer goals=0;

    @Builder.Default
    @Min(0)
    @Column(name = "assists",nullable = false)
    private Integer assists=0;

    @Builder.Default
    @Min(0)
    @Column(name = "mvp_votes",nullable = false)
    private Integer mvpVotes=0;

    @Builder.Default
    @Min(0)
    @Column(name = "fastest_player_votes",nullable = false)
    private Integer fastestPlayerVotes=0;

    @Column(name = "match_rating")
    private Double matchRating;
}
