package com.lsn.ragkb.dto.sales;

import java.math.BigDecimal;

public record MonthlyTrendDTO(
        String month,
        BigDecimal totalAmount,
        Integer orderCount
) {}
