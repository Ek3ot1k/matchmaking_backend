package com.football.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "rating_history")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RatingHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id", nullable = false)
    private MatchEntity match;

    @Builder.Default
    @Column(name = "ovr_change", nullable = false)
    private Integer ovrChange = 0;

    @Builder.Default
    @Column(name = "pac_change", nullable = false)
    private Integer pacChange = 0;

    @Builder.Default
    @Column(name = "sho_change", nullable = false)
    private Integer shoChange = 0;

    @Builder.Default
    @Column(name = "pas_change", nullable = false)
    private Integer pasChange = 0;

    @Builder.Default
    @Column(name = "dri_change", nullable = false)
    private Integer driChange = 0;

    @Builder.Default
    @Column(name = "def_change", nullable = false)
    private Integer defChange = 0;

    @Builder.Default
    @Column(name = "phy_change", nullable = false)
    private Integer phyChange = 0;

    @Column(name = "old_ovr", nullable = false)
    private Integer oldOvr;

    @Column(name = "new_ovr", nullable = false)
    private Integer newOvr;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}