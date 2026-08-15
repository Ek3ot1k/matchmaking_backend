package com.football.backend.util;

import com.football.backend.entity.UserEntity;
import com.football.backend.repository.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
public class UserValidator implements Validator {

    private final UserRepository userRepository;

    public UserValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean supports(Class<?> clazz) {
        return UserEntity.class.equals(clazz);
    }

    @Override
    public void validate(Object target, Errors errors) {
        UserEntity user = (UserEntity) target;

        // Ищем пользователя в базе по email
        // Предполагается, что в репозитории есть метод findByEmail
        if (userRepository.findByUsername(user.getUsername()).isPresent()) {
            // Если нашли, значит такой email уже занят, выдаем ошибку
            errors.rejectValue("username", "", "Человек с таким username уже существует");
        }
    }
}
