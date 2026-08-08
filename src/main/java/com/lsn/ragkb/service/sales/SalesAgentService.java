package com.lsn.ragkb.service.sales;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lsn.ragkb.agent.SalesAgent;
import com.lsn.ragkb.dto.sales.SalesAgentRequest;
import com.lsn.ragkb.dto.sales.SalesAgentResponse;
import com.lsn.ragkb.security.UserContext;
import com.lsn.ragkb.service.chat.ChatSessionService;
import com.lsn.ragkb.service.monitoring.ObservabilityMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesAgentService {

    private final SalesAgent salesAgent;
    private final SalesAnalyticsService analyticsService;
    private final SalesAgentRequestContext requestContext;
    private final ChatSessionService sessionService;
    private final ObjectMapper objectMapper;
    private final ObservabilityMetrics metrics;

    public SalesAgentResponse chat(SalesAgentRequest request) {
        long start = System.currentTimeMillis();
        String question = request.getMessage() == null ? "" : request.getMessage().strip();
        List<Long> kbIds = request.getKbIds() == null ? List.of() : request.getKbIds();
        String sessionId = sessionService.getOrCreateSession(request.getSessionId(), kbIds);
        String memoryId = buildMemoryId(sessionId);

        requestContext.init(kbIds);
        try {
            String answer = salesAgent.chat(
                    memoryId,
                    question,
                    LocalDate.now().toString(),
                    kbIds.isEmpty() ? "未传入知识库 ID" : kbIds.toString());
            int latencyMs = (int) (System.currentTimeMillis() - start);
            metrics.recordSalesAgentChat("LANGCHAIN4J_TOOL_AGENT", "success", latencyMs);
            List<String> toolTraces = requestContext.toolTraces();
            sessionService.saveMessage(sessionId, question, answer, sourcesJson(toolTraces), latencyMs);

            return SalesAgentResponse.builder()
                    .sessionId(sessionId)
                    .answer(answer)
                    .route("LANGCHAIN4J_TOOL_AGENT")
                    .salesContext("")
                    .knowledgeAnswer(requestContext.knowledgeAnswer())
                    .sources(requestContext.sources())
                    .toolTraces(toolTraces)
                    .latencyMs(latencyMs)
                    .build();
        } catch (Exception e) {
            log.warn("[SalesAgent] LangChain4j agent degraded: {}", e.getMessage());
            String fallback = analyticsService.buildContext(question);
            int latencyMs = (int) (System.currentTimeMillis() - start);
            metrics.recordSalesAgentChat("FALLBACK_SALES_ANALYTICS", "degraded", latencyMs);
            sessionService.saveMessage(sessionId, question, fallback, "[]", latencyMs);

            return SalesAgentResponse.builder()
                    .sessionId(sessionId)
                    .answer(fallback)
                    .route("FALLBACK_SALES_ANALYTICS")
                    .salesContext(fallback)
                    .knowledgeAnswer("")
                    .sources(List.of())
                    .toolTraces(List.of("LangChain4j agent 调用失败，已降级到本地销售分析服务。"))
                    .latencyMs(latencyMs)
                    .build();
        } finally {
            requestContext.clear();
        }
    }

    public SseEmitter streamChat(SalesAgentRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        long start = System.currentTimeMillis();
        StringBuilder answerBuffer = new StringBuilder();
        String question = request.getMessage() == null ? "" : request.getMessage().strip();
        List<Long> kbIds = request.getKbIds() == null ? List.of() : request.getKbIds();
        String sessionId = sessionService.getOrCreateSession(request.getSessionId(), kbIds);
        String memoryId = buildMemoryId(sessionId);

        requestContext.init(kbIds);
        try {
            salesAgent.chatStream(
                            memoryId,
                            question,
                            LocalDate.now().toString(),
                            kbIds.isEmpty() ? "未传入知识库 ID" : kbIds.toString())
                    .onPartialResponse(token -> {
                        answerBuffer.append(token);
                        send(emitter, "token", token);
                    })
                    .onCompleteResponse(response -> {
                        try {
                            int latencyMs = (int) (System.currentTimeMillis() - start);
                            List<String> toolTraces = requestContext.toolTraces();
                            sessionService.saveMessage(sessionId, question, answerBuffer.toString(),
                                    sourcesJson(toolTraces), latencyMs);
                            String done = objectMapper.writeValueAsString(Map.of(
                                    "sessionId", sessionId,
                                    "sources", requestContext.sources(),
                                    "toolTraces", toolTraces,
                                    "latencyMs", latencyMs
                            ));
                            send(emitter, "done", done);
                            emitter.complete();
                        } catch (Exception e) {
                            emitter.completeWithError(e);
                        } finally {
                            requestContext.clear();
                        }
                    })
                    .onError(error -> {
                        log.warn("[SalesAgent] stream degraded: {}", error.getMessage());
                        send(emitter, "error", "销售 Agent 流式响应暂时不可用，请稍后重试。");
                        requestContext.clear();
                        emitter.complete();
                    })
                    .start();
        } catch (Exception e) {
            log.warn("[SalesAgent] stream start failed: {}", e.getMessage());
            send(emitter, "error", "销售 Agent 流式响应启动失败，请稍后重试。");
            requestContext.clear();
            emitter.complete();
        }
        return emitter;
    }

    private String buildMemoryId(String sessionId) {
        return UserContext.getUserId() + ":" + sessionId;
    }

    private String sourcesJson(List<String> toolTraces) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "sources", requestContext.sources(),
                    "toolTraces", toolTraces
            ));
        } catch (Exception e) {
            return "[]";
        }
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
