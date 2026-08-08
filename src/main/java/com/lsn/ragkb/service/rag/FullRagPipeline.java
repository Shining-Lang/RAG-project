package com.lsn.ragkb.service.rag;

import com.lsn.ragkb.dto.RagResponse;
import com.lsn.ragkb.service.monitoring.ObservabilityMetrics;
import com.lsn.ragkb.service.rag.filter.ConfidenceFilter;
import com.lsn.ragkb.service.token.ContextTrimmerService;
import com.lsn.ragkb.template.RagPromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FullRagPipeline {

    private final EnhancedRetrieverService enhancedRetriever;
    private final RerankerService rerankerService;
    private final ConfidenceFilter confidenceFilter;
    private final ContextTrimmerService contextTrimmer;
    private final SourceBuilder sourceBuilder;
    private final HallucinationChecker hallucinationChecker;
    private final ChatClient chatClient;
    private final ObservabilityMetrics metrics;

    @Value("${reranker.top-n:5}")
    private int rerankerTopN;

    public RagResponse query(String question, List<Long> kbIds) {
        long pipelineStart = System.currentTimeMillis();

        try {
            List<HybridRetrieverService.ScoredChunk> candidates =
                    enhancedRetriever.retrieveWithHyde(question, kbIds, 20);

            if (candidates.isEmpty()) {
                metrics.recordRagQuery("not_found", System.currentTimeMillis() - pipelineStart, 0);
                return RagResponse.notFound();
            }

            List<HybridRetrieverService.ScoredChunk> reranked =
                    rerankerService.rerank(question, candidates, rerankerTopN);

            List<HybridRetrieverService.ScoredChunk> filtered = confidenceFilter.filter(reranked);

            if (filtered.isEmpty()) {
                metrics.recordRagQuery("filtered", System.currentTimeMillis() - pipelineStart, 0);
                return RagResponse.notFound();
            }

            List<HybridRetrieverService.ScoredChunk> trimmed = contextTrimmer.trim(filtered);
            String context = buildContext(trimmed);
            String answer = generateAnswer(question, context, trimmed.size());
            List<RagResponse.Source> sources = sourceBuilder.buildSources(answer, trimmed);

            if (System.currentTimeMillis() % 5 == 0) {
                var faithResult = hallucinationChecker.check(question, answer, context);
                if (!faithResult.isFaithful()) {
                    log.warn("[FullRagPipeline] hallucination check failed: score={}, reason={}",
                            faithResult.score(), faithResult.reason());
                }
            }

            long elapsed = System.currentTimeMillis() - pipelineStart;
            metrics.recordRagQuery("success", elapsed, sources.size());
            log.info("[FullRagPipeline] completed: question={}, elapsed={}ms, sources={}",
                    question.substring(0, Math.min(30, question.length())), elapsed, sources.size());

            return RagResponse.builder()
                    .answer(answer)
                    .sources(sources)
                    .latencyMs((int) elapsed)
                    .build();
        } catch (RuntimeException e) {
            metrics.recordRagQuery("error", System.currentTimeMillis() - pipelineStart, 0);
            throw e;
        }
    }

    private String buildContext(List<HybridRetrieverService.ScoredChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            var sc = chunks.get(i);
            sb.append("[参考").append(i + 1).append("]");
            if (sc.chunk().getSectionTitle() != null) {
                sb.append(" ").append(sc.chunk().getSectionTitle());
            }
            sb.append("\n").append(sc.content()).append("\n\n");
        }
        return sb.toString().strip();
    }

    private String generateAnswer(String question, String context, int chunkCount) {
        String systemPrompt = RagPromptTemplate.buildSystemPrompt(context, chunkCount);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(question)
                .call()
                .content();
    }
}