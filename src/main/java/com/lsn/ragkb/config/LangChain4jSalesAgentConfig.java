package com.lsn.ragkb.config;

import com.lsn.ragkb.agent.SalesAgent;
import com.lsn.ragkb.tool.AnomalyDetectionTool;
import com.lsn.ragkb.tool.ChartGeneratorTool;
import com.lsn.ragkb.tool.KnowledgeRetrievalTool;
import com.lsn.ragkb.tool.SalesQueryTool;
import com.lsn.ragkb.tool.SalesSummaryTool;
import com.lsn.ragkb.tool.SalesTrendTool;
import com.lsn.ragkb.service.sales.PersistentChatMemoryStore;
import com.lsn.ragkb.service.sales.SalesAgentRequestContext;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class LangChain4jSalesAgentConfig {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;
    private final SalesQueryTool salesQueryTool;
    private final SalesSummaryTool salesSummaryTool;
    private final SalesTrendTool salesTrendTool;
    private final AnomalyDetectionTool anomalyDetectionTool;
    private final ChartGeneratorTool chartGeneratorTool;
    private final KnowledgeRetrievalTool knowledgeRetrievalTool;
    private final SalesAgentRequestContext requestContext;
    private final PersistentChatMemoryStore persistentChatMemoryStore;

    @Bean
    public SalesAgent salesAgent() {
        return AiServices.builder(SalesAgent.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .tools(salesQueryTool,
                        salesSummaryTool,
                        salesTrendTool,
                        anomalyDetectionTool,
                        chartGeneratorTool,
                        knowledgeRetrievalTool)
                .beforeToolExecution(exec -> {
                    String trace = "tool start: " + exec.request().name() + " args=" + exec.request().arguments();
                    requestContext.addToolTrace(trace);
                    log.info("[SalesAgent] {}", trace);
                })
                .afterToolExecution(exec -> {
                    String trace = "tool done: " + exec.request().name()
                            + " resultLength=" + (exec.result() == null ? 0 : exec.result().length());
                    requestContext.addToolTrace(trace);
                    log.info("[SalesAgent] {}", trace);
                })
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(20)
                        .chatMemoryStore(persistentChatMemoryStore)
                        .build())
                .build();
    }
}
