package com.football.backend.controller;

import com.football.backend.dto.PermanentBanRequest;
import com.football.backend.service.UserDisciplineService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminUserController {
    private final UserDisciplineService disciplineService;

    public AdminUserController(UserDisciplineService disciplineService) {
        this.disciplineService = disciplineService;
    }

    @PostMapping("/{userId}/permanent-ban")
    public ResponseEntity<String> permanentlyBan(@PathVariable Long userId,
                                                  @Valid @RequestBody PermanentBanRequest request) {
        disciplineService.permanentlyBan(userId, request.reason());
        return ResponseEntity.ok("Пользователь заблокирован навсегда");
    }
}
