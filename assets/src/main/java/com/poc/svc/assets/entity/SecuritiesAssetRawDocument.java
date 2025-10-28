package com.poc.svc.assets.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

@Document(collection = "securities_raw")
public record SecuritiesAssetRawDocument(
        @Id String id,
        String customerId,
        Map<String, Object> payload,
        BigDecimal totalMarketValue,
        Integer holdingsCount,
        Instant fetchedAt,
        String traceId
) {
}
