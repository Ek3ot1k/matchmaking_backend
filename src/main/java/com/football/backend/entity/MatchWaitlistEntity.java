package com.football.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "match_waitlist",uniqueConstraints = {
        @UniqueConstraint(columnNames = {"match_id","user_id"})
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor

public class MatchWaitlistEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id",nullable = false)
    private MatchEntity match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id",nullable = false)
    private UserEntity user;

    @Column(name = "joined_at",nullable = false)
    @Builder.Default
    private LocalDateTime joinedAt=LocalDateTime.now();
}
