package com.lsn.ragkb.dto.sales;

import com.lsn.ragkb.dto.RagResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class SalesAgentResponse {
    private String answer;
    private String route;
    private String salesContext;
    private String knowledgeAnswer;
    private List<RagResponse.Source> sources;
    private int latencyMs;
}
