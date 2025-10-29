package com.poc.svc.assets.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Document(collection = "asset_staging")
public record AssetStagingDocument(
        @Id String id,
        String customerId,
        String baseCurrency,
        List<Component> components,
        BigDecimal totalAssetValue,
        List<CurrencyAmount> currencyBreakdown,
        String aggregationStatus,
        Instant aggregatedAt,
        String traceId
) {
    public record Component(
            String source,
            String status,
            BigDecimal amountInBase,
            String sourceCurrency,
            BigDecimal exchangeRate,
            String rawTraceId,
            Instant fetchedAt,
            List<Map<String, Object>> assetDetails,
            String payloadRefId
    ) {
    }

    public record CurrencyAmount(String currency, BigDecimal amount) {
    }
}
