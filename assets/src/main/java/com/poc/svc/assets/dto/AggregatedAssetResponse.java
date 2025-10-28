package com.poc.svc.assets.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record AggregatedAssetResponse(
        String customerId,
        String baseCurrency,
        BigDecimal totalAssetValue,
        List<CurrencyAmount> currencyBreakdown,
        List<Component> components,
        AggregationStatus aggregationStatus,
        Instant aggregatedAt,
        String traceId
) {

    public AggregatedAssetResponse {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
        Objects.requireNonNull(totalAssetValue, "totalAssetValue must not be null");
        Objects.requireNonNull(currencyBreakdown, "currencyBreakdown must not be null");
        Objects.requireNonNull(components, "components must not be null");
        Objects.requireNonNull(aggregationStatus, "aggregationStatus must not be null");
        Objects.requireNonNull(aggregatedAt, "aggregatedAt must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
    }

    public enum AggregationStatus {
        COMPLETED,
        PARTIAL,
        FAILED
    }

    public record CurrencyAmount(String currency, BigDecimal amount) {
        public CurrencyAmount {
            Objects.requireNonNull(currency, "currency must not be null");
            Objects.requireNonNull(amount, "amount must not be null");
        }
    }

    public record Component(
            AssetSourceType source,
            AssetComponentStatus status,
            BigDecimal amountInBase,
            String sourceCurrency,
            BigDecimal exchangeRate,
            String rawTraceId,
            Instant fetchedAt,
            String payloadRefId
    ) {
        public Component {
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(amountInBase, "amountInBase must not be null");
            Objects.requireNonNull(sourceCurrency, "sourceCurrency must not be null");
            Objects.requireNonNull(exchangeRate, "exchangeRate must not be null");
            Objects.requireNonNull(rawTraceId, "rawTraceId must not be null");
            Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
            if (payloadRefId != null && payloadRefId.isBlank()) {
                payloadRefId = null;
            }
            if (status == AssetComponentStatus.SUCCESS && payloadRefId == null) {
                throw new IllegalArgumentException("payloadRefId must be present when status is SUCCESS");
            }
        }
    }
}
