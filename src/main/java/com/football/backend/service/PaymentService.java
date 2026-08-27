package com.football.backend.service;

import com.football.backend.entity.TransactionEntity;
import com.football.backend.entity.UserEntity;
import com.football.backend.model.TransactionStatus;
import com.football.backend.repository.TransactionRepository;
import com.football.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.meta.api.methods.invoices.CreateInvoiceLink;
import org.telegram.telegrambots.meta.api.objects.payments.LabeledPrice;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.springframework.context.annotation.Lazy;

import java.time.LocalDateTime;

@Service
public class PaymentService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final DefaultAbsSender telegramBot;

    public PaymentService(UserRepository userRepository,
                          TransactionRepository transactionRepository,
                          @Lazy DefaultAbsSender telegramBot) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.telegramBot = telegramBot;
    }

    /**
     * Шаг 1: Генерация инвойса для Mini App.
     * Возвращает URL, который фронтенд открывает у юзера.
     */
    public String generateVipInvoiceLink(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("Пользователь не найден");
        }

        CreateInvoiceLink createInvoiceLink = CreateInvoiceLink.builder()
                .title("VIP Статус на 30 дней")
                .description("Золотая FUT-карточка, VIP-корона, расширенная статистика и оформление на 30 дней")
                .payload("VIP_30_DAYS_" + userId) // Важный параметр! По нему мы поймем, за что заплатили
                .providerToken("")
                .currency("XTR") // XTR = Telegram Stars
                .price(new LabeledPrice("VIP 30 Дней", 100)) // 100 звезд
                .build();

        try {
            return telegramBot.execute(createInvoiceLink);
        } catch (TelegramApiException e) {
            throw new RuntimeException("Ошибка при генерации ссылки на оплату", e);
        }
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
