package com.lsn.ragkb.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@Configuration
@ConditionalOnProperty(prefix = "index.kafka", name = "enabled", havingValue = "true")
public class KafkaIndexConfig {

    @Bean
    public NewTopic indexTaskTopic(@Value("${index.kafka.topic}") String topic) {
        return new NewTopic(topic, 3, (short) 1);
    }
}
