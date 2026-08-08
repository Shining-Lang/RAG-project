package com.lsn.ragkb.controller;

import com.lsn.ragkb.dto.ApiResponse;
import com.lsn.ragkb.tool.AnomalyDetectionTool;
import com.lsn.ragkb.tool.ChartGeneratorTool;
import com.lsn.ragkb.tool.SalesSummaryTool;
import com.lsn.ragkb.tool.SalesTrendTool;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sales-agent/tools")
@RequiredArgsConstructor
public class SalesAgentToolController {

    private final SalesSummaryTool salesSummaryTool;
    private final SalesTrendTool salesTrendTool;
    private final AnomalyDetectionTool anomalyDetectionTool;
    private final ChartGeneratorTool chartGeneratorTool;

    @GetMapping("/summary")
    public ApiResponse<String> summary(@RequestParam String startDate,
                                       @RequestParam String endDate,
                                       @RequestParam(required = false) String regionName) {
        return ApiResponse.ok(salesSummaryTool.getSalesSummary(startDate, endDate, regionName));
    }

    @GetMapping("/top-reps")
    public ApiResponse<String> topReps(@RequestParam String startDate,
                                      @RequestParam String endDate,
                                      @RequestParam(defaultValue = "5") int topN,
                                      @RequestParam(required = false) String regionName) {
        return ApiResponse.ok(salesSummaryTool.getTopReps(startDate, endDate, regionName, topN));
    }

    @GetMapping("/trend")
    public ApiResponse<String> trend(@RequestParam(defaultValue = "6") int months,
                                    @RequestParam(required = false) String regionName) {
        return ApiResponse.ok(salesTrendTool.getMonthlyTrend(months, regionName));
    }

    @GetMapping("/anomalies")
    public ApiResponse<String> anomalies() {
        return ApiResponse.ok(anomalyDetectionTool.detectSalesAnomalies());
    }

    @GetMapping("/chart/line")
    public ApiResponse<String> lineChart(@RequestParam(defaultValue = "6") int months,
                                        @RequestParam(required = false) String regionName,
                                        @RequestParam(required = false) String title) {
        return ApiResponse.ok(chartGeneratorTool.generateLineChart(months, regionName, title));
    }
}
