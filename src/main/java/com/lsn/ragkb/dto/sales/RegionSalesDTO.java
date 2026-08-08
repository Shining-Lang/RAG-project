package com.lsn.ragkb.dto.sales;

import java.math.BigDecimal;

public record RegionSalesDTO(
        Long regionId,
        String regionName,
        BigDecimal totalAmount,
        Integer orderCount,
        BigDecimal totalProfit
) {}
