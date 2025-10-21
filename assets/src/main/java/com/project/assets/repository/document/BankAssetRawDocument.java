package com.project.assets.repository.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "bank_raw")
public record BankAssetRawDocument(
        @Id String id,
        String customerId,
        Map<String, Object> payload,
        BigDecimal totalBalance,
        List<CurrencyAmount> currencySummary,
        Instant fetchedAt,
        String traceId
) {
    public record CurrencyAmount(String currency, BigDecimal amount) {
    }
}
