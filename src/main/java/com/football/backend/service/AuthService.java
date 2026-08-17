package com.football.backend.service;

import com.football.backend.entity.UserEntity;
import com.football.backend.model.Position;
import com.football.backend.model.Role;
import com.football.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AuthService {
    private final UserRepository userRepository;

    @Autowired
    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserEntity authenticateOrRegister(Long telegramId,
                                             String username){
        Optional<UserEntity> existingUser=
                userRepository.findByTelegramId(telegramId);

        if (existingUser.isPresent()) {
            // Если юзер уже есть в базе — возвращаем его (Логин)
            return existingUser.get();
        }

        UserEntity newUser=UserEntity.builder()
                .telegramId(telegramId)
                .username(username)
                .position(Position.UNKNOWN)
                .role(Role.USER)
                .build();

        return userRepository.save(newUser);
    }
}
