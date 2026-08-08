package com.lsn.ragkb.service.sales;

import com.lsn.ragkb.entity.AgentMemory;
import com.lsn.ragkb.repository.AgentMemoryRepository;
import com.lsn.ragkb.security.UserContext;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageJsonCodec;
import dev.langchain4j.data.message.JacksonChatMessageJsonCodec;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersistentChatMemoryStore implements ChatMemoryStore {

    private final AgentMemoryRepository repository;
    private final ChatMessageJsonCodec codec = new JacksonChatMessageJsonCodec();

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessage> getMessages(Object memoryId) {
        return repository.findById(asString(memoryId))
                .map(memory -> {
                    try {
                        return codec.messagesFromJson(memory.getMessagesJson());
                    } catch (Exception e) {
                        log.warn("[AgentMemory] Failed to decode memoryId={}", memory.getMemoryId(), e);
                        return List.<ChatMessage>of();
                    }
                })
                .orElseGet(List::of);
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String id = asString(memoryId);
        MemoryKey key = parse(id);

        AgentMemory memory = repository.findById(id).orElseGet(() -> {
            AgentMemory created = new AgentMemory();
            created.setMemoryId(id);
            created.setUserId(key.userId());
            created.setSessionId(key.sessionId());
            return created;
        });
        memory.setMessagesJson(codec.messagesToJson(messages));
        memory.setMessageCount(messages.size());
        repository.save(memory);
    }

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        repository.deleteById(asString(memoryId));
    }

    private String asString(Object memoryId) {
        return String.valueOf(memoryId);
    }

    private MemoryKey parse(String memoryId) {
        int split = memoryId.indexOf(':');
        if (split > 0 && split < memoryId.length() - 1) {
            try {
                return new MemoryKey(Long.parseLong(memoryId.substring(0, split)), memoryId.substring(split + 1));
            } catch (NumberFormatException ignored) {
                // Fall through to request context defaults.
            }
        }
        return new MemoryKey(UserContext.getUserId(), memoryId);
    }

    private record MemoryKey(Long userId, String sessionId) {
    }
}
