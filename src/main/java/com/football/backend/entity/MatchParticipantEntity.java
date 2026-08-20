package com.football.backend.entity;

import com.football.backend.model.ParticipantStatus;
import com.football.backend.model.TeamColor;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "match_participants",uniqueConstraints = {
        @UniqueConstraint(columnNames = {"match_id","user_id"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchParticipantEntity {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "team_color",nullable = false)
    private TeamColor teamColor=TeamColor.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id",nullable = false)
    private MatchEntity match;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ParticipantStatus status = ParticipantStatus.REGISTERED;
}
