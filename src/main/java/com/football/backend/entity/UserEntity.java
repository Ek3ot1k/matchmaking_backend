package com.football.backend.entity;

import com.football.backend.model.Position;
import com.football.backend.model.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserEntity {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.NONE)
    private Long id;

    @Column(name = "telegram_id",unique = true,nullable = false)
    private Long telegramId;

    @Column(name = "username")
    private String username;

    @Builder.Default
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Role role=Role.USER;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "position",nullable = false)
    private Position position=Position.MIDFIELDER;

    @OneToMany(mappedBy = "user")
    private List<PlayerStatsEntity> stats;

    @OneToMany(mappedBy = "user")
    private List<MatchParticipantEntity> matchParticipants;

    @OneToMany(mappedBy = "user")
    private List<TransactionEntity> transactions;

    @Builder.Default
    @Column(name = "ovr")
    private Integer ovr=65;

    @Builder.Default
    @Column(name = "pac")
    private Integer pace=65;

    @Builder.Default
    @Column(name = "sho")
    private Integer shoot=65;

    @Builder.Default
    @Column(name = "pas")
    private Integer pass=65;

    @Builder.Default
    @Column(name = "dri")
    private Integer dribbling=65;

    @Builder.Default
    @Column(name = "def")
    private Integer defend=65;

    @Builder.Default
    @Column(name = "phy")
    private Integer physic=65;

    @Column(name = "is_vip")
    private boolean isVip;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "avatar_url", length = 1000)
    private String avatarUrl;

    @OneToMany(mappedBy = "user")
    private List<RatingHistoryEntity> ratingHistory;

    @Column(name = "vip_until")
    private LocalDateTime vipUntil;

    @Builder.Default
    @ColumnDefault("false")
    @Column(name = "permanently_banned", nullable = false)
    private boolean permanentlyBanned = false;

    @Column(name = "banned_until")
    private LocalDateTime bannedUntil;

    @Column(name = "ban_reason", length = 1000)
    private String banReason;

    @Column(name = "last_no_show_ban_at")
    private LocalDateTime lastNoShowBanAt;

    public boolean isVip() {
        return vipUntil != null && vipUntil.isAfter(LocalDateTime.now());
    }

    public boolean isOfficiallyBanned() {
        return permanentlyBanned || (bannedUntil != null && bannedUntil.isAfter(LocalDateTime.now()));
    }
}
