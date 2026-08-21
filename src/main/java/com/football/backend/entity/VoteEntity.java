package com.football.backend.entity;

import com.football.backend.model.VoteCategory;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name="match_votes",uniqueConstraints = {
        @UniqueConstraint(name = "uq_match_voter_category",columnNames = {"match_id","voter_id","category"})
})
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VoteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Setter(AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id",nullable = false)
    private MatchEntity match;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voter_id",nullable = false)
    private UserEntity voter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_id",nullable = false)
    private UserEntity target;

    @Column(name = "category",nullable = false)
    @Enumerated(EnumType.STRING)
    private VoteCategory category;

    @Column(name = "created_at",nullable = false)
    @Builder.Default
    private LocalDateTime createdAt=LocalDateTime.now();
}
