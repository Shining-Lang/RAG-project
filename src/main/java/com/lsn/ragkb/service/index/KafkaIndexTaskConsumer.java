package com.lsn.ragkb.service.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lsn.ragkb.security.UserContext;
import com.lsn.ragkb.service.monitoring.ObservabilityMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "index.kafka", name = "enabled", havingValue = "true")
public class KafkaIndexTaskConsumer {

    private final ObjectMapper objectMapper;

    private final IndexService indexService;
    private final ObservabilityMetrics metrics;

    public KafkaIndexTaskConsumer(ObjectMapper objectMapper,
                                  @Lazy IndexService indexService,
                                  ObservabilityMetrics metrics) {
        this.objectMapper = objectMapper;
        this.indexService = indexService;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = "${index.kafka.topic}",
            groupId = "${spring.kafka.consumer.group-id}",
            concurrency = "${index.kafka.concurrency}",
            autoStartup = "${index.kafka.enabled:false}"
    )
    public void consume(String payload, Acknowledgment acknowledgment) {
        IndexTaskMessage message = null;
        try {
            message = objectMapper.readValue(payload, IndexTaskMessage.class);
            if (!IndexService.TASK_TYPE_FROM_MINIO.equals(message.getTaskType())) {
                log.warn("[IndexKafka] 忽略不支持的任务类型：payload={}", payload);
                acknowledgment.acknowledge();
                return;
            }

            UserContext.set(message.getUserId(), message.getDepartmentId(), message.getRole());
            indexService.executeFromMinio(message.getTaskId(), message.getDocId());
            metrics.recordKafkaIndexConsume("success");
            acknowledgment.acknowledge();
        } catch (Exception e) {
            metrics.recordKafkaIndexConsume("error");
            Long taskId = message == null ? null : message.getTaskId();
            Long docId = message == null ? null : message.getDocId();
            log.error("[IndexKafka] 消费索引任务失败：taskId={}, docId={}, error={}",
                    taskId, docId, e.getMessage(), e);
            throw new IllegalStateException("Kafka 索引任务消费失败", e);
        } finally {
            UserContext.clear();
        }
    }
}
