package com.poc.svc.assets.service;

import com.poc.svc.assets.service.AssetSourceClient;
import com.poc.svc.assets.exception.AssetSourceMissingException;
import com.poc.svc.assets.dto.AssetComponentStatus;
import com.poc.svc.assets.dto.AssetSourceType;
import com.poc.svc.assets.entity.BankAssetRawDocument;
import com.poc.svc.assets.entity.InsuranceAssetRawDocument;
import com.poc.svc.assets.entity.SecuritiesAssetRawDocument;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AssetAggregationCoordinator {

    private static final Logger log = LoggerFactory.getLogger(AssetAggregationCoordinator.class);

    private final AssetSourceClient assetSourceClient;
    private final BankAssetWriter bankAssetWriter;
    private final SecuritiesAssetWriter securitiesAssetWriter;
    private final InsuranceAssetWriter insuranceAssetWriter;
    private final MeterRegistry meterRegistry;

    public AssetAggregationCoordinator(
            AssetSourceClient assetSourceClient,
            BankAssetWriter bankAssetWriter,
            SecuritiesAssetWriter securitiesAssetWriter,
            InsuranceAssetWriter insuranceAssetWriter,
            MeterRegistry meterRegistry
    ) {
        this.assetSourceClient = assetSourceClient;
        this.bankAssetWriter = bankAssetWriter;
        this.securitiesAssetWriter = securitiesAssetWriter;
        this.insuranceAssetWriter = insuranceAssetWriter;
        this.meterRegistry = meterRegistry;
    }

    public ExecutionSummary coordinate(String customerId, String traceId, Duration timeout) {
        Objects.requireNonNull(customerId, "customerId must not be null");
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");

        Map<AssetSourceType, SourceOutcome> outcomes = new ConcurrentHashMap<>();

        CompletableFuture<SourceOutcome> bankFuture = handleBank(customerId, traceId, timeout);
        CompletableFuture<SourceOutcome> securitiesFuture = handleSecurities(customerId, traceId, timeout);
        CompletableFuture<SourceOutcome> insuranceFuture = handleInsurance(customerId, traceId, timeout);

        CompletableFuture.allOf(bankFuture, securitiesFuture, insuranceFuture).join();

        SourceOutcome bankOutcome = bankFuture.join();
        SourceOutcome securitiesOutcome = securitiesFuture.join();
        SourceOutcome insuranceOutcome = insuranceFuture.join();

        outcomes.put(AssetSourceType.BANK, bankOutcome);
        outcomes.put(AssetSourceType.SECURITIES, securitiesOutcome);
        outcomes.put(AssetSourceType.INSURANCE, insuranceOutcome);

        return new ExecutionSummary(outcomes);
    }

    private CompletableFuture<SourceOutcome> handleBank(String customerId, String traceId, Duration timeout) {
        return assetSourceClient.fetchBankAssets(customerId, traceId)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        return handleException(AssetSourceType.BANK, throwable, traceId);
                    }
                    String rawTraceId = result.traceId();
                    try {
                        BankAssetRawDocument document = bankAssetWriter.write(new BankAssetWriter.BankAssetWriteRequest(
                                customerId,
                                result.payload(),
                                result.totalBalance(),
                                result.currencySummary(),
                                result.fetchedAt(),
                                rawTraceId
                        ));
                        meterRegistry.counter("asset.aggregation.raw.write", "source", AssetSourceType.BANK.name(), "status", "SUCCESS").increment();
                        log.info("TraceId={} source=BANK rawWriteId={} status=SUCCESS", traceId, document.id());
                        List<Map<String, Object>> assetDetails = extractAssetDetails(result.payload(), "bankAssets");
                        return SourceOutcome.success(
                                AssetSourceType.BANK,
                                result.totalBalance(),
                                result.currency(),
                                result.fetchedAt(),
                                rawTraceId,
                                document.id(),
                                result.payload(),
                                result.currencySummary(),
                                assetDetails
                        );
                    } catch (DataAccessException ex) {
                        return handleWriteFailure(
                                AssetSourceType.BANK,
                                traceId,
                                rawTraceId,
                                ex
                        );
                    }
                });
    }

    private CompletableFuture<SourceOutcome> handleSecurities(String customerId, String traceId, Duration timeout) {
        return assetSourceClient.fetchSecuritiesAssets(customerId, traceId)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        return handleException(AssetSourceType.SECURITIES, throwable, traceId);
                    }
                    String rawTraceId = result.traceId();
                    try {
                        SecuritiesAssetRawDocument document = securitiesAssetWriter.write(new SecuritiesAssetWriter.SecuritiesAssetWriteRequest(
                                customerId,
                                result.payload(),
                                result.totalMarketValue(),
                                result.holdingsCount(),
                                result.fetchedAt(),
                                rawTraceId
                        ));
                        meterRegistry.counter("asset.aggregation.raw.write", "source", AssetSourceType.SECURITIES.name(), "status", "SUCCESS").increment();
                        log.info("TraceId={} source=SECURITIES rawWriteId={} status=SUCCESS", traceId, document.id());
                        List<Map<String, Object>> assetDetails = extractAssetDetails(result.payload(), "securitiesAssets");
                        return SourceOutcome.success(
                                AssetSourceType.SECURITIES,
                                result.totalMarketValue(),
                                result.currency(),
                                result.fetchedAt(),
                                rawTraceId,
                                document.id(),
                                result.payload(),
                                List.of(),
                                assetDetails
                        );
                    } catch (DataAccessException ex) {
                        return handleWriteFailure(
                                AssetSourceType.SECURITIES,
                                traceId,
                                rawTraceId,
                                ex
                        );
                    }
                });
    }

    private CompletableFuture<SourceOutcome> handleInsurance(String customerId, String traceId, Duration timeout) {
        return assetSourceClient.fetchInsuranceAssets(customerId, traceId)
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .handle((result, throwable) -> {
                    if (throwable != null) {
                        return handleException(AssetSourceType.INSURANCE, throwable, traceId);
                    }
                    String rawTraceId = result.traceId();
                    try {
                        InsuranceAssetRawDocument document = insuranceAssetWriter.write(new InsuranceAssetWriter.InsuranceAssetWriteRequest(
                                customerId,
                                result.payload(),
                                result.totalCoverage(),
                                result.policiesCount(),
                                result.fetchedAt(),
                                rawTraceId
                        ));
                        meterRegistry.counter("asset.aggregation.raw.write", "source", AssetSourceType.INSURANCE.name(), "status", "SUCCESS").increment();
                        log.info("TraceId={} source=INSURANCE rawWriteId={} status=SUCCESS", traceId, document.id());
                        List<Map<String, Object>> assetDetails = extractAssetDetails(result.payload(), "insuranceAssets");
                        return SourceOutcome.success(
                                AssetSourceType.INSURANCE,
                                result.totalCoverage(),
                                result.currency(),
                                result.fetchedAt(),
                                rawTraceId,
                                document.id(),
                                result.payload(),
                                List.of(),
                                assetDetails
                        );
                    } catch (DataAccessException ex) {
                        return handleWriteFailure(
                                AssetSourceType.INSURANCE,
                                traceId,
                                rawTraceId,
                                ex
                        );
                    }
                });
    }

    private SourceOutcome handleException(AssetSourceType source, Throwable throwable, String traceId) {
        Throwable cause = unwrap(throwable);
        if (cause instanceof TimeoutException) {
            meterRegistry.counter("asset.aggregation.raw.write", "source", source.name(), "status", "TIMEOUT").increment();
            log.warn("TraceId={} source={} status=TIMEOUT", traceId, source);
            return SourceOutcome.timeout(source, traceId);
        }
        if (cause instanceof AssetSourceMissingException) {
            meterRegistry.counter("asset.aggregation.raw.write", "source", source.name(), "status", "MISSING").increment();
            log.info("TraceId={} source={} status=MISSING reason={}", traceId, source, cause.getMessage());
            return SourceOutcome.missing(source, traceId);
        }
        meterRegistry.counter("asset.aggregation.raw.write", "source", source.name(), "status", "FAILED").increment();
        log.error("TraceId={} source={} status=FAILED reason={}", traceId, source, cause.getMessage());
        return SourceOutcome.failed(source, traceId, cause);
    }

    private SourceOutcome handleWriteFailure(
            AssetSourceType source,
            String aggregationTraceId,
            String rawTraceId,
            DataAccessException ex
    ) {
        meterRegistry.counter("asset.aggregation.raw.write", "source", source.name(), "status", "FAILED").increment();
        log.error("TraceId={} source={} rawWrite status=FAILED reason={}", aggregationTraceId, source, ex.getMessage(), ex);
        String effectiveTraceId = (rawTraceId == null || rawTraceId.isBlank()) ? aggregationTraceId : rawTraceId;
        return SourceOutcome.failed(source, effectiveTraceId, ex);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException completionException && completionException.getCause() != null) {
            current = completionException.getCause();
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractAssetDetails(Map<String, Object> payload, String key) {
        if (payload == null) {
            return List.of();
        }
        Object raw = payload.get(key);
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }
        return rawList.stream()
                .filter(Map.class::isInstance)
                .map(item -> Map.copyOf((Map<String, Object>) item))
                .toList();
    }

    public record ExecutionSummary(Map<AssetSourceType, SourceOutcome> outcomes) {

        public ExecutionSummary {
            EnumMap<AssetSourceType, SourceOutcome> map = new EnumMap<>(AssetSourceType.class);
            map.putAll(outcomes);
            outcomes = map;
        }

        public SourceOutcome outcome(AssetSourceType source) {
            return outcomes.get(source);
        }

        public boolean hasFailures() {
            return outcomes.values().stream().anyMatch(SourceOutcome::isFailure);
        }

        public List<AssetSourceType> failedSources() {
            return outcomes.values().stream()
                    .filter(SourceOutcome::isFailure)
                    .map(SourceOutcome::source)
                    .toList();
        }

        public boolean hasMissingData() {
            return outcomes.values().stream().anyMatch(outcome -> outcome.status() == AssetComponentStatus.MISSING);
        }
    }

    public record SourceOutcome(
            AssetSourceType source,
            AssetComponentStatus status,
            BigDecimal amount,
            String currency,
            Instant fetchedAt,
            String rawTraceId,
            String payloadRefId,
            Map<String, Object> payload,
            List<BankAssetWriter.BankAssetWriteRequest.CurrencyAmount> currencySummary,
            List<Map<String, Object>> assetDetails,
            Throwable error
    ) {
        public SourceOutcome {
            payload = payload == null ? Map.of() : Map.copyOf(payload);
            currencySummary = currencySummary == null ? List.of() : List.copyOf(currencySummary);
            assetDetails = assetDetails == null ? List.of() : assetDetails.stream()
                    .map(Map::copyOf)
                    .toList();
        }

        public static SourceOutcome success(
                AssetSourceType source,
                BigDecimal amount,
                String currency,
                Instant fetchedAt,
                String rawTraceId,
                String payloadRefId,
                Map<String, Object> payload,
                List<BankAssetWriter.BankAssetWriteRequest.CurrencyAmount> currencySummary,
                List<Map<String, Object>> assetDetails
        ) {
            return new SourceOutcome(
                    source,
                    AssetComponentStatus.SUCCESS,
                    amount,
                    currency,
                    fetchedAt,
                    rawTraceId,
                    payloadRefId,
                    payload,
                    currencySummary,
                    assetDetails,
                    null
            );
        }

        public static SourceOutcome missing(AssetSourceType source, String traceId) {
            return new SourceOutcome(
                    source,
                    AssetComponentStatus.MISSING,
                    BigDecimal.ZERO,
                    null,
                    Instant.now(),
                    traceId,
                    null,
                    Map.of(),
                    List.of(),
                    List.of(),
                    null
            );
        }

        public static SourceOutcome failed(AssetSourceType source, String traceId, Throwable error) {
            return new SourceOutcome(
                    source,
                    AssetComponentStatus.FAILED,
                    BigDecimal.ZERO,
                    null,
                    Instant.now(),
                    traceId,
                    null,
                    Map.of(),
                    List.of(),
                    List.of(),
                    error
            );
        }

        public static SourceOutcome timeout(AssetSourceType source, String traceId) {
            return new SourceOutcome(
                    source,
                    AssetComponentStatus.TIMEOUT,
                    BigDecimal.ZERO,
                    null,
                    Instant.now(),
                    traceId,
                    null,
                    Map.of(),
                    List.of(),
                    List.of(),
                    new TimeoutException("Timed out")
            );
        }

        public boolean isFailure() {
            return status == AssetComponentStatus.FAILED || status == AssetComponentStatus.TIMEOUT;
        }

        public Optional<Throwable> errorOptional() {
            return Optional.ofNullable(error);
        }
    }
}
