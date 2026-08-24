package com.football.backend.service;

import com.football.backend.entity.MatchEntity;
import com.football.backend.entity.MatchParticipantEntity;
import com.football.backend.entity.NoShowWarningEntity;
import com.football.backend.entity.UserEntity;
import com.football.backend.repository.NoShowWarningRepository;
import com.football.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDisciplineServiceTest {

    @Mock private NoShowWarningRepository warningRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private UserDisciplineService service;

    @BeforeEach
    void setUp() {
        service = new UserDisciplineService(warningRepository, userRepository, notificationService);
    }

    @Test
    void thirdNoShowWithinThirtyDaysBansOfficialMatchesForSevenDays() {
        UserEntity player = UserEntity.builder().id(10L).telegramId(100L).build();
        UserEntity organizer = UserEntity.builder().id(11L).telegramId(101L).build();
        MatchEntity match = MatchEntity.builder().id(20L).build();
        MatchParticipantEntity participant = MatchParticipantEntity.builder()
                .match(match)
                .user(player)
                .build();
        LocalDateTime before = LocalDateTime.now();

        when(warningRepository.existsByMatchIdAndUserId(20L, 10L)).thenReturn(false);
        when(warningRepository.countByUserIdAndIssuedAtGreaterThanEqual(any(), any())).thenReturn(3L);

        long warnings = service.registerNoShow(match, participant, organizer);

        assertThat(warnings).isEqualTo(3);
        assertThat(player.getBannedUntil())
                .isAfterOrEqualTo(before.plusDays(7))
                .isBeforeOrEqualTo(LocalDateTime.now().plusDays(7));
        assertThat(player.getBanReason()).contains("Три неявки");
        assertThat(player.getLastNoShowBanAt()).isNotNull();
        verify(warningRepository).saveAndFlush(any(NoShowWarningEntity.class));
        verify(userRepository).save(player);
        verify(notificationService).sendToUser(100L,
                "⛔ За три неявки за 30 дней вы заблокированы в официальных матчах до "
                        + player.getBannedUntil().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                        + ". Неофициальные игры во время блокировки не попадают в статистику.");
    }

    @Test
    void permanentManipulationBanHasNoExpiryAndCannotBeBypassed() {
        UserEntity player = UserEntity.builder()
                .id(10L)
                .telegramId(100L)
                .bannedUntil(LocalDateTime.now().plusDays(2))
                .build();
        when(userRepository.findById(10L)).thenReturn(Optional.of(player));

        service.permanentlyBan(10L, "Накрутка статистики");

        assertThat(player.isPermanentlyBanned()).isTrue();
        assertThat(player.getBannedUntil()).isNull();
        assertThat(player.getBanReason()).isEqualTo("Накрутка статистики");
        assertThatThrownBy(() -> service.assertCanParticipate(player))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("заблокирован навсегда");
        verify(userRepository).save(player);
        verify(notificationService).sendToUser(100L,
                "⛔ За попытку накрутки статистики вы навсегда заблокированы в официальных матчах. "
                        + "Разблокировка не предусмотрена. Причина: Накрутка статистики");
    }
}
