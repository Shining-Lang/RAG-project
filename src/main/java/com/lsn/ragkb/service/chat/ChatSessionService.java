package com.lsn.ragkb.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lsn.ragkb.entity.ChatMessage;
import com.lsn.ragkb.entity.ChatSession;
import com.lsn.ragkb.repository.ChatMessageRepository;
import com.lsn.ragkb.repository.ChatSessionRepository;
import com.lsn.ragkb.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatSessionService {

    private static final int MAX_HISTORY_ROUNDS = 5;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public String getOrCreateSession(String sessionId, List<Long> kbIds) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionRepository.findById(sessionId)
                    .ifPresentOrElse(s -> {
                        s.setLastActiveAt(LocalDateTime.now());
                        sessionRepository.save(s);
                    }, () -> createSession(sessionId, kbIds));
            return sessionId;
        }

        String newSessionId = UUID.randomUUID().toString();
        createSession(newSessionId, kbIds);
        return newSessionId;
    }

    @Transactional
    public void saveMessage(String sessionId, String question, String answer,
                            String sourcesJson, int latencyMs) {
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSessionId(sessionId);
        userMsg.setRole("USER");
        userMsg.setContent(question);
        messageRepository.save(userMsg);

        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSessionId(sessionId);
        assistantMsg.setRole("ASSISTANT");
        assistantMsg.setContent(answer);
        assistantMsg.setSources(sourcesJson);
        assistantMsg.setLatencyMs(latencyMs);
        messageRepository.save(assistantMsg);

        sessionRepository.findById(sessionId).ifPresent(s -> {
            s.setMessageCount(s.getMessageCount() + 2);
            s.setLastActiveAt(LocalDateTime.now());
            if (s.getTitle() == null && question.length() > 0) {
                s.setTitle(question.substring(0, Math.min(50, question.length())));
            }
            sessionRepository.save(s);
        });
    }

    public List<ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> all = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        int maxMessages = MAX_HISTORY_ROUNDS * 2;
        if (all.size() > maxMessages) {
            all = all.subList(all.size() - maxMessages, all.size());
        }
        return all;
    }

    private void createSession(String sessionId, List<Long> kbIds) {
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setUserId(UserContext.getUserId());
        session.setKbIds(objectMapper.valueToTree(kbIds == null ? List.of() : kbIds).toString());
        session.setMessageCount(0);
        sessionRepository.save(session);

        log.info("[ChatSession] Created session: sessionId={}, userId={}",
                session.getId(), UserContext.getUserId());
    }
}
