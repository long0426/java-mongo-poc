package com.poc.svc.assets.service;

import com.poc.svc.assets.config.MetricsConfig;
import com.poc.svc.assets.util.TraceContext;
import com.poc.svc.assets.dto.AggregatedAssetResponse;
import com.poc.svc.assets.dto.AssetComponentStatus;
import com.poc.svc.assets.dto.AssetSourceType;
import com.poc.svc.assets.entity.AssetStagingDocument;
import com.poc.svc.assets.exception.AssetAggregationException;
import com.poc.svc.assets.repository.AssetStagingRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class AssetAggregationService {

    private static final Logger log = LoggerFactory.getLogger(AssetAggregationService.class);

    private final AssetAggregationCoordinator coordinator;
    private final AssetStagingRepository assetStagingRepository;
    private final CurrencyConversionService currencyConversionService;
    private final AggregationProperties properties;
    private final MeterRegistry meterRegistry;

    public record AggregationProperties(String baseCurrency, Duration timeout) {
        public AggregationProperties {
            Objects.requireNonNull(baseCurrency, "baseCurrency must not be null");
            Objects.requireNonNull(timeout, "timeout must not be null");
        }

        public AggregationProperties normalize() {
            return new AggregationProperties(baseCurrency.trim().toUpperCase(), timeout);
        }
    }

    public AssetAggregationService(
            AssetAggregationCoordinator coordinator,
            AssetStagingRepository assetStagingRepository,
            CurrencyConversionService currencyConversionService,
            AggregationProperties aggregationProperties,
            MeterRegistry meterRegistry
    ) {
        this.coordinator = coordinator;
        this.assetStagingRepository = assetStagingRepository;
        this.currencyConversionService = currencyConversionService;
        this.properties = aggregationProperties.normalize();
        this.meterRegistry = meterRegistry;
    }

    public AggregatedAssetResponse aggregateCustomerAssets(String customerId) {
        if (!StringUtils.hasText(customerId)) {
            throw new IllegalArgumentException("customerId must not be blank");
        }

        String traceId = TraceContext.traceId();
        if (!StringUtils.hasText(traceId)) {
            traceId = TraceContext.ensureTraceId(null);
        }

        Timer.Sample totalTimer = Timer.start(meterRegistry);

        AssetAggregationCoordinator.ExecutionSummary summary = coordinator.coordinate(customerId, traceId, properties.timeout());

        if (summary.hasFailures()) {
            meterRegistry.counter(MetricsConfig.ASSET_AGGREGATION_FAILURE).increment();
            totalTimer.stop(meterRegistry.timer(MetricsConfig.ASSET_AGGREGATION_LATENCY));
            List<AssetSourceType> failedSources = summary.failedSources();
            Throwable rootCause = failedSources.stream()
                    .map(summary::outcome)
                    .map(AssetAggregationCoordinator.SourceOutcome::errorOptional)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .orElse(null);
            log.warn("TraceId={} aggregation failed sources={}", traceId, failedSources);
            String message = "Failed to aggregate assets for customer " + customerId;
            if (rootCause != null && StringUtils.hasText(rootCause.getMessage())) {
                message += " due to " + rootCause.getMessage();
            }
            throw new AssetAggregationException(
                    message,
                    failedSources,
                    rootCause
            );
        }

        AggregationComputation computation = computeAggregation(customerId, traceId, summary);

        AssetStagingDocument stagingDocument = computation.stagingDocument();
        Optional<AssetStagingDocument> existingStaging = assetStagingRepository.findByCustomerId(customerId);
        if (existingStaging.isPresent()) {
            AssetStagingDocument current = stagingDocument;
            AssetStagingDocument previous = existingStaging.get();
            stagingDocument = new AssetStagingDocument(
                    previous.id(),
                    current.customerId(),
                    current.baseCurrency(),
                    current.components(),
                    current.totalAssetValue(),
                    current.currencyBreakdown(),
                    current.aggregationStatus(),
                    current.aggregatedAt(),
                    current.traceId()
            );
        }

        Timer.Sample stagingTimer = Timer.start(meterRegistry);
        stagingDocument = assetStagingRepository.save(stagingDocument);
        stagingTimer.stop(meterRegistry.timer(MetricsConfig.ASSET_AGGREGATION_STAGING_WRITE_LATENCY));

        meterRegistry.counter(MetricsConfig.ASSET_AGGREGATION_SUCCESS).increment();

        AggregatedAssetResponse response = new AggregatedAssetResponse(
                stagingDocument.customerId(),
                stagingDocument.baseCurrency(),
                stagingDocument.totalAssetValue(),
                computation.currencyBreakdown(),
                computation.components(),
                computation.status(),
                stagingDocument.aggregatedAt(),
                traceId
        );

        totalTimer.stop(meterRegistry.timer(MetricsConfig.ASSET_AGGREGATION_LATENCY));

        log.info("TraceId={} aggregationStatus={} totalAssetValue={}", traceId, computation.status(), stagingDocument.totalAssetValue());
        return response;
    }

    private AggregationComputation computeAggregation(String customerId, String traceId, AssetAggregationCoordinator.ExecutionSummary summary) {
        Map<String, BigDecimal> currencyBreakdownMap = new LinkedHashMap<>();
        List<AggregatedAssetResponse.Component> components = new ArrayList<>();
        List<AssetStagingDocument.Component> stagingComponents = new ArrayList<>();
        BigDecimal totalInBase = BigDecimal.ZERO;
        AggregatedAssetResponse.AggregationStatus aggregationStatus = summary.hasMissingData()
                ? AggregatedAssetResponse.AggregationStatus.PARTIAL
                : AggregatedAssetResponse.AggregationStatus.COMPLETED;

        for (AssetSourceType sourceType : EnumSet.allOf(AssetSourceType.class)) {
            AssetAggregationCoordinator.SourceOutcome outcome = summary.outcome(sourceType);
            if (outcome == null) {
                continue;
            }

            ComponentComputation componentComputation = buildComponent(traceId, outcome);
            if (componentComputation.status() == AssetComponentStatus.SUCCESS) {
                totalInBase = totalInBase.add(componentComputation.amountInBase());
                if (sourceType == AssetSourceType.BANK && !outcome.currencySummary().isEmpty()) {
                    outcome.currencySummary().forEach(summaryItem ->
                            mergeCurrency(currencyBreakdownMap, summaryItem.currency(), summaryItem.amount()));
                } else {
                    mergeCurrency(currencyBreakdownMap, componentComputation.sourceCurrency(), componentComputation.originalAmount());
                }
            }

            components.add(componentComputation.responseComponent());
            stagingComponents.add(componentComputation.documentComponent());
        }

        List<AggregatedAssetResponse.CurrencyAmount> currencyBreakdown = currencyBreakdownMap.entrySet().stream()
                .map(entry -> new AggregatedAssetResponse.CurrencyAmount(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP)))
                .toList();

        AssetStagingDocument stagingDocument = new AssetStagingDocument(
                null,
                customerId,
                properties.baseCurrency(),
                stagingComponents,
                totalInBase.setScale(2, RoundingMode.HALF_UP),
                currencyBreakdown.stream()
                        .map(amount -> new AssetStagingDocument.CurrencyAmount(amount.currency(), amount.amount()))
                        .toList(),
                aggregationStatus.name(),
                Instant.now(),
                traceId
        );

        return new AggregationComputation(components, currencyBreakdown, aggregationStatus, stagingDocument);
    }

    private ComponentComputation buildComponent(String traceId, AssetAggregationCoordinator.SourceOutcome outcome) {
        AssetComponentStatus status = outcome.status();
        BigDecimal originalAmount = outcome.amount() == null ? BigDecimal.ZERO : outcome.amount();
        String sourceCurrency = outcome.currency() == null ? properties.baseCurrency() : outcome.currency();
        BigDecimal amountInBase;
        BigDecimal exchangeRate;

        if (status == AssetComponentStatus.SUCCESS) {
            CurrencyConversionService.ConversionResult conversion = currencyConversionService.convert(
                    originalAmount,
                    sourceCurrency,
                    properties.baseCurrency()
            );
            amountInBase = conversion.convertedAmount().setScale(2, RoundingMode.HALF_UP);
            exchangeRate = conversion.exchangeRate().setScale(4, RoundingMode.HALF_UP);
        } else {
            amountInBase = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            exchangeRate = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP);
        }

        String payloadRefId = status == AssetComponentStatus.SUCCESS ? outcome.payloadRefId() : null;
        String effectiveTraceId = outcome.rawTraceId() == null ? traceId : outcome.rawTraceId();
        Instant fetchedAt = outcome.fetchedAt() == null ? Instant.now() : outcome.fetchedAt();
        List<Map<String, Object>> assetDetails = outcome.assetDetails();

        AggregatedAssetResponse.Component responseComponent = new AggregatedAssetResponse.Component(
                outcome.source(),
                status,
                amountInBase,
                sourceCurrency,
                exchangeRate,
                effectiveTraceId,
                fetchedAt,
                assetDetails,
                payloadRefId
        );

        AssetStagingDocument.Component documentComponent = new AssetStagingDocument.Component(
                outcome.source().name(),
                status.name(),
                amountInBase,
                sourceCurrency,
                exchangeRate,
                effectiveTraceId,
                fetchedAt,
                assetDetails,
                payloadRefId
        );

        log.info("TraceId={} source={} status={} amountInBase={} payloadRefId={}",
                traceId, outcome.source(), status, amountInBase, payloadRefId);

        return new ComponentComputation(outcome.source(), status, originalAmount, sourceCurrency, amountInBase, assetDetails, responseComponent, documentComponent);
    }

    private void mergeCurrency(Map<String, BigDecimal> breakdown, String currency, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        String normalizedCurrency = currency == null ? properties.baseCurrency() : currency.toUpperCase();
        BigDecimal current = breakdown.getOrDefault(normalizedCurrency, BigDecimal.ZERO);
        breakdown.put(normalizedCurrency, current.add(amount));
    }

    private record AggregationComputation(
            List<AggregatedAssetResponse.Component> components,
            List<AggregatedAssetResponse.CurrencyAmount> currencyBreakdown,
            AggregatedAssetResponse.AggregationStatus status,
            AssetStagingDocument stagingDocument
    ) {
    }

    private record ComponentComputation(
            AssetSourceType source,
            AssetComponentStatus status,
            BigDecimal originalAmount,
            String sourceCurrency,
            BigDecimal amountInBase,
            List<Map<String, Object>> assetDetails,
            AggregatedAssetResponse.Component responseComponent,
            AssetStagingDocument.Component documentComponent
    ) {
    }
}
