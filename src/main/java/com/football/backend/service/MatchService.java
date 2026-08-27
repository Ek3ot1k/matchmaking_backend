package com.football.backend.service;

import com.football.backend.dto.*;
import com.football.backend.entity.*;
import com.football.backend.model.MatchStatus;
import com.football.backend.model.ParticipantStatus;
import com.football.backend.model.Position;
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
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class MatchService {
    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
    private static final int DAILY_ORGANIZER_LIMIT = 3;
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
        return matchPage.map(match -> {
            MatchDTO dto = modelMapper.map(match, MatchDTO.class);
            dto.setOrganizerId(match.getOrganizer().getId());
            return dto;
        });
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
                        participant.getPosition(),
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
                        entry.getPosition(),
                        entry.getJoinedAt(),
                        toUserDTO(entry.getUser())
                ))
                .toList();

        Map<Long, MatchParticipantEntity> participantsByUserId = matchParticipantRepository.findByMatchId(matchId)
                .stream()
                .collect(Collectors.toMap(participant -> participant.getUser().getId(), participant -> participant));
        List<MatchPlayerReportDTO> playerStats = playerStatsRepository.findByMatchId(matchId).stream()
                .map(stat -> {
                    MatchParticipantEntity participant = participantsByUserId.get(stat.getUser().getId());
                    return new MatchPlayerReportDTO(
                            stat.getUser().getId(),
                            toUserDTO(stat.getUser()),
                            participant == null ? TeamColor.NONE : participant.getTeamColor(),
                            participant == null ? null : participant.getPosition(),
                            stat.getGoals(),
                            stat.getAssists(),
                            stat.getMvpVotes(),
                            stat.getMatchRating()
                    );
                })
                .toList();

        return new MatchDetailsDTO(
                match.getId(),
                match.getFormat(),
                match.getLocation(),
                match.getLatitude(),
                match.getLongitude(),
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
                match.getWhiteFormation(),
                match.getDarkFormation(),
                match.getChatLink(),
                toUserDTO(match.getOrganizer()),
                participants,
                waitlist,
                playerStats
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

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public MatchDTO createDraft(Long organizerId, CreateMatchRequest request){
        UserEntity organizer=userRepository.findByIdForUpdate(organizerId)
                .orElseThrow(()->new EntityNotFoundException("Организатор не найден"));
        disciplineService.assertCanParticipate(organizer);
        if (request.requestId() != null && !request.requestId().isBlank()) {
            Optional<MatchEntity> existing = matchRepository.findByCreationRequestId(request.requestId());
            if (existing.isPresent()) return modelMapper.map(existing.get(), MatchDTO.class);
        }
        assertDailyCreateLimit(organizer);
        String format = MatchEntity.normalizeSupportedFormat(request.format());

        MatchEntity draftMatch=MatchEntity.builder()
                .organizer(organizer)
                .format(format)
                .location(request.location())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .dateTime(request.dateTime())
                .duration(MatchEntity.MATCH_DURATION_MINUTES)
                .minPlayers(10)
                .maxPlayers(MatchEntity.maxPlayersForFormat(format))
                .currentPlayers(1)
                .status(MatchStatus.OPEN)
                .chatLink(normalizeChatLink(request.chatLink()))
                .creationRequestId(request.requestId() == null || request.requestId().isBlank()
                        ? null : request.requestId().trim())
                .build();

        assertVenueAvailable(draftMatch);

        MatchEntity savedMatch=matchRepository.save(draftMatch);

        MatchParticipantEntity participant=MatchParticipantEntity.builder()
                .match(savedMatch)
                .user(organizer)
                .position(request.position())
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

        String format = MatchEntity.normalizeSupportedFormat(match.getFormat());
        match.setFormat(format);
        match.setMaxPlayers(MatchEntity.maxPlayersForFormat(format));
        match.setDuration(MatchEntity.MATCH_DURATION_MINUTES);

        assertVenueAvailable(match);

        match.setStatus(MatchStatus.OPEN);
        MatchEntity savedMatch=matchRepository.save(match);
        matchRepository.flush();

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
        if (request.latitude() != null) {
            match.setLatitude(request.latitude());
        }
        if (request.longitude() != null) {
            match.setLongitude(request.longitude());
        }
        if (request.dateTime() != null && !request.dateTime().equals(oldTime)) {
            match.setDateTime(request.dateTime());
            isRescheduled = true;
        }
        if (request.maxPlayers() != null) {
            int expectedMaxPlayers = MatchEntity.maxPlayersForFormat(match.getFormat());
            if (!request.maxPlayers().equals(expectedMaxPlayers)) {
                throw new IllegalStateException("Количество игроков определяется форматом матча");
            }
            if (request.maxPlayers() < match.getCurrentPlayers()) {
                throw new IllegalArgumentException("Нельзя сделать лимит меньше, чем уже записано игроков");
            }
            match.setMaxPlayers(request.maxPlayers());
        }
        if (request.chatLink() != null) {
            match.setChatLink(normalizeChatLink(request.chatLink()));
        }

        if (match.getStatus() == MatchStatus.OPEN || match.getStatus() == MatchStatus.IN_PROGRESS) {
            assertVenueAvailable(match);
        }

        MatchEntity savedMatch = matchRepository.save(match);
        matchRepository.flush();

        // ПУБЛИКУЕМ СОБЫТИЕ ПЕРЕНОСА, ТОЛЬКО ЕСЛИ БЫЛИ ИЗМЕНЕНИЯ
        if (isRescheduled) {
            notifyParticipants(matchId, String.format(
                    "📣 Матч изменён: %s, %s. Проверьте новые время или место в Mini App.",
                    match.getLocation(), match.getDateTime()));
            eventPublisher.publishEvent(new MatchRescheduledEvent(
                    matchId, oldTime, match.getDateTime(), oldLocation, match.getLocation()
            ));
        }

        return modelMapper.map(savedMatch, MatchDTO.class);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void cancelMatch(Long matchId, Long organizerId){
        MatchEntity match=matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));
        if (!match.getOrganizer().getId().equals(organizerId)) {
            throw new AccessDeniedException("Отменить можно только свой матч");
        }
        if (!LocalDateTime.now(MOSCOW_ZONE).isBefore(match.getDateTime())) {
            throw new IllegalStateException("После начала матча отмена запрещена");
        }
        if(match.getStatus()==MatchStatus.COMPLETED
                || match.getStatus()==MatchStatus.RESULT_PENDING
                || match.getStatus()==MatchStatus.RESULT_DISPUTED
                || match.getStatus()==MatchStatus.RESULT_REJECTED){
            throw new IllegalStateException("Матч с отправленным протоколом нельзя отменить");
        }
        assertDailyCancelLimit(match.getOrganizer());

        match.setStatus(MatchStatus.CANCELLED);
        match.setCancelledAt(LocalDateTime.now(MOSCOW_ZONE));
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

    public void joinMatch(Long matchId, Long userId, Position position){
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
        assertPositionAvailable(match, position, null);

        MatchParticipantEntity participant=MatchParticipantEntity.builder()
                .match(match)
                .user(user)
                .position(position)
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
        if (!promoteWaitlistedPlayer(match)) {
            match.setCurrentPlayers(match.getCurrentPlayers()-1);
        }
        matchRepository.save(match);

        teamBalancerService.autoBalanceTeams(matchId);
    }

    /**
     * Организатор может заменить только игрока, который не подтвердил участие.
     * Это доступно до старта и исключительно когда в очереди действительно есть замена.
     */
    public void releaseUnconfirmedParticipant(Long matchId, Long targetUserId, Long organizerId) {
        MatchEntity match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));
        if (!match.getOrganizer().getId().equals(organizerId)) {
            throw new AccessDeniedException("Заменить игрока может только организатор");
        }
        if (match.getStatus() != MatchStatus.OPEN || !LocalDateTime.now(MOSCOW_ZONE).isBefore(match.getDateTime())) {
            throw new IllegalStateException("Заменять неподтвердившихся можно только до начала матча");
        }
        if (targetUserId.equals(organizerId)) {
            throw new IllegalStateException("Организатора нельзя заменить");
        }
        MatchParticipantEntity participant = matchParticipantRepository.findByMatchIdAndUserId(matchId, targetUserId)
                .orElseThrow(() -> new EntityNotFoundException("Игрок не найден в основном составе"));
        if (participant.getStatus() == ParticipantStatus.CONFIRMED) {
            throw new IllegalStateException("Игрок подтвердил участие — его место нельзя освободить");
        }
        if (!matchWaitlistRepository.findFirstByMatchIdOrderByJoinedAtAsc(matchId).isPresent()) {
            throw new IllegalStateException("В листе ожидания пока нет замены");
        }

        matchParticipantRepository.delete(participant);
        promoteWaitlistedPlayer(match);
        matchRepository.save(match);
        teamBalancerService.autoBalanceTeams(matchId);
        notificationService.sendToUser(participant.getUser().getTelegramId(), String.format(
                "ℹ️ Место в матче на площадке %s освобождено, потому что участие не было подтверждено. Можно выбрать другой матч в Mini App.",
                match.getLocation()
        ));
    }

    public void joinWaitList(Long matchId,Long userId, Position position){
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
        if (position == null || position == Position.UNKNOWN) {
            throw new IllegalArgumentException("Выберите игровую позицию");
        }

        MatchWaitlistEntity waitlist=MatchWaitlistEntity.builder()
                .match(match)
                .user(user)
                .position(position)
                .build();

        matchWaitlistRepository.save(waitlist);
    }

    public void changeMyPosition(Long matchId, Long userId, Position position) {
        MatchEntity match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));
        if (match.getStatus() != MatchStatus.OPEN || !LocalDateTime.now(MOSCOW_ZONE).isBefore(match.getDateTime())) {
            throw new IllegalStateException("Позицию можно менять только до начала матча");
        }
        MatchParticipantEntity participant = matchParticipantRepository.findByMatchIdAndUserId(matchId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Вы не записаны на этот матч"));
        if (participant.getPosition() == position) return;
        assertPositionAvailable(match, position, participant.getId());
        participant.setPosition(position);
        matchParticipantRepository.save(participant);
        teamBalancerService.autoBalanceTeams(matchId);
    }

    /** Игрок самостоятельно подтверждает, что придёт на матч. Повторный клик безопасен. */
    public void checkIn(Long matchId, Long userId) {
        MatchEntity match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));
        if (match.getStatus() != MatchStatus.OPEN || !LocalDateTime.now(MOSCOW_ZONE).isBefore(match.getDateTime())) {
            throw new IllegalStateException("Подтвердить участие можно только до начала активного матча");
        }
        MatchParticipantEntity participant = matchParticipantRepository.findByMatchIdAndUserId(matchId, userId)
                .orElseThrow(() -> new EntityNotFoundException("Вы не записаны на этот матч"));
        if (participant.getStatus() == ParticipantStatus.NO_SHOW) {
            throw new IllegalStateException("Нельзя подтвердить участие после отметки о неявке");
        }
        if (participant.getStatus() != ParticipantStatus.CONFIRMED) {
            participant.setStatus(ParticipantStatus.CONFIRMED);
            matchParticipantRepository.save(participant);
        }
    }

    public void updateTeamFormation(Long matchId, Long userId, String formation) {
        MatchEntity match = matchRepository.findByIdForUpdate(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));
        if (match.getStatus() != MatchStatus.OPEN || !LocalDateTime.now(MOSCOW_ZONE).isBefore(match.getDateTime())) {
            throw new IllegalStateException("Схему можно выбрать только до начала матча");
        }
        String normalized = formation.trim().replace('-', '–');
        if (!List.of("1–2–1–1", "1–2–2–1", "1–3–1–1", "1–1–3–1", "1–2–3–1", "1–3–2–1", "Свободная").contains(normalized)) {
            throw new IllegalArgumentException("Выберите схему из предложенного списка");
        }
        List<MatchParticipantEntity> participants = matchParticipantRepository
                .findByMatchIdAndStatusNot(matchId, ParticipantStatus.NO_SHOW);
        MatchParticipantEntity current = participants.stream()
                .filter(p -> p.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Схему может выбрать только капитан команды"));
        if (current.getTeamColor() == TeamColor.WHITE && match.getOrganizer().getId().equals(userId)) {
            match.setWhiteFormation(normalized);
        } else if (current.getTeamColor() == TeamColor.DARK && isDarkCaptain(participants, userId)) {
            match.setDarkFormation(normalized);
        } else {
            throw new AccessDeniedException("Схему может выбрать только капитан команды");
        }
        matchRepository.save(match);
    }

    private boolean isDarkCaptain(List<MatchParticipantEntity> participants, Long userId) {
        return participants.stream()
                .filter(p -> p.getTeamColor() == TeamColor.DARK)
                .max(java.util.Comparator.comparingInt(p -> p.getUser().getOvr()))
                .map(p -> p.getUser().getId().equals(userId))
                .orElse(false);
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

    private boolean promoteWaitlistedPlayer(MatchEntity match) {
        Optional<MatchWaitlistEntity> waitlistEntry = matchWaitlistRepository
                .findFirstByMatchIdOrderByJoinedAtAsc(match.getId());
        if (waitlistEntry.isEmpty()) return false;

        UserEntity luckyUser = waitlistEntry.get().getUser();
        MatchParticipantEntity newParticipant = MatchParticipantEntity.builder()
                .match(match)
                .user(luckyUser)
                .position(waitlistEntry.get().getPosition())
                .teamColor(TeamColor.NONE)
                .build();
        matchParticipantRepository.save(newParticipant);
        matchWaitlistRepository.delete(waitlistEntry.get());
        notificationService.sendToUser(luckyUser.getTelegramId(), String.format(
                "🎉 Отличные новости! Вы автоматически переведены из листа ожидания в основной состав!\n\n📍 Арена: %s\n⏳ Не забудьте подтвердить участие в Mini App.",
                match.getLocation()
        ));
        return true;
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

    private String normalizeChatLink(String chatLink) {
        if (chatLink == null || chatLink.isBlank()) return null;
        String normalized = chatLink.trim();
        String lowerCase = normalized.toLowerCase();
        if (!lowerCase.startsWith("https://t.me/") && !lowerCase.startsWith("https://telegram.me/")) {
            throw new IllegalArgumentException("Укажите ссылку-приглашение Telegram в формате https://t.me/...");
        }
        return normalized;
    }

    private void assertDailyCreateLimit(UserEntity organizer) {
        LocalDateTime start = LocalDateTime.now(MOSCOW_ZONE).toLocalDate().atStartOfDay();
        if (matchRepository.countCreatedByOrganizerBetween(organizer.getId(), start, start.plusDays(1)) >= DAILY_ORGANIZER_LIMIT) {
            notificationService.sendToUser(organizer.getTelegramId(), "⚠️ Достигнут лимит: не более 3 созданных матчей в сутки (по Москве).");
            throw new IllegalStateException("Лимит созданий матчей на сегодня исчерпан: максимум 3 в сутки по Москве");
        }
    }

    private void assertDailyCancelLimit(UserEntity organizer) {
        LocalDateTime start = LocalDateTime.now(MOSCOW_ZONE).toLocalDate().atStartOfDay();
        if (matchRepository.countCancelledByOrganizerBetween(organizer.getId(), start, start.plusDays(1)) >= DAILY_ORGANIZER_LIMIT) {
            notificationService.sendToUser(organizer.getTelegramId(), "⚠️ Достигнут лимит: не более 3 отмен своих матчей в сутки (по Москве).");
            throw new IllegalStateException("Лимит отмен на сегодня исчерпан: максимум 3 в сутки по Москве");
        }
    }

    private void assertPositionAvailable(MatchEntity match, Position position, Long excludedParticipantId) {
        if (position == null || position == Position.UNKNOWN) {
            throw new IllegalArgumentException("Выберите игровую позицию");
        }
        int maxForPosition = Math.max(1, match.getMaxPlayers() / 2);
        long taken = matchParticipantRepository.findByMatchId(match.getId()).stream()
                .filter(p -> excludedParticipantId == null || !p.getId().equals(excludedParticipantId))
                .filter(p -> p.getStatus() != ParticipantStatus.NO_SHOW)
                .filter(p -> p.getPosition() == position)
                .count();
        if (taken >= maxForPosition) {
            throw new IllegalStateException("На этой позиции мест больше нет");
        }
    }

    private void notifyParticipants(Long matchId, String message) {
        notificationService.sendToUsers(matchParticipantRepository.findByMatchId(matchId).stream()
                .map(participant -> participant.getUser().getTelegramId())
                .filter(id -> id != null)
                .toList(), message);
    }
}
