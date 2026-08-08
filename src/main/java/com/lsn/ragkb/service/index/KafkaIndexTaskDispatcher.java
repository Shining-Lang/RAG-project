package com.lsn.ragkb.service.index;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "index.kafka", name = "enabled", havingValue = "true")
public class KafkaIndexTaskDispatcher implements IndexTaskDispatcher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final IndexTaskLauncher taskLauncher;

    @Value("${index.kafka.topic}")
    private String topic;

    @Override
    public void dispatchFromMinio(Long taskId, Long docId,
                                  Long userId, String departmentId, String role) {
        IndexTaskMessage message = IndexTaskMessage.builder()
                .taskId(taskId)
                .docId(docId)
                .taskType(IndexService.TASK_TYPE_FROM_MINIO)
                .userId(userId)
                .departmentId(departmentId)
                .role(role)
                .build();
        try {
            String payload = objectMapper.writeValueAsString(message);
            kafkaTemplate.send(topic, String.valueOf(docId), payload);
            log.info("[IndexKafka] 已投递索引任务：topic={}, taskId={}, docId={}", topic, taskId, docId);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("索引任务消息序列化失败", e);
        }
    }

    @Override
    public void dispatchWithText(Long taskId, Long docId, String textContent,
                                 Long userId, String departmentId, String role) {
        // 文本任务内容只存在内存中，不放入 Kafka，避免大消息和重启后不可恢复的问题。
        taskLauncher.launchWithText(taskId, docId, textContent, userId, departmentId, role);
    }
}
