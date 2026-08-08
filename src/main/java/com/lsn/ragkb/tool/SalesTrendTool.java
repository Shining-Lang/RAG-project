package com.lsn.ragkb.tool;

import com.lsn.ragkb.security.ToolInputValidator;
import com.lsn.ragkb.service.sales.SalesAnalyticsService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class SalesTrendTool {

    private final SalesAnalyticsService analyticsService;
    private final ToolInputValidator validator;

    @Tool("计算销售环比增长率，即当前周期与上一周期对比。适用于：本月比上月、本季比上季、最近两期对比。")
    public String calcPeriodOverPeriod(
            @P("当前周期开始日期，格式 yyyy-MM-dd") String currentStart,
            @P("当前周期结束日期，格式 yyyy-MM-dd") String currentEnd,
            @P("对比周期开始日期，格式 yyyy-MM-dd。传 null 则自动计算上一等长周期") String previousStart,
            @P("对比周期结束日期，格式 yyyy-MM-dd。传 null 则自动计算上一等长周期") String previousEnd,
            @P("大区名称，例如：华东区。传 null 表示全公司") String regionName) {
        log.info("[SalesTool] calcPeriodOverPeriod current={}/{}, previous={}/{}, region={}",
                currentStart, currentEnd, previousStart, previousEnd, regionName);
        try {
            LocalDate cStart = validator.parseDate(currentStart);
            LocalDate cEnd = validator.parseDate(currentEnd);
            validator.validateDateRange(cStart, cEnd);

            LocalDate pStart;
            LocalDate pEnd;
            if (validator.normalizeNullableName(previousStart) == null
                    && validator.normalizeNullableName(previousEnd) == null) {
                long days = ChronoUnit.DAYS.between(cStart, cEnd) + 1;
                pEnd = cStart.minusDays(1);
                pStart = pEnd.minusDays(days - 1);
            } else {
                pStart = validator.parseDate(previousStart);
                pEnd = validator.parseDate(previousEnd);
                validator.validateDateRange(pStart, pEnd);
            }

            return analyticsService.formatGrowthComparison(cStart, cEnd, pStart, pEnd,
                    resolveRegionId(regionName), "环比分析");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[SalesTool] calcPeriodOverPeriod failed", e);
            return "计算环比数据时出现问题，请稍后重试。";
        }
    }

    @Tool("计算销售同比增长率，即与去年同期对比。适用于：今年和去年同期比、同比增长率、年度对比。")
    public String calcYearOverYear(
            @P("查询开始日期，格式 yyyy-MM-dd") String startDate,
            @P("查询结束日期，格式 yyyy-MM-dd") String endDate,
            @P("大区名称，例如：华东区。传 null 表示全公司") String regionName) {
        log.info("[SalesTool] calcYearOverYear start={}, end={}, region={}", startDate, endDate, regionName);
        try {
            LocalDate start = validator.parseDate(startDate);
            LocalDate end = validator.parseDate(endDate);
            validator.validateDateRange(start, end);
            return analyticsService.formatGrowthComparison(start, end, start.minusYears(1), end.minusYears(1),
                    resolveRegionId(regionName), "同比分析");
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[SalesTool] calcYearOverYear failed", e);
            return "计算同比数据时出现问题，请稍后重试。";
        }
    }

    @Tool("获取近 N 个月的月度销售趋势数据。适用于：最近几个月趋势、月度变化、销售走势、画折线图前的数据准备。")
    public String getMonthlyTrend(
            @P("查看最近多少个月，例如 6 表示近 6 个月，最大 24") int months,
            @P("大区名称，例如：华东区。传 null 表示全公司") String regionName) {
        log.info("[SalesTool] getMonthlyTrend months={}, region={}", months, regionName);
        try {
            return analyticsService.formatMonthlyTrend(resolveRegionId(regionName), validator.normalizeMonths(months));
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[SalesTool] getMonthlyTrend failed", e);
            return "获取趋势数据时出现问题，请稍后重试。";
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
