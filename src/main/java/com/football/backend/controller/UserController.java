package com.football.backend.controller;

import com.football.backend.dto.UserDTO;
import com.football.backend.entity.UserEntity;
import com.football.backend.security.UserEntityDetails;
import com.football.backend.service.UserService;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserService userService;
    private final ModelMapper modelMapper;

    public UserController(UserService userService, ModelMapper modelMapper) {
        this.userService = userService;
        this.modelMapper = modelMapper;
    }

    @GetMapping("/{id}")
    public UserDTO getUser(@PathVariable("id") Long id){
        return convertToUserDTO(userService.findById(id));
    }

    @GetMapping("/me")
    public UserDTO getMyProfile(@AuthenticationPrincipal UserEntityDetails userEntityDetails){
        return convertToUserDTO(userService.findById(userEntityDetails.getUserId()));
    }

    private UserDTO convertToUserDTO(UserEntity user){
        return modelMapper.map(user, UserDTO.class);
    }
}
