package com.poc.svc.securities.service;

import com.poc.svc.securities.logging.TraceContext;
import com.poc.svc.securities.service.SecuritiesAssetService.SecuritiesAssetResponse.SecurityHolding;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

@Service
public class SecuritiesAssetService {

    private static final List<String> SECURITY_TYPES = List.of("stock", "bond", "fund", "reit");
    private static final List<String> RISK_LEVELS = List.of("LOW", "MEDIUM", "HIGH");
    private static final List<String> CURRENCIES = List.of("TWD", "USD", "JPY", "EUR");
    private static final String ERROR_INVALID_CUSTOMER = "INVALID_CUSTOMER_ID";
    private static final String ERROR_NOT_FOUND = "SECURITIES_ASSET_NOT_FOUND";
    private static final String ERROR_UNAVAILABLE = "ASSET_SOURCE_UNAVAILABLE";
    private static final String ERROR_TIMEOUT = "ASSET_SOURCE_TIMEOUT";
    private static final int MAX_RETRY = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(100);
    private static final List<String> SECURITY_NAME_PREFIXES = List.of(
            "環球",
            "精選",
            "均衡",
            "成長",
            "策略",
            "動能"
    );

    private final MeterRegistry meterRegistry;

    public SecuritiesAssetService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public SecuritiesAssetResponse getCustomerAssets(String customerId) {
        validateCustomerId(customerId);
        Timer.Sample timerSample = Timer.start(meterRegistry);
        try {
            SecuritiesAssetResponse response = fetchWithRetry(customerId);
            meterRegistry.counter("securities.asset.fetch.success").increment();
            return response;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                meterRegistry.counter("securities.asset.fetch.failure",
                        "code", ex.getReason() != null ? ex.getReason() : "UNKNOWN").increment();
            }
            throw ex;
        } finally {
            timerSample.stop(Timer.builder("securities.asset.fetch.latency")
                    .description("Time spent generating mock securities asset data")
                    .register(meterRegistry));
        }
    }

    private SecuritiesAssetResponse fetchWithRetry(String customerId) {
        int attempt = 0;
        while (true) {
            try {
                return buildResponse(customerId);
            } catch (AssetSourceUnavailableException ex) {
                attempt++;
                if (attempt >= MAX_RETRY) {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ERROR_UNAVAILABLE, ex);
                }
                sleepForRetry(attempt);
            }
        }
    }

    private SecuritiesAssetResponse buildResponse(String customerId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        simulateDownstreamIssues(customerId);
        int holdingCount = random.nextInt(1, 5);
        List<SecurityHolding> holdings = IntStream.range(0, holdingCount)
                .mapToObj(idx -> buildHolding(idx, random))
                .toList();

        BigDecimal totalMarketValue = holdings.stream()
                .map(SecurityHolding::marketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        String traceId = TraceContext.traceId();
        if (!StringUtils.hasText(traceId)) {
            traceId = TraceContext.ensureTraceId(null);
        }

        return new SecuritiesAssetResponse(customerId, holdings, totalMarketValue, "TWD", traceId);
    }

    private SecurityHolding buildHolding(int index, ThreadLocalRandom random) {
        String securityType = SECURITY_TYPES.get(random.nextInt(SECURITY_TYPES.size()));
        String symbol = switch (securityType) {
            case "stock" -> "STK-" + (100 + index);
            case "bond" -> "BND-" + (10 + index);
            case "fund" -> "FND-" + (1000 + index);
            case "reit" -> "RET-" + (200 + index);
            default -> "SEC-" + index;
        };
        BigDecimal marketValue = BigDecimal.valueOf(random.nextDouble(50_000, 1_000_000))
                .setScale(2, RoundingMode.HALF_UP);
        int quantity = random.nextInt(10, 500);
        String currency = CURRENCIES.get(random.nextInt(CURRENCIES.size()));
        String riskLevel = RISK_LEVELS.get(random.nextInt(RISK_LEVELS.size()));
        String assetName = SECURITY_NAME_PREFIXES.get(random.nextInt(SECURITY_NAME_PREFIXES.size())) + " " + symbol;
        return new SecurityHolding(securityType, symbol, assetName, quantity, marketValue, currency, riskLevel, Instant.now());
    }

    private void validateCustomerId(String customerId) {
        if (!StringUtils.hasText(customerId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ERROR_INVALID_CUSTOMER);
        }
        if (customerId.toLowerCase().startsWith("missing")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ERROR_NOT_FOUND);
        }
    }

    private void simulateDownstreamIssues(String customerId) {
        String lower = customerId.toLowerCase();
        if (lower.contains("timeout")) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, ERROR_TIMEOUT);
        }
        if (lower.contains("fail")) {
            throw new AssetSourceUnavailableException("Securities downstream unavailable");
        }
    }

    private void sleepForRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAY.multipliedBy(attempt).toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ERROR_UNAVAILABLE, interruptedException);
        }
    }

    @Schema(name = "SecuritiesAssetResponse", description = "證券資產查詢回應")
    public record SecuritiesAssetResponse(
            @Schema(description = "客戶識別碼", example = "cust-001", requiredMode = Schema.RequiredMode.REQUIRED)
            String customerId,
            @Schema(description = "持有的證券清單", requiredMode = Schema.RequiredMode.REQUIRED)
            List<SecurityHolding> securitiesAssets,
            @Schema(description = "總市值", example = "780000.00", requiredMode = Schema.RequiredMode.REQUIRED)
            BigDecimal totalMarketValue,
            @Schema(description = "市值幣別", example = "TWD", requiredMode = Schema.RequiredMode.REQUIRED)
            String currency,
            @Schema(description = "Trace ID", example = "trace-abc") String traceId
    ) {
        public SecuritiesAssetResponse {
            Objects.requireNonNull(customerId, "customerId must not be null");
            Objects.requireNonNull(securitiesAssets, "securitiesAssets must not be null");
            Objects.requireNonNull(totalMarketValue, "totalMarketValue must not be null");
            Objects.requireNonNull(currency, "currency must not be null");
        }

        public SecuritiesAssetResponse withTraceId(String traceId) {
            return new SecuritiesAssetResponse(customerId, securitiesAssets, totalMarketValue, currency, traceId);
        }

        @Schema(name = "SecurityHolding", description = "單一持有的證券")
        public record SecurityHolding(
                @Schema(description = "證券類型", example = "stock") String securityType,
                @Schema(description = "證券代碼", example = "STK-101") String symbol,
                @Schema(description = "資產名稱", example = "環球 STK-101") String assetName,
                @Schema(description = "持有數量", example = "100") Integer holdings,
                @Schema(description = "市值", example = "120000.00") BigDecimal marketValue,
                @Schema(description = "幣別", example = "USD") String currency,
                @Schema(description = "風險等級", example = "MEDIUM") String riskLevel,
                @Schema(description = "最後更新時間", example = "2025-11-14T01:13:00Z") Instant lastUpdated
        ) {
            public SecurityHolding {
                Objects.requireNonNull(securityType, "securityType must not be null");
                Objects.requireNonNull(symbol, "symbol must not be null");
                Objects.requireNonNull(assetName, "assetName must not be null");
                Objects.requireNonNull(holdings, "holdings must not be null");
                Objects.requireNonNull(marketValue, "marketValue must not be null");
                Objects.requireNonNull(currency, "currency must not be null");
                Objects.requireNonNull(riskLevel, "riskLevel must not be null");
                Objects.requireNonNull(lastUpdated, "lastUpdated must not be null");
            }
        }
    }

    private static class AssetSourceUnavailableException extends RuntimeException {
        AssetSourceUnavailableException(String message) {
            super(message);
        }
    }
}
