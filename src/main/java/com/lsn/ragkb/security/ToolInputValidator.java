package com.lsn.ragkb.security;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ToolInputValidator {

    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Set<String> VALID_CHART_DIMENSIONS = Set.of("region", "rep", "category");

    public LocalDate parseDate(String dateStr) {
        if (dateStr == null || !DATE_PATTERN.matcher(dateStr).matches()) {
            throw new IllegalArgumentException("日期格式错误，请使用 yyyy-MM-dd。");
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期不存在：" + dateStr);
        }
    }

    public void validateDateRange(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("开始日期和结束日期不能为空。");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("开始日期不能晚于结束日期。");
        }
    }

    public String normalizeNullableName(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    public int normalizeTopN(int topN) {
        return Math.min(Math.max(topN, 1), 20);
    }

    public int normalizeSignedTopN(int topN) {
        int sign = topN < 0 ? -1 : 1;
        int normalized = Math.min(Math.max(Math.abs(topN), 1), 20);
        return sign * normalized;
    }

    public int normalizeLimit(int limit) {
        return Math.min(Math.max(limit, 1), 50);
    }

    public int normalizeMonths(int months) {
        return Math.min(Math.max(months, 1), 24);
    }

    public String validateChartDimension(String dimension) {
        if (dimension == null || !VALID_CHART_DIMENSIONS.contains(dimension)) {
            throw new IllegalArgumentException("无效维度：" + dimension + "，有效值为 region/rep/category。");
        }
        return dimension;
    }
}
