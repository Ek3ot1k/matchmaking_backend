package com.football.backend.controller;

import com.football.backend.dto.PublicPlayerProfileResponse;
import com.football.backend.dto.UpdateProfileRequest;
import com.football.backend.dto.UserDTO;
import com.football.backend.dto.UserProfileDTO;
import com.football.backend.entity.UserEntity;
import com.football.backend.security.UserEntityDetails;
import com.football.backend.service.UserService;
import jakarta.validation.Valid;
import org.modelmapper.ModelMapper;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileDTO> getUserProfile(@PathVariable("id") Long id){
        UserProfileDTO userProfileDTO=userService.getUserProfile(id);
        return ResponseEntity.ok(userProfileDTO);
    }

    @GetMapping("/me")
    public UserDTO getMyProfile(@AuthenticationPrincipal UserEntityDetails userEntityDetails){
        return convertToUserDTO(userService.findById(userEntityDetails.getUserId()));
    }

    @PatchMapping("/me")
    public UserDTO updateMyProfile(@AuthenticationPrincipal UserEntityDetails userDetails,
                                   @Valid @RequestBody UpdateProfileRequest request){
        UserEntity updatedUser=userService.updateProfile(userDetails.getUserId(),request);
        return convertToUserDTO(updatedUser);
    }

    private UserDTO convertToUserDTO(UserEntity user){
        return modelMapper.map(user, UserDTO.class);
    }
}
