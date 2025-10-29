package com.poc.svc.insurance.service;

import com.poc.svc.insurance.logging.TraceContext;
import com.poc.svc.insurance.service.InsuranceAssetService.InsuranceAssetResponse.InsuranceAssetItem;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
public class InsuranceAssetService {

    private static final List<String> POLICY_TYPES = List.of("life", "health", "travel", "property");
    private static final List<String> PREMIUM_STATUSES = List.of("paid", "due", "overdue");
    private static final String ERROR_INVALID_CUSTOMER = "INVALID_CUSTOMER_ID";
    private static final String ERROR_NOT_FOUND = "INSURANCE_ASSET_NOT_FOUND";
    private static final String ERROR_UNAVAILABLE = "ASSET_SOURCE_UNAVAILABLE";
    private static final String ERROR_TIMEOUT = "ASSET_SOURCE_TIMEOUT";
    private static final int MAX_RETRY = 3;
    private static final Duration RETRY_DELAY = Duration.ofMillis(100);
    private static final List<String> POLICY_NAME_PREFIXES = List.of(
            "安穩未來",
            "家庭守護",
            "生命照護",
            "頂級醫療",
            "旅遊安心",
            "家居保障"
    );

    private final MeterRegistry meterRegistry;

    public InsuranceAssetService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public InsuranceAssetResponse getCustomerAssets(String customerId) {
        validateCustomerId(customerId);
        Timer.Sample timerSample = Timer.start(meterRegistry);
        try {
            InsuranceAssetResponse response = fetchWithRetry(customerId);
            meterRegistry.counter("insurance.asset.fetch.success").increment();
            return response;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().is5xxServerError()) {
                meterRegistry.counter("insurance.asset.fetch.failure",
                        "code", ex.getReason() != null ? ex.getReason() : "UNKNOWN").increment();
            }
            throw ex;
        } finally {
            timerSample.stop(Timer.builder("insurance.asset.fetch.latency")
                    .description("Time spent generating mock insurance asset data")
                    .register(meterRegistry));
        }
    }

    private InsuranceAssetResponse fetchWithRetry(String customerId) {
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

    private InsuranceAssetResponse buildResponse(String customerId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        simulateDownstreamIssues(customerId);
        int policyCount = random.nextInt(1, 4);

        List<InsuranceAssetItem> policies = IntStream.range(0, policyCount)
                .mapToObj(idx -> buildPolicy(idx, random))
                .toList();

        BigDecimal totalCoverage = policies.stream()
                .map(InsuranceAssetItem::coverage)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        String traceId = TraceContext.traceId();
        if (!StringUtils.hasText(traceId)) {
            traceId = TraceContext.ensureTraceId(null);
        }

        return new InsuranceAssetResponse(customerId, policies, totalCoverage, "TWD", traceId);
    }

    private InsuranceAssetItem buildPolicy(int index, ThreadLocalRandom random) {
        String policyType = POLICY_TYPES.get(random.nextInt(POLICY_TYPES.size()));
        String policyNumber = policyType.substring(0, Math.min(policyType.length(), 3)).toUpperCase() + "-" + (1_000 + index);
        BigDecimal coverage = BigDecimal.valueOf(random.nextDouble(100_000, 2_000_000))
                .setScale(2, RoundingMode.HALF_UP);
        String premiumStatus = PREMIUM_STATUSES.get(random.nextInt(PREMIUM_STATUSES.size()));
        String assetName = POLICY_NAME_PREFIXES.get(random.nextInt(POLICY_NAME_PREFIXES.size())) + " 方案";
        return new InsuranceAssetItem(policyNumber, policyType, assetName, coverage, premiumStatus, "TWD", Instant.now());
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
            throw new AssetSourceUnavailableException("Insurance downstream unavailable");
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

    public record InsuranceAssetResponse(
            String customerId,
            List<InsuranceAssetItem> insuranceAssets,
            BigDecimal totalCoverage,
            String currency,
            String traceId
    ) {
        public InsuranceAssetResponse {
            Objects.requireNonNull(customerId, "customerId must not be null");
            Objects.requireNonNull(insuranceAssets, "insuranceAssets must not be null");
            Objects.requireNonNull(totalCoverage, "totalCoverage must not be null");
            Objects.requireNonNull(currency, "currency must not be null");
        }

        public InsuranceAssetResponse withTraceId(String traceId) {
            return new InsuranceAssetResponse(customerId, insuranceAssets, totalCoverage, currency, traceId);
        }

        public record InsuranceAssetItem(
                String policyNumber,
                String policyType,
                String assetName,
                BigDecimal coverage,
                String premiumStatus,
                String currency,
                Instant lastUpdated
        ) {
            public InsuranceAssetItem {
                Objects.requireNonNull(policyNumber, "policyNumber must not be null");
                Objects.requireNonNull(policyType, "policyType must not be null");
                Objects.requireNonNull(assetName, "assetName must not be null");
                Objects.requireNonNull(coverage, "coverage must not be null");
                Objects.requireNonNull(premiumStatus, "premiumStatus must not be null");
                Objects.requireNonNull(currency, "currency must not be null");
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
