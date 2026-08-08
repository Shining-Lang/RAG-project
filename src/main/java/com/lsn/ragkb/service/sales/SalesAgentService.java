package com.lsn.ragkb.service.sales;

import com.lsn.ragkb.dto.RagResponse;
import com.lsn.ragkb.dto.sales.SalesAgentRequest;
import com.lsn.ragkb.dto.sales.SalesAgentResponse;
import com.lsn.ragkb.service.rag.FullRagPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesAgentService {

    private final SalesAnalyticsService analyticsService;
    private final FullRagPipeline ragPipeline;
    private final ChatClient chatClient;

    @Value("${sales-agent.synthesis.enabled:true}")
    private boolean synthesisEnabled;

    public SalesAgentResponse chat(SalesAgentRequest request) {
        long start = System.currentTimeMillis();
        String question = request.getMessage() == null ? "" : request.getMessage().strip();
        Route route = route(question, request.getKbIds());

        String salesContext = route.includeSales()
                ? analyticsService.buildContext(question)
                : "";

        RagResponse ragResponse = null;
        String knowledgeAnswer = "";
        if (route.includeKnowledge()) {
            try {
                ragResponse = ragPipeline.query(question, request.getKbIds());
                if (ragResponse != null && !ragResponse.isNotFound()) {
                    knowledgeAnswer = ragResponse.getAnswer();
                }
            } catch (Exception e) {
                log.warn("[SalesAgent] RAG knowledge lookup degraded: {}", e.getMessage());
                knowledgeAnswer = "知识库检索暂时不可用，已先基于结构化销售数据回答。";
            }
        }

        String answer = synthesize(question, route, salesContext, knowledgeAnswer);
        int latencyMs = (int) (System.currentTimeMillis() - start);

        return SalesAgentResponse.builder()
                .answer(answer)
                .route(route.name())
                .salesContext(salesContext)
                .knowledgeAnswer(knowledgeAnswer)
                .sources(ragResponse == null ? List.of() : ragResponse.getSources())
                .latencyMs(latencyMs)
                .build();
    }

    private Route route(String question, List<Long> kbIds) {
        String q = question.toLowerCase();
        boolean sales = containsAny(q, "销售", "业绩", "订单", "营收", "大区", "产品", "sku",
                "客户", "排名", "趋势", "异常", "预警", "退单", "利润", "top");
        boolean knowledge = kbIds != null && !kbIds.isEmpty()
                && containsAny(q, "制度", "政策", "话术", "手册", "流程", "怎么", "如何", "原因", "建议");

        if (sales && knowledge) return Route.HYBRID;
        if (sales) return Route.SALES_DATA;
        if (knowledge) return Route.KNOWLEDGE_RAG;
        return kbIds == null || kbIds.isEmpty() ? Route.SALES_DATA : Route.HYBRID;
    }

    private String synthesize(String question, Route route, String salesContext, String knowledgeAnswer) {
        String fallback = fallbackAnswer(route, salesContext, knowledgeAnswer);
        if (!synthesisEnabled) {
            return fallback;
        }

        try {
            String system = """
                    你是一个企业级销售 Copilot，负责把结构化销售数据和知识库内容融合成可执行建议。
                    要求：
                    1. 数据结论必须基于给定的销售数据上下文，不要编造数字。
                    2. 如果有知识库答案，把它作为制度、话术或流程依据。
                    3. 输出中文，先给结论，再给依据，最后给行动建议。
                    4. 当数据和知识库不足时，明确说明不足。

                    【销售数据上下文】
                    %s

                    【知识库答案】
                    %s
                    """.formatted(blankToNone(salesContext), blankToNone(knowledgeAnswer));

            return chatClient.prompt()
                    .system(system)
                    .user(question)
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("[SalesAgent] LLM synthesis degraded: {}", e.getMessage());
            return fallback;
        }
    }

    private String fallbackAnswer(Route route, String salesContext, String knowledgeAnswer) {
        return switch (route) {
            case SALES_DATA -> salesContext.isBlank() ? "暂未找到可用销售数据。" : salesContext;
            case KNOWLEDGE_RAG -> knowledgeAnswer.isBlank() ? "暂未找到可用知识库答案。" : knowledgeAnswer;
            case HYBRID -> (salesContext + "\n\n【知识库补充】\n" +
                    (knowledgeAnswer.isBlank() ? "暂未检索到可用知识库补充。" : knowledgeAnswer)).strip();
        };
    }

    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }

    private boolean containsAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private enum Route {
        SALES_DATA(true, false),
        KNOWLEDGE_RAG(false, true),
        HYBRID(true, true);

        private final boolean includeSales;
        private final boolean includeKnowledge;

        Route(boolean includeSales, boolean includeKnowledge) {
            this.includeSales = includeSales;
            this.includeKnowledge = includeKnowledge;
        }

        boolean includeSales() {
            return includeSales;
        }

        boolean includeKnowledge() {
            return includeKnowledge;
        }
    }
}
