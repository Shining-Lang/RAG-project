package com.lsn.ragkb.tool;

import com.lsn.ragkb.dto.RagResponse;
import com.lsn.ragkb.service.rag.FullRagPipeline;
import com.lsn.ragkb.service.sales.SalesAgentRequestContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class KnowledgeRetrievalTool {

    private final FullRagPipeline ragPipeline;
    private final SalesAgentRequestContext requestContext;

    @Tool("检索企业销售知识库。适用于：销售流程、客户异议处理、话术、制度、手册、预测口径、交接规范等非结构化文档问题。")
    public String retrieveSalesKnowledge(
            @P("需要检索的自然语言问题，尽量保留用户原意") String question) {
        List<Long> kbIds = requestContext.kbIds();
        log.info("[SalesTool] retrieveSalesKnowledge kbIds={}, question={}", kbIds, question);
        if (kbIds == null || kbIds.isEmpty()) {
            return "本次请求没有传入可检索的知识库 ID，无法检索知识库。";
        }
        try {
            RagResponse response = ragPipeline.query(question, kbIds);
            if (response == null || response.isNotFound()) {
                requestContext.setKnowledgeAnswer("");
                requestContext.setSources(List.of());
                return "知识库中未检索到足够相关的内容。";
            }
            requestContext.setKnowledgeAnswer(response.getAnswer());
            requestContext.setSources(response.getSources());
            return response.getAnswer();
        } catch (Exception e) {
            log.warn("[SalesTool] retrieveSalesKnowledge degraded: {}", e.getMessage());
            return "知识库检索暂时不可用，请先基于结构化销售数据回答，并说明知识库证据不足。";
        }
    }
}
