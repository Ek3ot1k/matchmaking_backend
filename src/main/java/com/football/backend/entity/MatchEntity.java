package com.football.backend.entity;

import com.football.backend.model.MatchStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity
@Table(name = "matches")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MatchEntity {
    /**
     * Дворовые матчи в приложении всегда идут 15 минут. Одно значение
     * используется при создании, проверке пересечения площадок и протоколе.
     */
    public static final int MATCH_DURATION_MINUTES = 15;
    public static final int MAX_DURATION_MINUTES = MATCH_DURATION_MINUTES;

    private static final Pattern SUPPORTED_FORMAT =
            Pattern.compile("^\\s*([567])\\s*[xXхХ×]\\s*\\1\\s*$");

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "date",nullable = false)
    private LocalDateTime dateTime;

    @Column(name = "location",nullable = false)
    private String location;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

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
    private Integer minPlayers=10;

    @Builder.Default
    @Column(name = "duration",nullable = false)
    private Integer duration=MATCH_DURATION_MINUTES;

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
    private String format; // Допустимы только: "5×5", "6×6", "7×7"

    @Column(name = "score_white")
    private Integer scoreWhite;

    @Column(name = "score_dark")
    private Integer scoreDark;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "updated_by_organizer_id")
    private Long updatedByOrganizerId;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "creation_request_id", unique = true, length = 100)
    private String creationRequestId;

    @Column(name = "white_formation", length = 20)
    private String whiteFormation;

    @Column(name = "dark_formation", length = 20)
    private String darkFormation;

    /** Необязательная ссылка-приглашение в чат конкретного матча. */
    @Column(name = "chat_link", length = 500)
    private String chatLink;

    @Column(name = "result_voting_started_at")
    private LocalDateTime resultVotingStartedAt;

    @Column(name = "result_voting_ends_at")
    private LocalDateTime resultVotingEndsAt;

    @Column(name = "result_eligible_voters")
    private Integer resultEligibleVoters;

    @Column(name = "result_confirmations_required")
    private Integer resultConfirmationsRequired;

    @Builder.Default
    @Column(name = "voting_closed", nullable = false)
    private Boolean votingClosed = false;

    // Флаги отправленных напоминаний (чтобы не спамить повторно)
    @Builder.Default
    private boolean reminder24hSent = false;

    @Builder.Default
    private boolean reminder2hSent = false;

    @Builder.Default
    private boolean reminder30minSent = false;

    @PrePersist
    @PreUpdate
    private void enforceFixedDuration() {
        duration = MATCH_DURATION_MINUTES;
    }

    public static String normalizeSupportedFormat(String rawFormat) {
        Matcher matcher = SUPPORTED_FORMAT.matcher(rawFormat == null ? "" : rawFormat);
        if (!matcher.matches()) {
            throw new IllegalStateException("Доступны только форматы 5×5, 6×6 и 7×7");
        }
        String teamSize = matcher.group(1);
        return teamSize + "×" + teamSize;
    }

    public static int maxPlayersForFormat(String rawFormat) {
        String normalized = normalizeSupportedFormat(rawFormat);
        int teamSize = Integer.parseInt(normalized.substring(0, 1));
        return teamSize * 2;
    }
}
