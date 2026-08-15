package com.football.backend.repository;

import com.football.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity,Long> {
    Optional<UserEntity> findByTelegramId(Long telegramId);
    Optional<UserEntity> findByUsername(String username);
}
