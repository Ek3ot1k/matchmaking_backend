package com.football.backend.service;

import com.football.backend.dto.PlayerMatchHistoryDTO;
import com.football.backend.dto.PublicPlayerProfileResponse;
import com.football.backend.dto.UpdateProfileRequest;
import com.football.backend.dto.UserProfileDTO;
import com.football.backend.entity.PlayerStatsEntity;
import com.football.backend.entity.MatchParticipantEntity;
import com.football.backend.entity.UserEntity;
import com.football.backend.exceptions.ResourceNotFoundException;
import com.football.backend.model.Position;
import com.football.backend.repository.PlayerStatsRepository;
import com.football.backend.repository.UserRepository;
import com.football.backend.repository.MatchParticipantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PlayerStatsRepository playerStatsRepository;
    private final UserDisciplineService disciplineService;
    private final MatchParticipantRepository participantRepository;

    public UserService(UserRepository userRepository,
                       PlayerStatsRepository playerStatsRepository,
                       UserDisciplineService disciplineService,
                       MatchParticipantRepository participantRepository) {
        this.userRepository = userRepository;
        this.playerStatsRepository = playerStatsRepository;
        this.disciplineService = disciplineService;
        this.participantRepository = participantRepository;
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

    public UserProfileDTO getUserProfile(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));

        // Достаем агрегированную стату из playerStatsRepository
        Object[] rawStats = (Object[]) playerStatsRepository.getAggregatedStatsByUserId(userId)[0];

        // Безопасный парсинг данных (база может вернуть null, если игрок еще не играл)
        int totalMatches = rawStats[0] != null ? ((Number) rawStats[0]).intValue() : 0;
        int totalGoals = rawStats[1] != null ? ((Number) rawStats[1]).intValue() : 0;
        int totalAssists = rawStats[2] != null ? ((Number) rawStats[2]).intValue() : 0;
        int totalMvp = rawStats[3] != null ? ((Number) rawStats[3]).intValue() : 0;

        // Средний рейтинг округляем до 1 знака (например, 7.4)
        double avgRating = rawStats[4] != null ? Math.round(((Number) rawStats[4]).doubleValue() * 10.0) / 10.0 : 0.0;

        List<PlayerMatchHistoryDTO> recentMatches = playerStatsRepository.findRecentMatchesByUserId(userId)
                .stream()
                .limit(10)
                .map(stat -> new PlayerMatchHistoryDTO(
                        stat.getMatch().getId(),
                        stat.getMatch().getDateTime(),
                        stat.getMatch().getLocation(),
                        stat.getGoals(),
                        stat.getAssists(),
                        stat.getMvpVotes()
                ))
                .toList();

        List<MatchParticipantEntity> completedParticipations = participantRepository
                .findCompletedByUserIdOrderByMatchDateDesc(userId);
        Map<Position, Long> positionCounts = completedParticipations.stream()
                .filter(p -> p.getPosition() != null && p.getPosition() != Position.UNKNOWN)
                .collect(java.util.stream.Collectors.groupingBy(MatchParticipantEntity::getPosition,
                        () -> new EnumMap<>(Position.class), java.util.stream.Collectors.counting()));
        Position mainPosition = positionCounts.entrySet().stream()
                .max(Comparator.<Map.Entry<Position, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(entry -> lastPlayedRank(completedParticipations, entry.getKey())))
                .map(Map.Entry::getKey)
                .orElse(Position.UNKNOWN);
        int positionedMatches = positionCounts.values().stream().mapToInt(Long::intValue).sum();
        Map<String, Integer> positionPercentages = new LinkedHashMap<>();
        positionCounts.forEach((position, count) -> positionPercentages.put(position.name(),
                (int) Math.round(count * 100.0 / Math.max(1, positionedMatches))));

        return new UserProfileDTO(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getAvatarUrl(),
                mainPosition.name(),
                user.getRole(),
                user.isVip(),
                user.getVipUntil(),

                user.getOvr(),
                user.getPace(),
                user.getShoot(),
                user.getPass(),
                user.getDribbling(),
                user.getDefend(),
                user.getPhysic(),

                totalMatches,
                totalGoals,
                totalAssists,
                totalMvp,
                avgRating,
                user.isOfficiallyBanned(),
                user.isPermanentlyBanned(),
                user.getBannedUntil(),
                user.getBanReason(),
                disciplineService.warningsInLast30Days(userId),
                positionPercentages,
                recentMatches
        );
    }

    private int lastPlayedRank(List<MatchParticipantEntity> completedParticipations, Position position) {
        for (int i = 0; i < completedParticipations.size(); i++) {
            if (completedParticipations.get(i).getPosition() == position) return completedParticipations.size() - i;
        }
        return 0;
    }

    @Transactional
    public void registerOrUpdateTelegramUser(Long telegramUserId, String firstName) {
        Optional<UserEntity> existingUser = userRepository.findByTelegramId(telegramUserId);

        if (existingUser.isEmpty()) {
            UserEntity newUser = UserEntity.builder()
                    .telegramId(telegramUserId)
                    .firstName(firstName)
                    .position(Position.UNKNOWN)
                    .build();

            userRepository.save(newUser);
            System.out.println("Новый игрок добавлен в базу: " + firstName);
        } else if (firstName != null && !firstName.equals(existingUser.get().getFirstName())) {
            existingUser.get().setFirstName(firstName);
            userRepository.save(existingUser.get());
        }
    }
}
