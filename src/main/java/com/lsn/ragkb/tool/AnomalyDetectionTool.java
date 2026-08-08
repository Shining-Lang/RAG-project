package com.lsn.ragkb.tool;

import com.lsn.ragkb.service.sales.SalesAnalyticsService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectionTool {

    private final SalesAnalyticsService analyticsService;

    @Tool("检测销售异常和经营风险。适用于：异常预警、销售下滑、退单率偏高、产品连续零销售、大区风险复盘。")
    public String detectSalesAnomalies() {
        log.info("[SalesTool] detectSalesAnomalies");
        try {
            return analyticsService.formatAnomalies();
        } catch (Exception e) {
            log.error("[SalesTool] detectSalesAnomalies failed", e);
            return "检测销售异常时出现问题，请稍后重试。";
        }
    }
}
