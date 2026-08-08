package com.lsn.ragkb.controller;

import com.lsn.ragkb.dto.ApiResponse;
import com.lsn.ragkb.dto.sales.SalesAgentRequest;
import com.lsn.ragkb.dto.sales.SalesAgentResponse;
import com.lsn.ragkb.service.sales.SalesAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/sales-agent")
@RequiredArgsConstructor
public class SalesAgentController {

    private final SalesAgentService salesAgentService;

    @PostMapping("/chat")
    public ApiResponse<SalesAgentResponse> chat(@RequestBody SalesAgentRequest request) {
        return ApiResponse.ok(salesAgentService.chat(request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody SalesAgentRequest request) {
        return salesAgentService.streamChat(request);
    }
}
