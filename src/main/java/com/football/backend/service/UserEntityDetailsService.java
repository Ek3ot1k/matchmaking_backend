package com.football.backend.service;

import com.football.backend.entity.UserEntity;
import com.football.backend.repository.UserRepository;
import com.football.backend.security.UserEntityDetails;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class UserEntityDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public UserEntityDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<UserEntity> user=userRepository.findByUsername(username);

        if(user.isEmpty()){
            throw new UsernameNotFoundException("Пользователь с таким username не найден");
        }

        return new UserEntityDetails(user.get());
    }

    public UserDetails loadUserById(Long id) throws UsernameNotFoundException {
        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Пользователь с id=" + id + " не найден"
                ));

        return new UserEntityDetails(user);
    }
}
