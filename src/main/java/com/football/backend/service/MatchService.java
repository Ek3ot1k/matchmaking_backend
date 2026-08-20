package com.football.backend.service;

import com.football.backend.dto.CreateMatchRequest;
import com.football.backend.dto.MatchDTO;
import com.football.backend.dto.MatchFilterRequest;
import com.football.backend.entity.MatchEntity;
import com.football.backend.entity.MatchParticipantEntity;
import com.football.backend.entity.UserEntity;
import com.football.backend.model.MatchStatus;
import com.football.backend.model.TeamColor;
import com.football.backend.repository.MatchParticipantRepository;
import com.football.backend.repository.MatchRepository;
import com.football.backend.repository.UserRepository;
import com.football.backend.repository.specification.MatchSpecifications;
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MatchService {
    private final MatchRepository matchRepository;
    private final ModelMapper modelMapper;
    private final UserRepository userRepository;
    private final MatchParticipantRepository matchParticipantRepository;

    public MatchService(MatchRepository matchRepository,
                        ModelMapper modelMapper,
                        UserRepository userRepository,
                        MatchParticipantRepository matchParticipantRepository) {
        this.matchRepository = matchRepository;
        this.modelMapper = modelMapper;
        this.userRepository = userRepository;
        this.matchParticipantRepository = matchParticipantRepository;
    }

    public void cancelMatch(Long matchId, Long currentUserId){
        MatchEntity match=matchRepository.findById(matchId)
                .orElseThrow(()->new EntityNotFoundException("Матч не найден"));
        if(!match.getOrganizer().getId().equals(currentUserId)){
            throw new AccessDeniedException("Вы не можете отменить чужой матч!");
        }
        match.setStatus(MatchStatus.CANCELLED);
        matchRepository.save(match);
    }

    @Transactional(readOnly = true)
    public Page<MatchDTO> getMatches(MatchFilterRequest filter, Pageable pageable){
        Specification<MatchEntity> spec=MatchSpecifications.withFilters(filter);
        Page<MatchEntity> matchPage=matchRepository.findAll(spec,pageable);
        return matchPage.map(match -> modelMapper.map(match, MatchDTO.class));
    }

    public MatchDTO createMatch(Long organizerId, CreateMatchRequest request){
        UserEntity organizer=userRepository.findById(organizerId)
                .orElseThrow(()->new EntityNotFoundException("Организатор не найден"));
        MatchEntity newMatch=MatchEntity.builder()
                .organizer(organizer)
                .format(request.format())
                .location(request.location())
                .dateTime(request.dateTime())
                .maxPlayers(request.maxPlayers())
                .currentPlayers(1) // Организатор сразу занимает 1 место
                .status(MatchStatus.OPEN) // Дефолтный статус для новых матчей
                .build();
        MatchParticipantEntity participant=MatchParticipantEntity.builder()
                .match(newMatch)
                .user(organizer)
                .teamColor(TeamColor.NONE)
                .build();
        matchParticipantRepository.save(participant);
        MatchEntity savedMatch=matchRepository.save(newMatch);
        return modelMapper.map(savedMatch, MatchDTO.class);
    }
}
