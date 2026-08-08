package com.lsn.ragkb.controller;

import com.lsn.ragkb.dto.ApiResponse;
import com.lsn.ragkb.dto.sales.SalesAgentRequest;
import com.lsn.ragkb.dto.sales.SalesAgentResponse;
import com.lsn.ragkb.service.sales.SalesAgentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sales-agent")
@RequiredArgsConstructor
public class SalesAgentController {

    private final SalesAgentService salesAgentService;

    @PostMapping("/chat")
    public ApiResponse<SalesAgentResponse> chat(@RequestBody SalesAgentRequest request) {
        return ApiResponse.ok(salesAgentService.chat(request));
    }
}
