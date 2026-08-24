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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
    private final NotificationService notificationService;
    private final MatchResultService matchResultService;
    private final UserDisciplineService disciplineService;

    public MatchService(MatchRepository matchRepository,
                        ModelMapper modelMapper,
                        UserRepository userRepository,
                        MatchParticipantRepository matchParticipantRepository,
                        MatchWaitlistRepository matchWaitlistRepository,
                        ApplicationEventPublisher eventPublisher,
                        TeamBalancerService teamBalancerService,
                        MatchRatingCalculator ratingCalculator,
                        PlayerStatsRepository playerStatsRepository,
                        PlayerSkillCalculator playerSkillCalculator,
                        NotificationService notificationService,
                        MatchResultService matchResultService,
                        UserDisciplineService disciplineService) {
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
        this.notificationService=notificationService;
        this.matchResultService = matchResultService;
        this.disciplineService = disciplineService;
    }

    @Transactional(readOnly = true)
    public Page<MatchDTO> getMatches(MatchFilterRequest filter, Pageable pageable){
        Specification<MatchEntity> spec=MatchSpecifications.withFilters(filter);
        Page<MatchEntity> matchPage=matchRepository.findAll(spec,pageable);
        return matchPage.map(match -> modelMapper.map(match, MatchDTO.class));
    }

    @Transactional(readOnly = true)
    public MatchDetailsDTO getMatchDetails(Long matchId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));

        List<MatchParticipantDetailsDTO> participants = matchParticipantRepository.findByMatchId(matchId)
                .stream()
                .map(participant -> new MatchParticipantDetailsDTO(
                        participant.getId(),
                        participant.getUser().getId(),
                        matchId,
                        participant.getTeamColor(),
                        participant.getStatus(),
                        toUserDTO(participant.getUser())
                ))
                .toList();

        List<MatchWaitlistDetailsDTO> waitlist = matchWaitlistRepository.findByMatchIdOrderByJoinedAtAsc(matchId)
                .stream()
                .map(entry -> new MatchWaitlistDetailsDTO(
                        entry.getId(),
                        entry.getUser().getId(),
                        matchId,
                        entry.getJoinedAt(),
                        toUserDTO(entry.getUser())
                ))
                .toList();

        return new MatchDetailsDTO(
                match.getId(),
                match.getFormat(),
                match.getLocation(),
                match.getDateTime(),
                match.getDuration(),
                match.getCurrentPlayers(),
                match.getMaxPlayers(),
                match.getStatus(),
                match.getScoreWhite(),
                match.getScoreDark(),
                match.getResultVotingEndsAt(),
                match.getResultEligibleVoters(),
                match.getResultConfirmationsRequired(),
                toUserDTO(match.getOrganizer()),
                participants,
                waitlist
        );
    }

    private UserDTO toUserDTO(UserEntity user) {
        if (user == null) return null;
        return new UserDTO(
                user.getId(),
                user.getTelegramId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getAvatarUrl(),
                user.getPosition(),
                user.getRole(),
                user.isVip(),
                user.getVipUntil(),
                user.getOvr(),
                user.getPace(),
                user.getShoot(),
                user.getPass(),
                user.getDribbling(),
                user.getDefend(),
                user.getPhysic()
        );
    }

    public MatchDTO createDraft(Long organizerId, CreateMatchRequest request){
        UserEntity organizer=userRepository.findById(organizerId)
                .orElseThrow(()->new EntityNotFoundException("Организатор не найден"));
        disciplineService.assertCanParticipate(organizer);

        MatchEntity draftMatch=MatchEntity.builder()
                .organizer(organizer)
                .format(request.format())
                .location(request.location())
                .dateTime(request.dateTime())
                .duration(MatchEntity.MAX_DURATION_MINUTES)
                .minPlayers(10)
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
        teamBalancerService.autoBalanceTeams(savedMatch.getId());

        return modelMapper.map(savedMatch, MatchDTO.class);
    }


    @Transactional(isolation = Isolation.SERIALIZABLE)
    public MatchDTO publishMatch(Long matchId, Long organizerId){
        MatchEntity match=getMatchAndValidateOrganizer(matchId,organizerId);
        disciplineService.assertCanParticipate(match.getOrganizer());

        if(match.getStatus()!=MatchStatus.DRAFT){
            throw new IllegalStateException("Опубликовать можно только черновик");
        }

        assertVenueAvailable(match);

        match.setStatus(MatchStatus.OPEN);
        MatchEntity savedMatch=matchRepository.save(match);
        matchRepository.flush();

        List<Long> telegramIds=userRepository.findAllByTelegramIdIsNotNull().stream()
                .map(UserEntity::getTelegramId)
                .toList();

        String notificationText = String.format(
                "🔥 Открыт набор на новый матч!\n\n📍 Локация: %s\n⚽ Формат: %s\n\nСкорее заходи в Mini App и занимай место в основном составе!",
                match.getLocation(),
                match.getFormat()
        );
        notificationService.sendToUsers(telegramIds,notificationText);

        return modelMapper.map(savedMatch, MatchDTO.class);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public MatchDTO updateMatch(Long matchId, Long organizerId, UpdateMatchRequest request){
        MatchEntity match=getMatchAndValidateOrganizer(matchId,organizerId);

        if(match.getStatus()==MatchStatus.CANCELLED
                || match.getStatus()==MatchStatus.COMPLETED
                || match.getStatus()==MatchStatus.RESULT_PENDING
                || match.getStatus()==MatchStatus.RESULT_DISPUTED
                || match.getStatus()==MatchStatus.RESULT_REJECTED){
            throw new IllegalStateException("Нельзя редактировать матч после отправки протокола");
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

        if (match.getStatus() == MatchStatus.OPEN || match.getStatus() == MatchStatus.IN_PROGRESS) {
            assertVenueAvailable(match);
        }

        MatchEntity savedMatch = matchRepository.save(match);
        matchRepository.flush();

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
        if(match.getStatus()==MatchStatus.COMPLETED
                || match.getStatus()==MatchStatus.RESULT_PENDING
                || match.getStatus()==MatchStatus.RESULT_DISPUTED
                || match.getStatus()==MatchStatus.RESULT_REJECTED){
            throw new IllegalStateException("Матч с отправленным протоколом нельзя отменить");
        }

        match.setStatus(MatchStatus.CANCELLED);
        matchRepository.save(match);

        List<Long> telegramIds = matchParticipantRepository.findByMatchId(matchId).stream()
                .map(participant -> participant.getUser().getTelegramId())
                .filter(id -> id != null) // Защита от NullPointerException
                .toList();

        String cancelText = String.format(
                "⚠️ Внимание! Матч на арене %s был отменен организатором. Приносим извинения за неудобства.",
                match.getLocation()
        );

        notificationService.sendToUsers(telegramIds, cancelText);

        eventPublisher.publishEvent(new MatchCancelledEvent(matchId));
    }

    public void joinMatch(Long matchId, Long userId){
        MatchEntity match=matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(()->new EntityNotFoundException("Матч не найден"));

        if(match.getStatus()!=MatchStatus.OPEN){
            throw new IllegalStateException("Регистрация на этот матч закрыта (матч отменен или завершен)");
        }

        if(match.getDateTime().isBefore(LocalDateTime.now(ZoneId.of("Europe/Moscow")))){
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
        disciplineService.assertCanParticipate(user);

        MatchParticipantEntity participant=MatchParticipantEntity.builder()
                .match(match)
                .user(user)
                .teamColor(TeamColor.NONE)
                .build();
        matchParticipantRepository.save(participant);

        match.setCurrentPlayers(match.getCurrentPlayers()+1);
        matchRepository.save(match);
        teamBalancerService.autoBalanceTeams(matchId);
    }

    public void leaveMatch(Long matchId, Long userId){
        MatchEntity match=matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(()->new EntityNotFoundException("Матч не найден"));
        if(match.getStatus()!=MatchStatus.OPEN){
            throw new IllegalStateException("Отписаться можно только от активного матча");
        }

        if (!LocalDateTime.now(ZoneId.of("Europe/Moscow")).isBefore(match.getDateTime())) {
            throw new IllegalStateException("После начала матча отменить участие нельзя. Организатор отметит присутствие или неявку.");
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

            String text = String.format(
                    "🎉 Отличные новости! Кто-то отменил запись, и вы автоматически переведены из листа ожидания в основной состав!\n\n📍 Арена: %s\n⏳ Не забудьте подтвердить участие в Mini App.",
                    match.getLocation()
            );
            notificationService.sendToUser(luckyUser.getTelegramId(), text);
        }else{
            match.setCurrentPlayers(match.getCurrentPlayers()-1);
        }
        matchRepository.save(match);

        teamBalancerService.autoBalanceTeams(matchId);
    }

    public void joinWaitList(Long matchId,Long userId){
        MatchEntity match=matchRepository.findById(matchId)
                .orElseThrow(()->new EntityNotFoundException("Матч не найден"));

        if(match.getStatus()!=MatchStatus.OPEN){
            throw new IllegalStateException("Регистрация закрыта");
        }

        if(match.getDateTime().isBefore(LocalDateTime.now(ZoneId.of("Europe/Moscow")))){
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
        disciplineService.assertCanParticipate(user);

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

        if (newStatus == ParticipantStatus.NO_SHOW) {
            if (LocalDateTime.now(ZoneId.of("Europe/Moscow")).isBefore(match.getDateTime())) {
                throw new IllegalStateException("Неявку можно поставить только после начала матча");
            }
            if (match.getStatus() != MatchStatus.OPEN && match.getStatus() != MatchStatus.IN_PROGRESS) {
                throw new IllegalStateException("Неявку нужно отметить до отправки итогового протокола");
            }
        }

        // 2. Ищем участника
        MatchParticipantEntity participant = matchParticipantRepository.findByMatchIdAndUserId(matchId, targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("Игрок не найден в основном составе этого матча"));

        // 3. Защита от дурака: организатор не может поставить "неявку" самому себе
        if (targetUserId.equals(organizerId) && newStatus == ParticipantStatus.NO_SHOW) {
            throw new IllegalStateException("Организатор не может поставить неявку самому себе");
        }

        if (participant.getStatus() == newStatus) {
            throw new IllegalStateException("Этот статус уже установлен");
        }

        if (newStatus == ParticipantStatus.NO_SHOW) {
            disciplineService.registerNoShow(match, participant, match.getOrganizer());
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

        if (LocalDateTime.now(ZoneId.of("Europe/Moscow")).isBefore(match.getDateTime())) {
            throw new IllegalStateException("Матч нельзя начать раньше назначенного времени");
        }

        long activePlayers = matchParticipantRepository.findByMatchIdAndStatusNot(matchId, ParticipantStatus.NO_SHOW).size();
        if (activePlayers < 10) {
            throw new IllegalStateException("Для старта нужно минимум 10 игроков");
        }

        match.setStatus(MatchStatus.IN_PROGRESS);
        matchRepository.save(match);

        // TODO: В будущем здесь можно кидать Event, чтобы пушить в ТГ: "Матч начался!"
    }


    public void finishMatch(Long matchId, Long organizerId, FinishMatchRequest request) {
        matchResultService.submitProtocol(matchId, organizerId, request);
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

    private void assertVenueAvailable(MatchEntity candidate) {
        LocalDateTime start = candidate.getDateTime();
        int duration = Math.min(
                Optional.ofNullable(candidate.getDuration()).orElse(MatchEntity.MAX_DURATION_MINUTES),
                MatchEntity.MAX_DURATION_MINUTES
        );
        LocalDateTime end = start.plusMinutes(duration);
        Long excludedId = candidate.getId() == null ? -1L : candidate.getId();

        boolean conflict = matchRepository.findVenueConflictCandidates(
                        excludedId,
                        List.of(MatchStatus.OPEN, MatchStatus.IN_PROGRESS),
                        start.minusMinutes(MatchEntity.MAX_DURATION_MINUTES),
                        end
                ).stream()
                .filter(existing -> normalizeLocation(existing.getLocation())
                        .equals(normalizeLocation(candidate.getLocation())))
                .anyMatch(existing -> {
                    int existingDuration = Math.min(
                            Optional.ofNullable(existing.getDuration()).orElse(MatchEntity.MAX_DURATION_MINUTES),
                            MatchEntity.MAX_DURATION_MINUTES
                    );
                    return existing.getDateTime().plusMinutes(existingDuration).isAfter(start);
                });

        if (conflict) {
            throw new IllegalStateException("На этой площадке уже есть матч в выбранное время. "
                    + "Выберите время не раньше чем через 30 минут или другую площадку.");
        }
    }

    private String normalizeLocation(String location) {
        if (location == null) return "";
        return Normalizer.normalize(location, Normalizer.Form.NFKC)
                .toLowerCase()
                .replace('ё', 'е')
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}
