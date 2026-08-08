package com.lsn.ragkb.dto.sales;

import lombok.Data;

import java.util.List;

@Data
public class SalesAgentRequest {
    private String sessionId;
    private String message;
    private List<Long> kbIds;
}
