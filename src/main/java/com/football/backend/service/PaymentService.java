package com.football.backend.service;

import com.football.backend.entity.TransactionEntity;
import com.football.backend.entity.UserEntity;
import com.football.backend.model.TransactionStatus;
import com.football.backend.repository.TransactionRepository;
import com.football.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class PaymentService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final TelegramStarsClient telegramStarsClient;

    public PaymentService(UserRepository userRepository,
                          TransactionRepository transactionRepository,
                          TelegramStarsClient telegramStarsClient) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.telegramStarsClient = telegramStarsClient;
    }

    /**
     * Шаг 1: Генерация инвойса для Mini App.
     * Возвращает URL, который фронтенд открывает у юзера.
     */
    public String generateVipInvoiceLink(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("Пользователь не найден");
        }

        return telegramStarsClient.createVipInvoice(userId);
    }

    /**
     * Шаг 2: Обработка успешного платежа от вебхука.
     */
    @Transactional
    public void processSuccessfulPayment(Long telegramUserId,
                                         String chargeId,
                                         Integer totalAmount,
                                         String currency,
                                         String payload) {


        if (transactionRepository.existsByTelegramChargeId(chargeId)) {
            return; // Просто выходим, платеж уже зачислен (спасибо Telegram за дублирование)
        }

        UserEntity user = userRepository.findByTelegramId(telegramUserId)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));

        if (!"XTR".equals(currency) || totalAmount == null || totalAmount != 100
                || !("VIP_30_DAYS_" + user.getId()).equals(payload)) {
            throw new IllegalArgumentException("Некорректные параметры VIP-платежа");
        }

        TransactionEntity transaction = TransactionEntity.builder()
                .user(user)
                .telegramChargeId(chargeId)
                .amount(totalAmount)
                .currency(currency)
                .status(TransactionStatus.SUCCESS)
                .build();
        transactionRepository.save(transaction);

        if (payload.startsWith("VIP_30_DAYS")) {
            LocalDateTime currentVip = user.getVipUntil() != null && user.getVipUntil().isAfter(LocalDateTime.now())
                    ? user.getVipUntil()
                    : LocalDateTime.now();

            // Продлеваем с текущего момента окончания на 30 дней
            user.setVipUntil(currentVip.plusDays(30));
            user.setVip(true);
            userRepository.save(user);
        }
    }

    @Transactional
    public void processRefund(String chargeId) {
        TransactionEntity transaction = transactionRepository.findByTelegramChargeId(chargeId)
                .orElseThrow(() -> new EntityNotFoundException("Транзакция с таким chargeId не найдена"));

        if (transaction.getStatus() == TransactionStatus.FAILED ||
                transaction.getStatus() == TransactionStatus.REFUNDED) {
            return;
        }

        transaction.setStatus(TransactionStatus.REFUNDED);
        transactionRepository.save(transaction);

        UserEntity user = transaction.getUser();

        user.setVipUntil(LocalDateTime.now().minusSeconds(1));
        user.setVip(false);

        userRepository.save(user);
    }
}
