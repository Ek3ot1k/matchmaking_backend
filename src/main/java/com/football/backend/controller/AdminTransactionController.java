package com.football.backend.controller;

import com.football.backend.dto.TransactionDTO;
import com.football.backend.repository.TransactionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/transactions")
public class AdminTransactionController {

    private final TransactionRepository transactionRepository;

    public AdminTransactionController(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<TransactionDTO>> getAllTransactions() {
        List<TransactionDTO> transactions = transactionRepository.findAll().stream()
                .map(tx -> new TransactionDTO(
                        tx.getId(),
                        tx.getUser().getId(),
                        tx.getUser().getFirstName(),
                        tx.getUser().getUsername(),
                        tx.getTelegramChargeId(),
                        tx.getAmount(),
                        tx.getCurrency(),
                        tx.getStatus(),
                        tx.getCreatedAt()
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(transactions);
    }
}