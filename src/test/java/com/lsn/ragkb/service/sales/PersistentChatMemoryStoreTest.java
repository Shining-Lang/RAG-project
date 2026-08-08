package com.lsn.ragkb.service.sales;

import com.lsn.ragkb.entity.AgentMemory;
import com.lsn.ragkb.repository.AgentMemoryRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistentChatMemoryStoreTest {

    @Mock
    private AgentMemoryRepository repository;

    @InjectMocks
    private PersistentChatMemoryStore store;

    @Test
    void persistsAndRestoresLangChain4jMessages() {
        String memoryId = "3:sales-demo";
        List<ChatMessage> messages = List.of(
                UserMessage.from("华东区销售怎么样？"),
                AiMessage.from("华东区销售额表现稳定。")
        );
        when(repository.findById(memoryId)).thenReturn(Optional.empty());

        store.updateMessages(memoryId, messages);

        ArgumentCaptor<AgentMemory> captor = ArgumentCaptor.forClass(AgentMemory.class);
        verify(repository).save(captor.capture());
        AgentMemory saved = captor.getValue();
        assertThat(saved.getMemoryId()).isEqualTo(memoryId);
        assertThat(saved.getUserId()).isEqualTo(3L);
        assertThat(saved.getSessionId()).isEqualTo("sales-demo");
        assertThat(saved.getMessageCount()).isEqualTo(2);
        assertThat(saved.getMessagesJson()).contains("华东区销售怎么样");

        when(repository.findById(memoryId)).thenReturn(Optional.of(saved));
        List<ChatMessage> restored = store.getMessages(memoryId);

        assertThat(restored).hasSize(2);
        assertThat(((UserMessage) restored.get(0)).singleText()).isEqualTo("华东区销售怎么样？");
        assertThat(((AiMessage) restored.get(1)).text()).isEqualTo("华东区销售额表现稳定。");
    }

    @Test
    void deletesMemoryById() {
        store.deleteMessages("3:sales-demo");

        verify(repository).deleteById("3:sales-demo");
    }
}
