package com.football.backend.entity;

import jakarta.persistence.*;
import lombok.*;

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

    @Column(name = "position",nullable = false)
    private String position;

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
}
