package com.football.backend.service;

import com.football.backend.dto.*;
import com.football.backend.entity.*;
import com.football.backend.model.MatchStatus;
import com.football.backend.model.ParticipantStatus;
import com.football.backend.model.TeamColor;
import com.football.backend.repository.*;
import com.football.backend.repository.specification.MatchSpecifications;
import com.football.backend.util.MatchRatingCalculator;
import com.football.backend.util.PlayerSkillCalculator;
import jakarta.persistence.EntityNotFoundException;
import org.modelmapper.ModelMapper;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Transactional
public class MatchService {
    private final MatchRepository matchRepository;
    private final ModelMapper modelMapper;
    private final UserRepository userRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final MatchWaitlistRepository matchWaitlistRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TeamBalancerService teamBalancerService;
    private final MatchRatingCalculator ratingCalculator;
    private final PlayerStatsRepository playerStatsRepository;
    private final PlayerSkillCalculator playerSkillCalculator;

    public MatchService(MatchRepository matchRepository,
                        ModelMapper modelMapper,
                        UserRepository userRepository,
                        MatchParticipantRepository matchParticipantRepository,
                        MatchWaitlistRepository matchWaitlistRepository,
                        ApplicationEventPublisher eventPublisher,
                        TeamBalancerService teamBalancerService,
                        MatchRatingCalculator ratingCalculator,
                        PlayerStatsRepository playerStatsRepository,
                        PlayerSkillCalculator playerSkillCalculator) {
        this.matchRepository = matchRepository;
        this.modelMapper = modelMapper;
        this.userRepository = userRepository;
        this.matchParticipantRepository = matchParticipantRepository;
        this.matchWaitlistRepository = matchWaitlistRepository;
        this.eventPublisher = eventPublisher;
        this.teamBalancerService = teamBalancerService;
        this.ratingCalculator = ratingCalculator;
        this.playerStatsRepository = playerStatsRepository;
        this.playerSkillCalculator = playerSkillCalculator;
    }

    @Transactional(readOnly = true)
    public Page<MatchDTO> getMatches(MatchFilterRequest filter, Pageable pageable){
        Specification<MatchEntity> spec=MatchSpecifications.withFilters(filter);
        Page<MatchEntity> matchPage=matchRepository.findAll(spec,pageable);
        return matchPage.map(match -> modelMapper.map(match, MatchDTO.class));
    }

    public MatchDTO createDraft(Long organizerId, CreateMatchRequest request){
        UserEntity organizer=userRepository.findById(organizerId)
                .orElseThrow(()->new EntityNotFoundException("Организатор не найден"));

        MatchEntity draftMatch=MatchEntity.builder()
                .organizer(organizer)
                .format(request.format())
                .location(request.location())
                .dateTime(request.dateTime())
                .maxPlayers(request.maxPlayers())
                .currentPlayers(1)
                .status(MatchStatus.DRAFT)
                .build();

        MatchEntity savedMatch=matchRepository.save(draftMatch);

        MatchParticipantEntity participant=MatchParticipantEntity.builder()
                .match(savedMatch)
                .user(organizer)
                .build();
        matchParticipantRepository.save(participant);

        return modelMapper.map(savedMatch, MatchDTO.class);
    }


    public MatchDTO publishMatch(Long matchId, Long organizerId){
        MatchEntity match=getMatchAndValidateOrganizer(matchId,organizerId);

        if(match.getStatus()!=MatchStatus.DRAFT){
            throw new IllegalStateException("Опубликовать можно только черновик");
        }

        match.setStatus(MatchStatus.OPEN);
        return modelMapper.map(matchRepository.save(match), MatchDTO.class);
    }

    public MatchDTO updateMatch(Long matchId, Long organizerId, UpdateMatchRequest request){
        MatchEntity match=getMatchAndValidateOrganizer(matchId,organizerId);

        if(match.getStatus()==MatchStatus.CANCELLED || match.getStatus()==MatchStatus.COMPLETED){
            throw new IllegalStateException("Нельзя редактировать отмененный или завершенный матч");
        }

        LocalDateTime oldTime = match.getDateTime();
        String oldLocation = match.getLocation();
        boolean isRescheduled = false;

        if (request.location() != null && !request.location().isBlank() && !request.location().equals(oldLocation)) {
            match.setLocation(request.location());
            isRescheduled = true;
        }
        if (request.dateTime() != null && !request.dateTime().equals(oldTime)) {
            match.setDateTime(request.dateTime());
            isRescheduled = true;
        }
        if (request.maxPlayers() != null) {
            if (request.maxPlayers() < match.getCurrentPlayers()) {
                throw new IllegalArgumentException("Нельзя сделать лимит меньше, чем уже записано игроков");
            }
            match.setMaxPlayers(request.maxPlayers());
        }

        MatchEntity savedMatch = matchRepository.save(match);

        // ПУБЛИКУЕМ СОБЫТИЕ ПЕРЕНОСА, ТОЛЬКО ЕСЛИ БЫЛИ ИЗМЕНЕНИЯ
        if (isRescheduled) {
            eventPublisher.publishEvent(new MatchRescheduledEvent(
                    matchId, oldTime, match.getDateTime(), oldLocation, match.getLocation()
            ));
        }

        return modelMapper.map(savedMatch, MatchDTO.class);
    }

    public void cancelMatch(Long matchId, Long organizerId){
        MatchEntity match=getMatchAndValidateOrganizer(matchId,organizerId);
        if(match.getStatus()==MatchStatus.COMPLETED){
            throw new IllegalStateException("Сыгранный матч нельзя отменить");
        }

        match.setStatus(MatchStatus.CANCELLED);
        matchRepository.save(match);

        eventPublisher.publishEvent(new MatchCancelledEvent(matchId));
    }

    public void joinMatch(Long matchId, Long userId){
        MatchEntity match=matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(()->new EntityNotFoundException("Матч не найден"));

        if(match.getStatus()!=MatchStatus.OPEN){
            throw new IllegalStateException("Регистрация на этот матч закрыта (матч отменен или завершен)");
        }

        if(match.getDateTime().isBefore(LocalDateTime.now())){
            throw new IllegalStateException("Матч уже начался или прошел. Запись невозможна.");
        }

        if(match.getCurrentPlayers()>=match.getMaxPlayers()){
            throw new IllegalStateException("Мест больше нет");
        }

        if(matchParticipantRepository.existsByMatchIdAndUserId(matchId,userId)){
            throw new IllegalStateException("Вы уже записаны на этот матч");
        }

        if(matchWaitlistRepository.existsByMatchIdAndUserId(matchId,userId)){
            throw new IllegalStateException("Вы уже находитесь в листе ожидания этого матча");
        }

        UserEntity user=userRepository.findById(userId)
                .orElseThrow(()->new EntityNotFoundException("Пользователь не найден"));

        MatchParticipantEntity participant=MatchParticipantEntity.builder()
                .match(match)
                .user(user)
                .teamColor(TeamColor.NONE)
                .build();
        matchParticipantRepository.save(participant);

        match.setCurrentPlayers(match.getCurrentPlayers()+1);
        matchRepository.save(match);
    }

    public void leaveMatch(Long matchId, Long userId){
        MatchEntity match=matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(()->new EntityNotFoundException("Матч не найден"));
        if(match.getStatus()!=MatchStatus.OPEN){
            throw new IllegalStateException("Отписаться можно только от активного матча");
        }

        if(match.getOrganizer().getId().equals(userId)){
            throw new IllegalStateException("Организатор не может покинуть матч. Вы можете только отменить его.");
        }

        MatchParticipantEntity participant=matchParticipantRepository
                .findByMatchIdAndUserId(matchId,userId)
                .orElseThrow(()->new EntityNotFoundException("Вы не записаны на этот матч"));


        matchParticipantRepository.delete(participant);
        Optional<MatchWaitlistEntity> waitlistEntry=matchWaitlistRepository
                .findFirstByMatchIdOrderByJoinedAtAsc(matchId);

        if(waitlistEntry.isPresent()){
            // Кто-то есть в очереди. Переводим счастливчика в основу
            UserEntity luckyUser=waitlistEntry.get().getUser();

            MatchParticipantEntity newParticipant=MatchParticipantEntity.builder()
                    .match(match)
                    .user(luckyUser)
                    .teamColor(TeamColor.NONE)
                    .build();
            matchParticipantRepository.save(newParticipant);
            matchWaitlistRepository.delete(waitlistEntry.get());

            // TODO: Отправить Push-уведомление или email счастливчику (luckyUser) о том, что он попал в состав
        }else{
            match.setCurrentPlayers(match.getCurrentPlayers()-1);
        }
        matchRepository.save(match);

        // --- АВТОМАТИЧЕСКИЙ ПЕРЕСЧЕТ ---
        teamBalancerService.rebalanceIfAlreadyBalanced(matchId, match.getOrganizer().getId());
    }

    public void joinWaitList(Long matchId,Long userId){
        MatchEntity match=matchRepository.findById(matchId)
                .orElseThrow(()->new EntityNotFoundException("Матч не найден"));

        if(match.getStatus()!=MatchStatus.OPEN){
            throw new IllegalStateException("Регистрация закрыта");
        }

        if(match.getDateTime().isBefore(LocalDateTime.now())){
            throw new IllegalStateException("Матч уже начался или прошел. Встать в очередь невозможно.");
        }

        if(match.getCurrentPlayers()<match.getMaxPlayers()){
            throw new IllegalStateException("В матче еще есть свободные места, записывайтесь сразу в основной состав!");
        }

        if(matchParticipantRepository.existsByMatchIdAndUserId(matchId,userId)){
            throw new IllegalStateException("Вы уже в основном составе");
        }

        if(matchWaitlistRepository.existsByMatchIdAndUserId(matchId,userId)){
            throw new IllegalStateException("Вы уже находитесь в листе ожидания");
        }


        UserEntity user=userRepository.findById(userId)
                .orElseThrow(()->new EntityNotFoundException("Пользователь не найден"));

        MatchWaitlistEntity waitlist=MatchWaitlistEntity.builder()
                .match(match)
                .user(user)
                .build();

        matchWaitlistRepository.save(waitlist);
    }

    public void updateParticipantStatus(Long matchId,
                                        Long targetUserId,
                                        Long organizerId,
                                        ParticipantStatus newStatus){
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));

        if (!match.getOrganizer().getId().equals(organizerId)) {
            throw new AccessDeniedException("Только организатор матча может управлять участниками");
        }

        // 2. Ищем участника
        MatchParticipantEntity participant = matchParticipantRepository.findByMatchIdAndUserId(matchId, targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("Игрок не найден в основном составе этого матча"));

        // 3. Защита от дурака: организатор не может поставить "неявку" самому себе
        if (targetUserId.equals(organizerId) && newStatus == ParticipantStatus.NO_SHOW) {
            throw new IllegalStateException("Организатор не может поставить неявку самому себе");
        }

        // 4. Обновляем статус
        participant.setStatus(newStatus);
        matchParticipantRepository.save(participant);

        // --- АВТОМАТИЧЕСКИЙ ПЕРЕСЧЕТ ---
        if (newStatus == ParticipantStatus.NO_SHOW) {
            teamBalancerService.rebalanceIfAlreadyBalanced(matchId, organizerId);
        }
    }

    public void startMatch(Long matchId, Long organizerId){
        MatchEntity match = getMatchAndValidateOrganizer(matchId, organizerId);

        if (match.getStatus() != MatchStatus.OPEN) {
            throw new IllegalStateException("Начать можно только матч со статусом OPEN");
        }

        if (match.getCurrentPlayers() < 2) {
            throw new IllegalStateException("Слишком мало игроков для старта матча");
        }

        match.setStatus(MatchStatus.IN_PROGRESS);
        matchRepository.save(match);

        // TODO: В будущем здесь можно кидать Event, чтобы пушить в ТГ: "Матч начался!"
    }


    @Transactional
    public void finishMatch(Long matchId, Long organizerId, FinishMatchRequest request) {
        MatchEntity match = getMatchAndValidateOrganizer(matchId, organizerId);

        // 1. Проверяем окно в 24 часа для уже завершенных матчей
        if (match.getStatus() == MatchStatus.COMPLETED) {
            if (match.getFinishedAt() != null && match.getFinishedAt().plusHours(24).isBefore(LocalDateTime.now())) {
                throw new IllegalStateException("Время редактирования вышло. Прошло больше 24 часов.");
            }
        }
        // 2. Для новых матчей ставим время завершения
        else if (match.getStatus() == MatchStatus.IN_PROGRESS || match.getStatus() == MatchStatus.OPEN) {
            match.setFinishedAt(LocalDateTime.now());
        }
        else {
            throw new IllegalStateException("Невозможно завершить матч в статусе " + match.getStatus());
        }

        // Обновляем счет и статус
        match.setScoreWhite(request.scoreWhite());
        match.setScoreDark(request.scoreDark());
        match.setStatus(MatchStatus.COMPLETED);
        matchRepository.save(match);

        // 3. Идемпотентное сохранение статистики
        if (request.playersStats() != null && !request.playersStats().isEmpty()) {
            List<MatchParticipantEntity> participants = matchParticipantRepository.findByMatchId(matchId);

            // Достаем уже существующую стату из базы (если это повторный запрос, список будет не пустой)
            List<PlayerStatsEntity> existingStats = playerStatsRepository.findByMatchId(matchId);

            List<PlayerStatsEntity> statsToSave = request.playersStats().stream().map(statDto -> {

                // Проверка, что игрок вообще был в заявке
                MatchParticipantEntity participant = participants.stream()
                        .filter(p -> p.getUser().getId().equals(statDto.userId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Игрок " + statDto.userId() + " не найден в матче"));

                Double calculatedRating = ratingCalculator.calculateRating(
                        statDto, participant, request.scoreWhite(), request.scoreDark()
                );

                // ИЩЕМ СТАТУ: если она уже есть — берем её для обновления, если нет — создаем новую
                PlayerStatsEntity stat = existingStats.stream()
                        .filter(s -> s.getUser().getId().equals(statDto.userId()))
                        .findFirst()
                        .orElse(new PlayerStatsEntity()); // <-- Создаем пустую, если не нашли

                // Заполняем/обновляем поля (Hibernate сам поймет, делать INSERT или UPDATE)
                stat.setMatch(match);
                stat.setUser(participant.getUser());
                stat.setGoals(statDto.goals());
                stat.setAssists(statDto.assists());
                stat.setMvpVotes(statDto.mvpVotes());
                stat.setFastestPlayerVotes(statDto.fastestPlayerVotes());
                stat.setMatchRating(calculatedRating);

                return stat;

            }).toList();

            // Сохраняем всё батчем
            playerStatsRepository.saveAll(statsToSave);

            for (PlayerStatsEntity stat:statsToSave){
                UserEntity user=stat.getUser();

                MatchParticipantEntity participant=participants.stream()
                        .filter(p -> p.getUser().getId().equals(user.getId()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Игрок не найден в участниках"));

                // Узнаем, сколько пропустила команда этого игрока
                int concededGoals = 0;
                if (participant.getTeamColor() == TeamColor.WHITE) {
                    concededGoals = request.scoreDark(); // Белые пропустили то, что забили Темные
                } else if (participant.getTeamColor() == TeamColor.DARK) {
                    concededGoals = request.scoreWhite(); // Темные пропустили то, что забили Белые
                }

                // Вызываем калькулятор
                // Он возьмет текущие PAC/SHO/PAS..., прибавит дельту и запишет новые значения обратно в user
                playerSkillCalculator.updateSkills(user, stat, concededGoals);

                // Сохраняем обновленного юзера в БД
                userRepository.save(user);
                match.setUpdatedByOrganizerId(organizerId);
            }
        }
    }


    // --- ПРИВАТНЫЙ МЕТОД ДЛЯ ПРОВЕРКИ ПРАВ ---
    private MatchEntity getMatchAndValidateOrganizer(Long matchId, Long userId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));

        if (!match.getOrganizer().getId().equals(userId)) {
            throw new AccessDeniedException("Вы не являетесь организатором этого матча");
        }
        return match;
    }
}
