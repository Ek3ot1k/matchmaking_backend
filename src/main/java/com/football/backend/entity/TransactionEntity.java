package com.football.backend.entity;

import com.football.backend.model.TransactionStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "telegram_payment_charge_id", nullable = false, unique = true)
    private String telegramPaymentChargeId;

    @Column(name = "provider_payment_charge_id", nullable = false, unique = true)
    private String providerPaymentChargeId;

    @Column(name = "product_payload", nullable = false)
    private String productPayload;

    @Min(1)
    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Builder.Default
    @Column(name = "currency", nullable = false)
    private String currency = "RUB";

    // Лучше создать отдельный Enum TransactionStatus (PENDING, SUCCESS, FAILED)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false)
    private TransactionStatus status = TransactionStatus.PENDING;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}