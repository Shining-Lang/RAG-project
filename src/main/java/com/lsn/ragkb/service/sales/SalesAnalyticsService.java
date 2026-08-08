package com.lsn.ragkb.service.sales;

import com.lsn.ragkb.dto.sales.AnomalyDTO;
import com.lsn.ragkb.dto.sales.MonthlyTrendDTO;
import com.lsn.ragkb.dto.sales.ProductSalesDTO;
import com.lsn.ragkb.dto.sales.RegionSalesDTO;
import com.lsn.ragkb.dto.sales.RepSalesDTO;
import com.lsn.ragkb.entity.sales.Product;
import com.lsn.ragkb.entity.sales.SalesOrder;
import com.lsn.ragkb.entity.sales.SalesRegion;
import com.lsn.ragkb.entity.sales.SalesRep;
import com.lsn.ragkb.repository.sales.ProductRepository;
import com.lsn.ragkb.repository.sales.SalesOrderRepository;
import com.lsn.ragkb.repository.sales.SalesRegionRepository;
import com.lsn.ragkb.repository.sales.SalesRepRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesAnalyticsService {

    private final SalesOrderRepository orderRepository;
    private final SalesRepRepository repRepository;
    private final SalesRegionRepository regionRepository;
    private final ProductRepository productRepository;

    public String buildContext(String question) {
        LocalDateRange range = resolveRange(question);
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        List<String> blocks = new ArrayList<>();

        if (matchesAny(normalized, "top", "排名", "销售员", "冠军", "谁卖")) {
            blocks.add(formatRepRanking(range.start(), range.end(), 5, null));
        }
        if (matchesAny(normalized, "大区", "区域", "地区")) {
            blocks.add(formatRegionRanking(range.start(), range.end()));
        }
        if (matchesAny(normalized, "产品", "sku", "商品", "畅销", "滞销")) {
            blocks.add(formatProductRanking(range.start(), range.end(), 5, false));
        }
        if (matchesAny(normalized, "趋势", "月份", "环比", "走势")) {
            blocks.add(formatMonthlyTrend(null, 6));
        }
        if (matchesAny(normalized, "异常", "预警", "风险", "下滑", "暴跌", "断货")) {
            blocks.add(formatAnomalies());
        }

        if (blocks.isEmpty()) {
            blocks.add(formatSalesSummary(null, range.start(), range.end()));
            blocks.add(formatRegionRanking(range.start(), range.end()));
            blocks.add(formatAnomalies());
        }

        return String.join("\n\n", blocks);
    }

    public String formatSalesSummary(Long regionId, LocalDate start, LocalDate end) {
        BigDecimal total = orderRepository.sumAmount(regionId, start, end);
        long count = orderRepository.countCompleted(regionId, start, end);
        String scope = regionId == null ? "全公司" : getRegionName(regionId);
        return """
                【销售汇总】
                范围：%s，%s 至 %s
                成交销售额：%s
                成交订单数：%d 单
                """.formatted(scope, start, end, money(total), count).strip();
    }

    public String formatOrderList(Long repId, Long regionId, LocalDate start, LocalDate end, int limit) {
        List<SalesOrder> orders = orderRepository.findOrders(repId, regionId, start, end)
                .stream()
                .limit(limit)
                .toList();
        if (orders.isEmpty()) {
            return "该时间段暂无符合条件的订单。";
        }
        StringBuilder sb = new StringBuilder("【订单明细】\n");
        sb.append("时间：").append(start).append(" 至 ").append(end).append("\n");
        for (SalesOrder order : orders) {
            sb.append("- 订单号：").append(order.getOrderNo())
                    .append("，日期：").append(order.getOrderDate())
                    .append("，销售员：").append(getRepName(order.getRepId()))
                    .append("，客户：").append(order.getCustomerName())
                    .append("，金额：").append(money(order.getAmount()))
                    .append("，状态：").append(translateStatus(order.getStatus()))
                    .append("\n");
        }
        return sb.toString().strip();
    }

    public String formatRepRanking(LocalDate start, LocalDate end, int topN, Long regionId) {
        List<RepSalesDTO> reps = queryRepRanking(start, end).stream()
                .filter(rep -> regionId == null || regionId.equals(rep.regionId()))
                .limit(topN)
                .toList();
        if (reps.isEmpty()) {
            return "【销售员排名】该时间段暂无成交订单。";
        }
        StringBuilder sb = new StringBuilder("【销售员业绩排名】\n");
        sb.append("时间：").append(start).append(" 至 ").append(end).append("\n");
        for (int i = 0; i < reps.size(); i++) {
            RepSalesDTO rep = reps.get(i);
            sb.append(i + 1).append(". ")
                    .append(rep.repName()).append("（").append(rep.regionName()).append("）")
                    .append(" 销售额 ").append(money(rep.totalAmount()))
                    .append("，订单 ").append(rep.orderCount()).append(" 单\n");
        }
        return sb.toString().strip();
    }

    public String formatRegionRanking(LocalDate start, LocalDate end) {
        List<RegionSalesDTO> regions = queryRegionRanking(start, end);
        if (regions.isEmpty()) {
            return "【大区排名】该时间段暂无成交订单。";
        }
        BigDecimal total = regions.stream()
                .map(RegionSalesDTO::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder sb = new StringBuilder("【大区业绩排名】\n");
        sb.append("时间：").append(start).append(" 至 ").append(end).append("\n");
        for (int i = 0; i < regions.size(); i++) {
            RegionSalesDTO region = regions.get(i);
            BigDecimal ratio = total.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : region.totalAmount().multiply(BigDecimal.valueOf(100))
                    .divide(total, 1, RoundingMode.HALF_UP);
            sb.append(i + 1).append(". ")
                    .append(region.regionName())
                    .append(" 销售额 ").append(money(region.totalAmount()))
                    .append("，订单 ").append(region.orderCount()).append(" 单")
                    .append("，占比 ").append(ratio).append("%\n");
        }
        return sb.toString().strip();
    }

    public String formatProductRanking(LocalDate start, LocalDate end, int topN, boolean worst) {
        List<ProductSalesDTO> products = new ArrayList<>(queryProductRanking(start, end));
        if (products.isEmpty()) {
            return "【产品排名】该时间段暂无成交订单。";
        }
        if (worst) {
            products.sort(Comparator.comparing(ProductSalesDTO::totalAmount));
        }
        products = products.stream().limit(topN).toList();

        StringBuilder sb = new StringBuilder(worst ? "【滞销产品排名】\n" : "【产品销售排名】\n");
        sb.append("时间：").append(start).append(" 至 ").append(end).append("\n");
        for (int i = 0; i < products.size(); i++) {
            ProductSalesDTO p = products.get(i);
            sb.append(i + 1).append(". ")
                    .append(p.productName()).append(" [").append(p.skuCode()).append("]")
                    .append("，品类 ").append(p.category())
                    .append("，销售额 ").append(money(p.totalAmount()))
                    .append("，销量 ").append(p.totalQuantity()).append(" 件\n");
        }
        return sb.toString().strip();
    }

    public String formatMonthlyTrend(Long regionId, int months) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusMonths(months).withDayOfMonth(1);
        List<MonthlyTrendDTO> trend = queryMonthlyTrend(regionId, start, end);
        if (trend.isEmpty()) {
            return "【月度趋势】暂无趋势数据。";
        }
        StringBuilder sb = new StringBuilder("【月度销售趋势】\n");
        for (int i = 0; i < trend.size(); i++) {
            MonthlyTrendDTO cur = trend.get(i);
            String change = "";
            if (i > 0) {
                BigDecimal rate = growthRate(cur.totalAmount(), trend.get(i - 1).totalAmount());
                if (rate != null) {
                    change = "，环比" + (rate.compareTo(BigDecimal.ZERO) >= 0 ? "增长 " : "下降 ")
                            + rate.abs() + "%";
                }
            }
            sb.append(cur.month())
                    .append("：").append(money(cur.totalAmount()))
                    .append("，订单 ").append(cur.orderCount()).append(" 单")
                    .append(change).append("\n");
        }
        return sb.toString().strip();
    }

    public String formatGrowthComparison(LocalDate currentStart, LocalDate currentEnd,
                                         LocalDate previousStart, LocalDate previousEnd,
                                         Long regionId, String label) {
        BigDecimal current = orderRepository.sumAmount(regionId, currentStart, currentEnd);
        BigDecimal previous = orderRepository.sumAmount(regionId, previousStart, previousEnd);
        BigDecimal rate = growthRate(current, previous);
        String scope = regionId == null ? "全公司" : getRegionName(regionId);
        if (rate == null) {
            return """
                    【%s】
                    范围：%s
                    当前周期：%s 至 %s，销售额 %s
                    对比周期：%s 至 %s，销售额 %s
                    对比周期无销售额，无法计算增长率。
                    """.formatted(label, scope, currentStart, currentEnd, money(current),
                    previousStart, previousEnd, money(previous)).strip();
        }
        return """
                【%s】
                范围：%s
                当前周期：%s 至 %s，销售额 %s
                对比周期：%s 至 %s，销售额 %s
                变化：%s %s%%，金额%s %s
                """.formatted(label, scope, currentStart, currentEnd, money(current),
                previousStart, previousEnd, money(previous),
                rate.compareTo(BigDecimal.ZERO) >= 0 ? "增长" : "下降",
                rate.abs(),
                rate.compareTo(BigDecimal.ZERO) >= 0 ? "增加" : "减少",
                money(current.subtract(previous).abs())).strip();
    }

    public String formatAnomalies() {
        List<AnomalyDTO> anomalies = detectAnomalies();
        if (anomalies.isEmpty()) {
            return "【异常检测】当前未发现明显销售异常。";
        }
        StringBuilder sb = new StringBuilder("【异常检测】\n");
        for (AnomalyDTO anomaly : anomalies) {
            sb.append("- [").append(anomaly.severity()).append("] ")
                    .append(anomaly.type()).append("：").append(anomaly.subject()).append("\n")
                    .append("  说明：").append(anomaly.description()).append("\n")
                    .append("  建议：").append(anomaly.suggestion()).append("\n");
        }
        return sb.toString().strip();
    }

    public List<MonthlyTrendDTO> queryMonthlyTrend(Long regionId, LocalDate start, LocalDate end) {
        return orderRepository.findMonthlyTrend(regionId, start, end).stream()
                .map(row -> new MonthlyTrendDTO(
                        row[0].toString(),
                        new BigDecimal(row[1].toString()),
                        ((Number) row[2]).intValue()))
                .toList();
    }

    public List<RepSalesDTO> queryRepRanking(LocalDate start, LocalDate end) {
        Map<Long, SalesRep> reps = repRepository.findAll().stream()
                .collect(Collectors.toMap(SalesRep::getId, r -> r));
        Map<Long, String> regions = regionRepository.findAll().stream()
                .collect(Collectors.toMap(SalesRegion::getId, SalesRegion::getName));

        return orderRepository.findRepRanking(start, end).stream()
                .map(row -> {
                    Long repId = ((Number) row[0]).longValue();
                    SalesRep rep = reps.get(repId);
                    if (rep == null) return null;
                    return new RepSalesDTO(
                            repId,
                            rep.getName(),
                            rep.getRegionId(),
                            regions.getOrDefault(rep.getRegionId(), "未知大区"),
                            new BigDecimal(row[1].toString()),
                            ((Number) row[2]).intValue());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public List<RegionSalesDTO> queryRegionRanking(LocalDate start, LocalDate end) {
        Map<Long, String> regions = regionRepository.findAll().stream()
                .collect(Collectors.toMap(SalesRegion::getId, SalesRegion::getName));
        return orderRepository.findRegionRanking(start, end).stream()
                .map(row -> {
                    Long regionId = ((Number) row[0]).longValue();
                    return new RegionSalesDTO(
                            regionId,
                            regions.getOrDefault(regionId, "未知大区"),
                            new BigDecimal(row[1].toString()),
                            ((Number) row[2]).intValue(),
                            new BigDecimal(row[3].toString()));
                })
                .toList();
    }

    public List<ProductSalesDTO> queryProductRanking(LocalDate start, LocalDate end) {
        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, p -> p));
        return orderRepository.findProductRanking(start, end).stream()
                .map(row -> {
                    Long productId = ((Number) row[0]).longValue();
                    Product product = products.get(productId);
                    if (product == null) return null;
                    return new ProductSalesDTO(
                            productId,
                            product.getSkuCode(),
                            product.getName(),
                            product.getCategory(),
                            new BigDecimal(row[1].toString()),
                            ((Number) row[2]).intValue());
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public Optional<Long> findRegionIdByName(String regionName) {
        if (regionName == null || regionName.isBlank()) {
            return Optional.empty();
        }
        return regionRepository.findAll().stream()
                .filter(region -> regionName.trim().equals(region.getName()))
                .map(SalesRegion::getId)
                .findFirst();
    }

    public Optional<Long> findRepIdByName(String repName) {
        if (repName == null || repName.isBlank()) {
            return Optional.empty();
        }
        return repRepository.findAll().stream()
                .filter(rep -> repName.trim().equals(rep.getName()))
                .map(SalesRep::getId)
                .findFirst();
    }

    public LocalDateRange resolveRange(String question) {
        LocalDate today = LocalDate.now();
        String q = question == null ? "" : question;
        if (q.contains("本月")) {
            return new LocalDateRange(today.withDayOfMonth(1), today);
        }
        if (q.contains("本季度") || q.contains("季度")) {
            int firstMonth = ((today.getMonthValue() - 1) / 3) * 3 + 1;
            LocalDate start = LocalDate.of(today.getYear(), firstMonth, 1);
            return new LocalDateRange(start, today);
        }
        if (q.contains("今年")) {
            return new LocalDateRange(LocalDate.of(today.getYear(), 1, 1), today);
        }
        if (q.contains("近30天") || q.contains("最近30天")) {
            return new LocalDateRange(today.minusDays(30), today);
        }
        return new LocalDateRange(today.minusMonths(3), today);
    }

    public BigDecimal growthRate(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }

    private List<AnomalyDTO> detectAnomalies() {
        List<AnomalyDTO> result = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDate recentStart = today.minusDays(14);
        LocalDate recentEnd = today;
        LocalDate baseStart = today.minusDays(42);
        LocalDate baseEnd = today.minusDays(15);

        for (SalesRegion region : regionRepository.findAll()) {
            long recent = orderRepository.countCompletedByRegion(region.getId(), recentStart, recentEnd);
            long base = orderRepository.countCompletedByRegion(region.getId(), baseStart, baseEnd);
            double baseAvg = base / 2.0;
            if (baseAvg >= 2) {
                double drop = (baseAvg - recent) / baseAvg;
                if (drop > 0.3) {
                    result.add(new AnomalyDTO(
                            "大区订单量骤降",
                            drop > 0.6 ? "HIGH" : "MEDIUM",
                            region.getName(),
                            "近 14 天订单 " + recent + " 单，过去 4 周折算均值 "
                                    + String.format(Locale.US, "%.1f", baseAvg) + " 单/两周，下降 "
                                    + String.format(Locale.US, "%.0f", drop * 100) + "%",
                            "联系大区负责人复盘线索、库存、渠道活动与竞品冲击。"));
                }
            }
        }

        for (Product product : productRepository.findByStatus("ACTIVE")) {
            LocalDate last = orderRepository.findLastOrderDateByProduct(product.getId());
            if (last == null) continue;
            long days = ChronoUnit.DAYS.between(last, today);
            if (days >= 14) {
                result.add(new AnomalyDTO(
                        "产品连续零销售",
                        days >= 30 ? "HIGH" : "MEDIUM",
                        product.getName() + " [" + product.getSkuCode() + "]",
                        "已连续 " + days + " 天无成交，最近成交日期为 " + last,
                        "检查库存、价格竞争力、页面曝光和渠道补货状态。"));
            }
        }

        LocalDate refundStart = today.minusDays(90);
        for (Object[] row : orderRepository.findRefundRateByRep(refundStart, today)) {
            Long repId = ((Number) row[0]).longValue();
            long refunded = ((Number) row[1]).longValue();
            long total = ((Number) row[2]).longValue();
            if (total < 3) continue;
            double rate = (double) refunded / total;
            if (rate > 0.15) {
                result.add(new AnomalyDTO(
                        "销售员退单率偏高",
                        rate > 0.3 ? "HIGH" : "MEDIUM",
                        getRepName(repId),
                        "近 90 天退单率 " + String.format(Locale.US, "%.0f", rate * 100) + "%（"
                                + refunded + "/" + total + " 单）",
                        "抽查成交承诺、客户画像匹配度和售后原因，必要时调整激励口径。"));
            }
        }

        return result.stream()
                .sorted(Comparator.comparingInt(a -> "HIGH".equals(a.severity()) ? 0 : 1))
                .toList();
    }

    private boolean matchesAny(String text, String... keys) {
        for (String key : keys) {
            if (text.contains(key.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String getRepName(Long repId) {
        return repRepository.findById(repId).map(SalesRep::getName).orElse("未知销售员");
    }

    private String getRegionName(Long regionId) {
        return regionRepository.findById(regionId).map(SalesRegion::getName).orElse("未知大区");
    }

    public String money(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return "¥" + String.format(Locale.US, "%,.0f", value);
    }

    private String translateStatus(String status) {
        return switch (status) {
            case "COMPLETED" -> "已完成";
            case "REFUNDED" -> "已退款";
            case "CANCELLED" -> "已取消";
            default -> status;
        };
    }

    public record LocalDateRange(LocalDate start, LocalDate end) {}
}
