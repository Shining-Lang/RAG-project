package com.lsn.ragkb.controller;

import com.lsn.ragkb.dto.ApiResponse;
import com.lsn.ragkb.dto.RagQueryRequest;
import com.lsn.ragkb.dto.RagResponse;
import com.lsn.ragkb.service.rag.FullRagPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagQueryController {

    private final FullRagPipeline fullRagPipeline;

    @PostMapping("/query")
    public ApiResponse<RagResponse> query(@RequestBody RagQueryRequest req) {
        return ApiResponse.ok(fullRagPipeline.query(req.getQuestion(), req.getKbIds()));
    }
}