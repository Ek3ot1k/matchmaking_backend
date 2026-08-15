package com.football.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player_stats")
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
    @JoinColumn(name = "user_id")
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private MatchEntity match;

    @Builder.Default
    @Column(name = "goals")
    private Integer goals=0;

    @Builder.Default
    @Column(name = "assists")
    private Integer assists=0;

    @Builder.Default
    @Column(name = "mvp_votes")
    private Integer mvpVotes=0;

    @Builder.Default
    @Column(name = "fastest_player_votes")
    private Integer fastestPlayerVotes=0;
}
