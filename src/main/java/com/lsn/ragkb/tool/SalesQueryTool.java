package com.lsn.ragkb.tool;

import com.lsn.ragkb.security.ToolInputValidator;
import com.lsn.ragkb.service.sales.SalesAnalyticsService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class SalesQueryTool {

    private final SalesAnalyticsService analyticsService;
    private final ToolInputValidator validator;

    @Tool("查询原始销售订单明细。适用于：查看某时间段订单列表、具体客户订单、某销售员或大区订单。")
    public String queryOrders(
            @P("查询开始日期，格式 yyyy-MM-dd") String startDate,
            @P("查询结束日期，格式 yyyy-MM-dd") String endDate,
            @P("大区名称，例如：华东区。传 null 或空字符串表示不限大区") String regionName,
            @P("销售员姓名，例如：张磊。传 null 或空字符串表示不限销售员") String repName,
            @P("最多返回条数，默认 20，最大 50") int limit) {
        log.info("[SalesTool] queryOrders start={}, end={}, region={}, rep={}, limit={}",
                startDate, endDate, regionName, repName, limit);
        try {
            LocalDate start = validator.parseDate(startDate);
            LocalDate end = validator.parseDate(endDate);
            validator.validateDateRange(start, end);
            String normalizedRegion = validator.normalizeNullableName(regionName);
            String normalizedRep = validator.normalizeNullableName(repName);
            Long regionId = normalizedRegion == null ? null
                    : analyticsService.findRegionIdByName(normalizedRegion).orElse(null);
            Long repId = normalizedRep == null ? null
                    : analyticsService.findRepIdByName(normalizedRep).orElse(null);
            if (normalizedRegion != null && regionId == null) {
                return "未找到大区：" + normalizedRegion;
            }
            if (normalizedRep != null && repId == null) {
                return "未找到销售员：" + normalizedRep;
            }
            return analyticsService.formatOrderList(repId, regionId, start, end, validator.normalizeLimit(limit));
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[SalesTool] queryOrders failed", e);
            return "查询订单数据时出现问题，请稍后重试。";
        }
    }
}
