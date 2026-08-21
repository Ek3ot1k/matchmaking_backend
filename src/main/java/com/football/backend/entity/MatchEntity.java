package com.football.backend.entity;

import com.football.backend.model.MatchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "matches")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organizer_id")
    private UserEntity organizer;

    @Builder.Default
    @Column(name = "max_players",nullable = false)
    private Integer maxPlayers=10;

    @Builder.Default
    @Version
    @Column(name = "version",nullable = false)
    private Long version=0L;

    @Builder.Default
    @Column(name = "min_players",nullable = false)
    private Integer minPlayers=8;

    @Builder.Default
    @Column(name = "duration",nullable = false)
    private Integer duration=60;

    @Column(name = "description")
    private String description;

    @Builder.Default
    @CreationTimestamp
    @Column(name = "created_at",updatable = false)
    private LocalDateTime createdAt=LocalDateTime.now();

    @Builder.Default
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt=LocalDateTime.now();

    @Formula("(SELECT count(*) FROM match_participants mp WHERE mp.match_id = id)")
    private Integer currentPlayers;

    @OneToMany(mappedBy = "match")
    private List<RatingHistoryEntity> ratingHistory;

    @Column(name = "format", length = 10)
    private String format; // Например: "5x5", "8x8", "11x11"

    @Column(name = "score_white")
    private Integer scoreWhite;

    @Column(name = "score_dark")
    private Integer scoreDark;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "updated_by_organizer_id")
    private Long updatedByOrganizerId;

    @Builder.Default
    @Column(name = "voting_closed", nullable = false)
    private Boolean votingClosed = false;
}