package com.lsn.ragkb.service.sales;

import com.lsn.ragkb.agent.SalesAgent;
import com.lsn.ragkb.dto.sales.SalesAgentRequest;
import com.lsn.ragkb.dto.sales.SalesAgentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesAgentService {

    private final SalesAgent salesAgent;
    private final SalesAnalyticsService analyticsService;
    private final SalesAgentRequestContext requestContext;
    private final ObjectMapper objectMapper;

    public SalesAgentResponse chat(SalesAgentRequest request) {
        long start = System.currentTimeMillis();
        String question = request.getMessage() == null ? "" : request.getMessage().strip();
        String sessionId = request.getSessionId() == null || request.getSessionId().isBlank()
                ? "sales-" + UUID.randomUUID()
                : request.getSessionId();
        List<Long> kbIds = request.getKbIds() == null ? List.of() : request.getKbIds();

        requestContext.init(kbIds);
        try {
            String answer = salesAgent.chat(
                    sessionId,
                    question,
                    LocalDate.now().toString(),
                    kbIds.isEmpty() ? "未传入知识库 ID" : kbIds.toString());

            return SalesAgentResponse.builder()
                    .answer(answer)
                    .route("LANGCHAIN4J_TOOL_AGENT")
                    .salesContext("")
                    .knowledgeAnswer(requestContext.knowledgeAnswer())
                    .sources(requestContext.sources())
                    .toolTraces(requestContext.toolTraces())
                    .latencyMs((int) (System.currentTimeMillis() - start))
                    .build();
        } catch (Exception e) {
            log.warn("[SalesAgent] LangChain4j agent degraded: {}", e.getMessage());
            String fallback = analyticsService.buildContext(question);
            return SalesAgentResponse.builder()
                    .answer(fallback)
                    .route("FALLBACK_SALES_ANALYTICS")
                    .salesContext(fallback)
                    .knowledgeAnswer("")
                    .sources(List.of())
                    .toolTraces(List.of("LangChain4j agent 调用失败，已降级到本地销售分析服务。"))
                    .latencyMs((int) (System.currentTimeMillis() - start))
                    .build();
        } finally {
            requestContext.clear();
        }
    }

    public SseEmitter streamChat(SalesAgentRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        String question = request.getMessage() == null ? "" : request.getMessage().strip();
        String sessionId = request.getSessionId() == null || request.getSessionId().isBlank()
                ? "sales-" + UUID.randomUUID()
                : request.getSessionId();
        List<Long> kbIds = request.getKbIds() == null ? List.of() : request.getKbIds();

        requestContext.init(kbIds);
        try {
            salesAgent.chatStream(
                            sessionId,
                            question,
                            LocalDate.now().toString(),
                            kbIds.isEmpty() ? "未传入知识库 ID" : kbIds.toString())
                    .onPartialResponse(token -> send(emitter, "token", token))
                    .onCompleteResponse(response -> {
                        try {
                            String done = objectMapper.writeValueAsString(Map.of(
                                    "sources", requestContext.sources(),
                                    "toolTraces", requestContext.toolTraces()
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

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
