package com.football.backend.service;

import com.football.backend.dto.PlayerMatchHistoryDTO;
import com.football.backend.dto.PublicPlayerProfileResponse;
import com.football.backend.dto.UpdateProfileRequest;
import com.football.backend.entity.PlayerStatsEntity;
import com.football.backend.entity.UserEntity;
import com.football.backend.exceptions.ResourceNotFoundException;
import com.football.backend.repository.PlayerStatsRepository;
import com.football.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PlayerStatsRepository playerStatsRepository;

    public UserService(UserRepository userRepository, PlayerStatsRepository playerStatsRepository) {
        this.userRepository = userRepository;
        this.playerStatsRepository = playerStatsRepository;
    }

    public UserEntity findByUsername(String username){
        return userRepository.findByUsername(username)
                .orElseThrow(()->new ResourceNotFoundException("Пользователь "+username+" не найден"));
    }

    public UserEntity findById(Long id){
        return userRepository.findById(id)
                .orElseThrow(()->new ResourceNotFoundException("Пользователь не найден"));
    }

    @Transactional
    public UserEntity updateProfile(Long userId, UpdateProfileRequest request){
        UserEntity user=userRepository.findById(userId)
                .orElseThrow(()->new EntityNotFoundException("Пользователь не найден"));
        if(request.username()!=null && !request.username().isBlank()){
            user.setUsername(request.username().trim());
        }
        if(request.position()!=null){
            user.setPosition(request.position());
        }
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public PublicPlayerProfileResponse getPubicProfile(Long userId){
        UserEntity user=userRepository.findById(userId)
                .orElseThrow(()->new EntityNotFoundException("Пользователь не найден"));
        List<PlayerStatsEntity> stats = playerStatsRepository.findRecentMatchesByUserId(userId);

        List<PlayerMatchHistoryDTO> matchHistory=stats.stream()
                .map(stat->new PlayerMatchHistoryDTO(
                        stat.getMatch().getId(),
                        stat.getMatch().getDateTime(),
                        stat.getMatch().getLocation(),
                        stat.getGoals(),
                        stat.getAssists(),
                        stat.getMvpVotes()
                )).toList();
        return new PublicPlayerProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getPosition(),
                user.getOvr(), user.getPace(), user.getShoot(),
                user.getPass(), user.getDribbling(), user.getDefend(), user.getPhysic(),
                user.isVip(),
                matchHistory
        );
    }
}
