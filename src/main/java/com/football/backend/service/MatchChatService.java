package com.football.backend.service;

import com.football.backend.dto.MatchChatMessageDTO;
import com.football.backend.entity.MatchChatMessageEntity;
import com.football.backend.entity.MatchEntity;
import com.football.backend.entity.UserEntity;
import com.football.backend.repository.MatchChatMessageRepository;
import com.football.backend.repository.MatchParticipantRepository;
import com.football.backend.repository.MatchRepository;
import com.football.backend.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class MatchChatService {
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final MatchParticipantRepository participantRepository;
    private final MatchChatMessageRepository messageRepository;

    public MatchChatService(MatchRepository matchRepository,
                            UserRepository userRepository,
                            MatchParticipantRepository participantRepository,
                            MatchChatMessageRepository messageRepository) {
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional(readOnly = true)
    public List<MatchChatMessageDTO> getMessages(Long matchId, Long userId) {
        MatchEntity match = accessibleMatch(matchId, userId);
        List<MatchChatMessageEntity> messages = new ArrayList<>(messageRepository
                .findByMatchIdOrderByCreatedAtDesc(match.getId(), PageRequest.of(0, 100)));
        messages.sort(java.util.Comparator.comparing(MatchChatMessageEntity::getCreatedAt));
        return messages.stream().map(this::toDto).toList();
    }

    @Transactional
    public MatchChatMessageDTO send(Long matchId, Long userId, String rawText) {
        MatchEntity match = accessibleMatch(matchId, userId);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));
        MatchChatMessageEntity saved = messageRepository.saveAndFlush(MatchChatMessageEntity.builder()
                .match(match)
                .user(user)
                .text(rawText.trim())
                .build());
        return toDto(saved);
    }

    private MatchEntity accessibleMatch(Long matchId, Long userId) {
        MatchEntity match = matchRepository.findById(matchId)
                .orElseThrow(() -> new EntityNotFoundException("Матч не найден"));
        if (!participantRepository.existsByMatchIdAndUserId(matchId, userId)
                && !match.getOrganizer().getId().equals(userId)) {
            throw new AccessDeniedException("Чат доступен только участникам матча");
        }
        return match;
    }

    private MatchChatMessageDTO toDto(MatchChatMessageEntity message) {
        UserEntity user = message.getUser();
        String displayName = String.join(" ",
                user.getFirstName() == null ? "" : user.getFirstName(),
                user.getLastName() == null ? "" : user.getLastName()).trim();
        if (displayName.isBlank()) displayName = user.getUsername() == null ? "Игрок" : user.getUsername();
        return new MatchChatMessageDTO(
                message.getId(), user.getId(), displayName, user.getAvatarUrl(), user.isVip(),
                message.getText(), message.getCreatedAt()
        );
    }
}
