package com.football.backend.service;

import com.football.backend.entity.UserEntity;
import com.football.backend.exceptions.ResourceNotFoundException;
import com.football.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserEntity findByUsername(String username){
        return userRepository.findByUsername(username)
                .orElseThrow(()->new ResourceNotFoundException("Пользователь "+username+" не найден"));
    }

    public UserEntity findById(Long id){
        return userRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Пользователь не найден"));
    }
}
