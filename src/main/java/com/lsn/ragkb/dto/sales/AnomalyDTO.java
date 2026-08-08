package com.lsn.ragkb.dto.sales;

public record AnomalyDTO(
        String type,
        String severity,
        String subject,
        String description,
        String suggestion
) {}
