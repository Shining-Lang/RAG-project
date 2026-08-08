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
public class SalesSummaryTool {

    private final SalesAnalyticsService analyticsService;
    private final ToolInputValidator validator;

    @Tool("计算指定时间段的总销售额、订单数等汇总数据。适用于：总销售额、本月/本季/今年收入、某大区整体业绩。")
    public String getSalesSummary(
            @P("查询开始日期，格式 yyyy-MM-dd") String startDate,
            @P("查询结束日期，格式 yyyy-MM-dd") String endDate,
            @P("大区名称，例如：华东区。传 null 表示全公司") String regionName) {
        log.info("[SalesTool] getSalesSummary start={}, end={}, region={}", startDate, endDate, regionName);
        try {
            LocalDate start = validator.parseDate(startDate);
            LocalDate end = validator.parseDate(endDate);
            validator.validateDateRange(start, end);
            Long regionId = resolveRegionId(regionName);
            return analyticsService.formatSalesSummary(regionId, start, end);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[SalesTool] getSalesSummary failed", e);
            return "查询销售汇总数据时出现问题，请稍后重试。";
        }
    }

    @Tool("计算销售员业绩排名。适用于：谁卖得最多、Top N 销售员、销售冠军、按大区筛选销售员排名。")
    public String getTopReps(
            @P("查询开始日期，格式 yyyy-MM-dd") String startDate,
            @P("查询结束日期，格式 yyyy-MM-dd") String endDate,
            @P("大区名称，例如：华东区。传 null 或空字符串表示全公司") String regionName,
            @P("返回前 N 名，默认 5，最大 20") int topN) {
        log.info("[SalesTool] getTopReps start={}, end={}, region={}, topN={}",
                startDate, endDate, regionName, topN);
        try {
            LocalDate start = validator.parseDate(startDate);
            LocalDate end = validator.parseDate(endDate);
            validator.validateDateRange(start, end);
            Long regionId = resolveRegionId(regionName);
            return analyticsService.formatRepRanking(start, end, validator.normalizeTopN(topN), regionId);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[SalesTool] getTopReps failed", e);
            return "查询销售员排名数据时出现问题，请稍后重试。";
        }
    }

    @Tool("计算各大区的销售业绩排名。适用于：哪个大区最好、大区业绩对比、各区销售额、区域排行榜。")
    public String getRegionRanking(
            @P("查询开始日期，格式 yyyy-MM-dd") String startDate,
            @P("查询结束日期，格式 yyyy-MM-dd") String endDate) {
        log.info("[SalesTool] getRegionRanking start={}, end={}", startDate, endDate);
        try {
            LocalDate start = validator.parseDate(startDate);
            LocalDate end = validator.parseDate(endDate);
            validator.validateDateRange(start, end);
            return analyticsService.formatRegionRanking(start, end);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[SalesTool] getRegionRanking failed", e);
            return "查询大区排名数据时出现问题，请稍后重试。";
        }
    }

    @Tool("计算产品销售排名，找出畅销品或滞销品。适用于：最畅销产品、Top N SKU、哪个产品卖得最好或最差。")
    public String getTopProducts(
            @P("查询开始日期，格式 yyyy-MM-dd") String startDate,
            @P("查询结束日期，格式 yyyy-MM-dd") String endDate,
            @P("返回前 N 名，负数表示查最差的 N 名") int topN) {
        log.info("[SalesTool] getTopProducts start={}, end={}, topN={}", startDate, endDate, topN);
        try {
            LocalDate start = validator.parseDate(startDate);
            LocalDate end = validator.parseDate(endDate);
            validator.validateDateRange(start, end);
            int signedTopN = validator.normalizeSignedTopN(topN);
            return analyticsService.formatProductRanking(start, end, Math.abs(signedTopN), signedTopN < 0);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[SalesTool] getTopProducts failed", e);
            return "查询产品排名数据时出现问题，请稍后重试。";
        }
    }

    private Long resolveRegionId(String regionName) {
        String normalized = validator.normalizeNullableName(regionName);
        if (normalized == null) {
            return null;
        }
        return analyticsService.findRegionIdByName(normalized)
                .orElseThrow(() -> new IllegalArgumentException("未找到大区：" + normalized));
    }
}
