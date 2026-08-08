package com.lsn.ragkb.service.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ObservabilityMetrics {

    private final MeterRegistry meterRegistry;

    public void recordRagQuery(String status, long latencyMs, int sourceCount) {
        Counter.builder("rag.query.total")
                .description("Total RAG query requests")
                .tag("status", status)
                .register(meterRegistry)
                .increment();
        Timer.builder("rag.query.latency")
                .description("RAG query latency")
                .tag("status", status)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
        meterRegistry.summary("rag.query.sources", "status", status).record(sourceCount);
    }

    public void recordSalesAgentChat(String route, String status, long latencyMs) {
        Counter.builder("sales.agent.chat.total")
                .description("Total Sales Agent chat requests")
                .tag("route", route)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
        Timer.builder("sales.agent.chat.latency")
                .description("Sales Agent chat latency")
                .tag("route", route)
                .tag("status", status)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    public void recordIndexTaskSubmitted(String taskType, String channel) {
        Counter.builder("rag.index.task.submitted")
                .description("Submitted index tasks")
                .tag("task_type", taskType)
                .tag("channel", channel)
                .register(meterRegistry)
                .increment();
    }

    public void recordIndexTaskFinished(String status) {
        Counter.builder("rag.index.task.finished")
                .description("Finished index tasks")
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    public void recordKafkaIndexConsume(String status) {
        Counter.builder("rag.index.kafka.consume.total")
                .description("Kafka index consumer messages")
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }
}
