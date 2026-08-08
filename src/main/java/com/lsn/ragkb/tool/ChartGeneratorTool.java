package com.lsn.ragkb.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lsn.ragkb.dto.sales.MonthlyTrendDTO;
import com.lsn.ragkb.dto.sales.ProductSalesDTO;
import com.lsn.ragkb.dto.sales.RegionSalesDTO;
import com.lsn.ragkb.dto.sales.RepSalesDTO;
import com.lsn.ragkb.security.ToolInputValidator;
import com.lsn.ragkb.service.sales.SalesAnalyticsService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChartGeneratorTool {

    private final SalesAnalyticsService analyticsService;
    private final ToolInputValidator validator;
    private final ObjectMapper objectMapper;

    @Tool("生成销售趋势折线图的 ECharts JSON。适用于：折线图、趋势图、月度变化图。")
    public String generateLineChart(
            @P("最近多少个月的数据，例如 6 表示近 6 个月，最大 24") int months,
            @P("大区名称，例如：华东区。传 null 表示全公司") String regionName,
            @P("图表标题") String title) {
        log.info("[SalesTool] generateLineChart months={}, region={}, title={}", months, regionName, title);
        try {
            int normalizedMonths = validator.normalizeMonths(months);
            Long regionId = resolveRegionId(regionName);
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusMonths(normalizedMonths).withDayOfMonth(1);
            List<MonthlyTrendDTO> data = analyticsService.queryMonthlyTrend(regionId, start, end);
            if (data.isEmpty()) {
                return "暂无数据，无法生成图表。";
            }
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("title", Map.of("text", blankToDefault(title, "销售趋势")));
            option.put("tooltip", Map.of("trigger", "axis"));
            option.put("xAxis", Map.of("type", "category", "data", data.stream().map(MonthlyTrendDTO::month).toList()));
            option.put("yAxis", Map.of("type", "value", "name", "销售额（元）"));
            option.put("series", List.of(Map.of(
                    "type", "line",
                    "smooth", true,
                    "name", "销售额",
                    "data", data.stream().map(d -> d.totalAmount().longValue()).toList(),
                    "itemStyle", Map.of("color", "#5470c6")
            )));
            return "CHART_JSON:" + objectMapper.writeValueAsString(option);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[SalesTool] generateLineChart failed", e);
            return "生成折线图数据时出现问题，请稍后重试。";
        }
    }

    @Tool("生成大区或销售员销售额对比柱状图 ECharts JSON。适用于：柱状图、对比图、排行榜图。")
    public String generateBarChart(
            @P("对比维度：region（大区）或 rep（销售员）") String dimension,
            @P("查询开始日期，格式 yyyy-MM-dd") String startDate,
            @P("查询结束日期，格式 yyyy-MM-dd") String endDate,
            @P("图表标题") String title) {
        log.info("[SalesTool] generateBarChart dim={}, start={}, end={}", dimension, startDate, endDate);
        try {
            String dim = validator.validateChartDimension(dimension);
            if (!"region".equals(dim) && !"rep".equals(dim)) {
                return "柱状图维度只支持 region 或 rep。";
            }
            LocalDate start = validator.parseDate(startDate);
            LocalDate end = validator.parseDate(endDate);
            validator.validateDateRange(start, end);

            List<String> names;
            List<Long> values;
            if ("region".equals(dim)) {
                List<RegionSalesDTO> regions = analyticsService.queryRegionRanking(start, end);
                names = regions.stream().map(RegionSalesDTO::regionName).toList();
                values = regions.stream().map(r -> r.totalAmount().longValue()).toList();
            } else {
                List<RepSalesDTO> reps = analyticsService.queryRepRanking(start, end).stream().limit(10).toList();
                names = reps.stream().map(RepSalesDTO::repName).toList();
                values = reps.stream().map(r -> r.totalAmount().longValue()).toList();
            }
            if (names.isEmpty()) {
                return "暂无数据，无法生成图表。";
            }
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("title", Map.of("text", blankToDefault(title, "销售对比")));
            option.put("tooltip", Map.of("trigger", "axis"));
            option.put("xAxis", Map.of("type", "category", "data", names, "axisLabel", Map.of("rotate", 30)));
            option.put("yAxis", Map.of("type", "value", "name", "销售额（元）"));
            option.put("series", List.of(Map.of("type", "bar", "data", values,
                    "itemStyle", Map.of("color", "#91cc75"))));
            return "CHART_JSON:" + objectMapper.writeValueAsString(option);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[SalesTool] generateBarChart failed", e);
            return "生成柱状图数据时出现问题，请稍后重试。";
        }
    }

    @Tool("生成销售占比饼图 ECharts JSON。适用于：饼图、各部分占比、份额分布。")
    public String generatePieChart(
            @P("饼图维度：region（大区占比）或 category（品类占比）") String dimension,
            @P("查询开始日期，格式 yyyy-MM-dd") String startDate,
            @P("查询结束日期，格式 yyyy-MM-dd") String endDate,
            @P("图表标题") String title) {
        log.info("[SalesTool] generatePieChart dim={}, start={}, end={}", dimension, startDate, endDate);
        try {
            String dim = validator.validateChartDimension(dimension);
            if (!"region".equals(dim) && !"category".equals(dim)) {
                return "饼图维度只支持 region 或 category。";
            }
            LocalDate start = validator.parseDate(startDate);
            LocalDate end = validator.parseDate(endDate);
            validator.validateDateRange(start, end);

            List<Map<String, Object>> data;
            if ("region".equals(dim)) {
                data = analyticsService.queryRegionRanking(start, end).stream()
                        .map(r -> pieItem(r.regionName(), r.totalAmount()))
                        .toList();
            } else {
                Map<String, BigDecimal> categoryMap = new LinkedHashMap<>();
                for (ProductSalesDTO product : analyticsService.queryProductRanking(start, end)) {
                    categoryMap.merge(product.category(), product.totalAmount(), BigDecimal::add);
                }
                data = categoryMap.entrySet().stream()
                        .map(e -> pieItem(e.getKey(), e.getValue()))
                        .toList();
            }
            if (data.isEmpty()) {
                return "暂无数据，无法生成图表。";
            }
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("title", Map.of("text", blankToDefault(title, "销售占比"), "left", "center"));
            option.put("tooltip", Map.of("trigger", "item", "formatter", "{b}: {c} ({d}%)"));
            option.put("legend", Map.of("orient", "vertical", "left", "left"));
            option.put("series", List.of(Map.of(
                    "type", "pie",
                    "radius", "55%",
                    "data", data
            )));
            return "CHART_JSON:" + objectMapper.writeValueAsString(option);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        } catch (Exception e) {
            log.error("[SalesTool] generatePieChart failed", e);
            return "生成饼图数据时出现问题，请稍后重试。";
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

    private Map<String, Object> pieItem(String name, BigDecimal value) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("value", value.longValue());
        return item;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
