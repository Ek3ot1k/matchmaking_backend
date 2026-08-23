package com.football.backend.controller;

import com.football.backend.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // Эндпоинт для Mini App: сгенерировать ссылку на оплату VIP (100 Telegram Stars)
    @PostMapping("/vip-link")
    public ResponseEntity<String> getVipInvoiceLink(@RequestParam Long userId) {
        String invoiceLink = paymentService.generateVipInvoiceLink(userId);
        return ResponseEntity.ok(invoiceLink);
    }


}