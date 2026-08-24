package com.football.backend.service;

import com.football.backend.entity.MatchEntity;
import com.football.backend.entity.MatchParticipantEntity;
import com.football.backend.entity.NoShowWarningEntity;
import com.football.backend.entity.UserEntity;
import com.football.backend.repository.NoShowWarningRepository;
import com.football.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class UserDisciplineService {
    public static final int WARNING_WINDOW_DAYS = 30;
    public static final int WARNINGS_BEFORE_BAN = 3;
    public static final int TEMPORARY_BAN_DAYS = 7;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final NoShowWarningRepository warningRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public UserDisciplineService(NoShowWarningRepository warningRepository,
                                 UserRepository userRepository,
                                 NotificationService notificationService) {
        this.warningRepository = warningRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public void assertCanParticipate(UserEntity user) {
        if (user.isPermanentlyBanned()) {
            throw new IllegalStateException("Доступ к официальным матчам заблокирован навсегда: "
                    + safeReason(user.getBanReason()));
        }
        if (user.getBannedUntil() != null && user.getBannedUntil().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Доступ к официальным матчам заблокирован до "
                    + user.getBannedUntil().format(DATE_TIME) + ": " + safeReason(user.getBanReason()));
        }
    }

    @Transactional
    public long registerNoShow(MatchEntity match,
                               MatchParticipantEntity participant,
                               UserEntity issuer) {
        Long userId = participant.getUser().getId();
        if (warningRepository.existsByMatchIdAndUserId(match.getId(), userId)) {
            throw new IllegalStateException("Неявка этому игроку за данный матч уже выставлена");
        }

        LocalDateTime now = LocalDateTime.now();
        warningRepository.saveAndFlush(NoShowWarningEntity.builder()
                .match(match)
                .user(participant.getUser())
                .issuedBy(issuer)
                .build());

        LocalDateTime windowStart = now.minusDays(WARNING_WINDOW_DAYS);
        if (participant.getUser().getLastNoShowBanAt() != null
                && participant.getUser().getLastNoShowBanAt().isAfter(windowStart)) {
            windowStart = participant.getUser().getLastNoShowBanAt();
        }
        long warnings = warningRepository.countByUserIdAndIssuedAtGreaterThanEqual(userId, windowStart);

        String message;
        if (warnings >= WARNINGS_BEFORE_BAN) {
            LocalDateTime bannedUntil = now.plusDays(TEMPORARY_BAN_DAYS);
            UserEntity user = participant.getUser();
            user.setBannedUntil(bannedUntil);
            user.setBanReason("Три неявки без предварительной отмены за 30 дней");
            user.setLastNoShowBanAt(now);
            userRepository.save(user);
            message = "⛔ За три неявки за 30 дней вы заблокированы в официальных матчах до "
                    + bannedUntil.format(DATE_TIME)
                    + ". Неофициальные игры во время блокировки не попадают в статистику.";
        } else {
            message = "⚠️ Зафиксирована неявка без предварительной отмены. Предупреждение "
                    + warnings + " из " + WARNINGS_BEFORE_BAN
                    + ". Три предупреждения за 30 дней — блокировка на 7 дней.";
        }
        notificationService.sendToUser(participant.getUser().getTelegramId(), message);
        return warnings;
    }

    @Transactional(readOnly = true)
    public long warningsInLast30Days(Long userId) {
        return warningRepository.countByUserIdAndIssuedAtGreaterThanEqual(
                userId, LocalDateTime.now().minusDays(WARNING_WINDOW_DAYS));
    }

    @Transactional
    public void permanentlyBan(Long userId, String reason) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));
        user.setPermanentlyBanned(true);
        user.setBannedUntil(null);
        user.setBanReason(reason.trim());
        userRepository.save(user);
        notificationService.sendToUser(user.getTelegramId(),
                "⛔ За попытку накрутки статистики вы навсегда заблокированы в официальных матчах. "
                        + "Разблокировка не предусмотрена. Причина: " + reason.trim());
    }

    private String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "нарушение правил приложения" : reason;
    }
}
