package com.poc.svc.assets.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AssetAggregationService {

    private static final Logger log = LoggerFactory.getLogger(AssetAggregationService.class);
    private static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final AssetAggregationCoordinator coordinator;
    private final AssetStagingRepository assetStagingRepository;
    private final CurrencyConversionService currencyConversionService;
    private final AggregationProperties properties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

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
            ObjectMapper objectMapper,
            AggregationProperties aggregationProperties,
            MeterRegistry meterRegistry
    ) {
        this.coordinator = coordinator;
        this.assetStagingRepository = assetStagingRepository;
        this.currencyConversionService = currencyConversionService;
        this.properties = aggregationProperties.normalize();
        this.meterRegistry = meterRegistry;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
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
                    current.assets(),
                    current.totalAssetValue(),
                    current.currencyBreakdown(),
                    current.aggregationStatus(),
                    current.aggregatedAt(),
                    current.traceId()
            );
        }

        Timer.Sample stagingTimer = Timer.start(meterRegistry);
        // stagingDocument = assetStagingRepository.save(stagingDocument);  將資料處理工程交給Mongo Aggregation Pipeline
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
        List<AssetEntryDraft> assetEntryDrafts = new ArrayList<>();
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
            log.info("component = {}", componentComputation);
            if (log.isDebugEnabled()) {
                log.debug("TraceId={} source={} componentEntries={}", traceId, outcome.source(), componentComputation.assetEntries().size());
            }
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
            assetEntryDrafts.addAll(componentComputation.assetEntries());
        }

        List<AggregatedAssetResponse.CurrencyAmount> currencyBreakdown = currencyBreakdownMap.entrySet().stream()
                .map(entry -> new AggregatedAssetResponse.CurrencyAmount(entry.getKey(), entry.getValue().setScale(2, RoundingMode.HALF_UP)))
                .toList();

        BigDecimal totalAssetValue = totalInBase.setScale(2, RoundingMode.HALF_UP);
        Instant aggregatedAt = Instant.now();
        List<AssetStagingDocument.AssetEntry> assetEntries = assetEntryDrafts.stream()
                .map(draft -> draft.toAssetEntry(
                        customerId,
                        properties.baseCurrency(),
                        totalAssetValue,
                        aggregationStatus.name(),
                        aggregatedAt,
                        traceId
                ))
                .toList();
        if (log.isDebugEnabled()) {
            log.debug("TraceId={} prepared {} asset entries from sources {}", traceId, assetEntries.size(),
                    assetEntries.stream().map(AssetStagingDocument.AssetEntry::source).distinct().toList());
        }

        AssetStagingDocument stagingDocument = new AssetStagingDocument(
                null,
                customerId,
                properties.baseCurrency(),
                stagingComponents,
                assetEntries,
                totalAssetValue,
                currencyBreakdown.stream()
                        .map(amount -> new AssetStagingDocument.CurrencyAmount(amount.currency(), amount.amount()))
                        .toList(),
                aggregationStatus.name(),
                aggregatedAt,
                traceId
        );
        log.info("TraceId={} staging assets count={} detailSources={}", traceId, assetEntries.size(),
                assetEntries.stream().map(AssetStagingDocument.AssetEntry::source).distinct().toList());

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

        List<AssetEntryDraft> assetEntries = status == AssetComponentStatus.SUCCESS
                ? resolveAssetEntries(traceId, outcome, payloadRefId, fetchedAt)
                : List.of();

        return new ComponentComputation(
                outcome.source(),
                status,
                originalAmount,
                sourceCurrency,
                amountInBase,
                assetDetails,
                responseComponent,
                documentComponent,
                assetEntries
        );
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
            AssetStagingDocument.Component documentComponent,
            List<AssetEntryDraft> assetEntries
    ) {
    }

    private List<AssetEntryDraft> resolveAssetEntries(String traceId, AssetAggregationCoordinator.SourceOutcome outcome, String payloadRefId, Instant fetchedAt) {
        try {
            return switch (outcome.source()) {
                case BANK -> buildBankAssetEntries(traceId, outcome, payloadRefId, fetchedAt);
                case SECURITIES -> buildSecuritiesAssetEntries(traceId, outcome, payloadRefId, fetchedAt);
                case INSURANCE -> buildInsuranceAssetEntries(traceId, outcome, payloadRefId, fetchedAt);
            };
        } catch (IllegalArgumentException ex) {
            log.warn("TraceId={} source={} failed to map asset payload: {}", traceId, outcome.source(), ex.getMessage());
            return List.of();
        }
    }

    private <T> List<AssetEntryDraft> mapAssetEntries(
            String traceId,
            AssetAggregationCoordinator.SourceOutcome outcome,
            List<T> items,
            Function<T, AssetEntryDraft> mapper
    ) {
        List<AssetEntryDraft> entries = new ArrayList<>();
        for (T item : items) {
            try {
                entries.add(mapper.apply(item));
            } catch (IllegalArgumentException ex) {
                log.warn("TraceId={} source={} skip asset entry: {}", traceId, outcome.source(), ex.getMessage());
            }
        }
        return entries;
    }

    private List<AssetEntryDraft> buildBankAssetEntries(String traceId, AssetAggregationCoordinator.SourceOutcome outcome, String payloadRefId, Instant fetchedAt) {
        List<Map<String, Object>> genericItems = extractAssetMaps(outcome.payload(), "bankAssets", "accounts");
        if (!genericItems.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Source=BANK rawTraceId={} extracted {} generic accounts example={}", outcome.rawTraceId(), genericItems.size(), genericItems.get(0));
            }
            return mapAssetEntries(traceId, outcome, genericItems, item -> toBankEntry(outcome, payloadRefId, fetchedAt, item));
        }
        BankPayload payload = objectMapper.convertValue(outcome.payload(), BankPayload.class);
        if (payload.bankAssets() == null || payload.bankAssets().isEmpty()) {
            return List.of();
        }
        return mapAssetEntries(traceId, outcome, payload.bankAssets(), item -> toBankEntry(outcome, payloadRefId, fetchedAt, item));
    }

    private List<AssetEntryDraft> buildSecuritiesAssetEntries(String traceId, AssetAggregationCoordinator.SourceOutcome outcome, String payloadRefId, Instant fetchedAt) {
        List<Map<String, Object>> genericItems = extractAssetMaps(outcome.payload(), "securitiesAssets", "holdings");
        if (!genericItems.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Source=SECURITIES rawTraceId={} extracted {} generic holdings example={}", outcome.rawTraceId(), genericItems.size(), genericItems.get(0));
            }
            return mapAssetEntries(traceId, outcome, genericItems, item -> toSecuritiesEntry(outcome, payloadRefId, fetchedAt, item));
        }
        SecuritiesPayload payload = objectMapper.convertValue(outcome.payload(), SecuritiesPayload.class);
        if (payload.securitiesAssets() == null || payload.securitiesAssets().isEmpty()) {
            return List.of();
        }
        return mapAssetEntries(traceId, outcome, payload.securitiesAssets(), item -> toSecuritiesEntry(outcome, payloadRefId, fetchedAt, item));
    }

    private List<AssetEntryDraft> buildInsuranceAssetEntries(String traceId, AssetAggregationCoordinator.SourceOutcome outcome, String payloadRefId, Instant fetchedAt) {
        List<Map<String, Object>> genericItems = extractAssetMaps(outcome.payload(), "insuranceAssets", "policies");
        if (!genericItems.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Source=INSURANCE rawTraceId={} extracted {} generic policies example={}", outcome.rawTraceId(), genericItems.size(), genericItems.get(0));
            }
            return mapAssetEntries(traceId, outcome, genericItems, item -> toInsuranceEntry(outcome, payloadRefId, fetchedAt, item));
        }
        InsurancePayload payload = objectMapper.convertValue(outcome.payload(), InsurancePayload.class);
        if (payload.insuranceAssets() == null || payload.insuranceAssets().isEmpty()) {
            return List.of();
        }
        return mapAssetEntries(traceId, outcome, payload.insuranceAssets(), item -> toInsuranceEntry(outcome, payloadRefId, fetchedAt, item));
    }

    private AssetEntryDraft toBankEntry(AssetAggregationCoordinator.SourceOutcome outcome, String payloadRefId, Instant fetchedAt, BankItem item) {
        String currency = normalizeCurrency(item.currency());
        BigDecimal balance = nullableAmount(item.balance());
        BigDecimal amountForConversion = balance != null ? balance : ZERO_AMOUNT;
        CurrencyConversionService.ConversionResult conversion = currencyConversionService.convert(
                amountForConversion,
                currency,
                properties.baseCurrency()
        );
        BigDecimal exchangeRate = normalizeExchangeRate(conversion.exchangeRate());
        BigDecimal amountInBase = conversion.convertedAmount().setScale(2, RoundingMode.HALF_UP);
        String accountId = normalizeString(item.accountId());
        return new AssetEntryDraft(
                outcome.source(),
                outcome.status(),
                accountId,
                "account",
                currency,
                currency,
                exchangeRate,
                amountInBase,
                balance,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                accountId,
                fetchedAt,
                payloadRefId
        );
    }

    private AssetEntryDraft toBankEntry(AssetAggregationCoordinator.SourceOutcome outcome, String payloadRefId, Instant fetchedAt, Map<String, Object> item) {
        String accountId = normalizeString(stringValue(item.get("accountId")));
        String currency = normalizeCurrency(stringValue(item.get("currency")));
        BigDecimal balance = nullableAmount(toBigDecimal(item.get("balance")));
        BigDecimal amountForConversion = balance != null ? balance : ZERO_AMOUNT;
        CurrencyConversionService.ConversionResult conversion = currencyConversionService.convert(
                amountForConversion,
                currency,
                properties.baseCurrency()
        );
        BigDecimal exchangeRate = normalizeExchangeRate(conversion.exchangeRate());
        BigDecimal amountInBase = conversion.convertedAmount().setScale(2, RoundingMode.HALF_UP);
        return new AssetEntryDraft(
                outcome.source(),
                outcome.status(),
                accountId,
                normalizeString(stringValue(item.getOrDefault("assetType", "account"))),
                currency,
                currency,
                exchangeRate,
                amountInBase,
                balance,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                accountId,
                fetchedAt,
                payloadRefId
        );
    }

    private AssetEntryDraft toSecuritiesEntry(AssetAggregationCoordinator.SourceOutcome outcome, String payloadRefId, Instant fetchedAt, SecuritiesHolding item) {
        String currency = normalizeCurrency(item.currency());
        BigDecimal marketValue = nullableAmount(item.marketValue());
        BigDecimal amountForConversion = marketValue != null ? marketValue : ZERO_AMOUNT;
        CurrencyConversionService.ConversionResult conversion = currencyConversionService.convert(
                amountForConversion,
                currency,
                properties.baseCurrency()
        );
        BigDecimal exchangeRate = normalizeExchangeRate(conversion.exchangeRate());
        BigDecimal amountInBase = conversion.convertedAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal holdings = item.holdings() == null ? null : BigDecimal.valueOf(item.holdings()).setScale(4, RoundingMode.HALF_UP);
        String symbol = normalizeString(item.symbol());
        String securityType = normalizeString(item.securityType());
        String assetName = symbol != null ? symbol : securityType;
        return new AssetEntryDraft(
                outcome.source(),
                outcome.status(),
                assetName,
                securityType,
                currency,
                currency,
                exchangeRate,
                amountInBase,
                null,
                marketValue,
                null,
                null,
                null,
                null,
                normalizeString(item.riskLevel()),
                symbol,
                holdings,
                null,
                fetchedAt,
                payloadRefId
        );
    }

    private AssetEntryDraft toSecuritiesEntry(AssetAggregationCoordinator.SourceOutcome outcome, String payloadRefId, Instant fetchedAt, Map<String, Object> item) {
        String currency = normalizeCurrency(stringValue(item.get("currency")));
        BigDecimal marketValue = nullableAmount(toBigDecimal(item.get("marketValue")));
        BigDecimal amountForConversion = marketValue != null ? marketValue : ZERO_AMOUNT;
        CurrencyConversionService.ConversionResult conversion = currencyConversionService.convert(
                amountForConversion,
                currency,
                properties.baseCurrency()
        );
        BigDecimal exchangeRate = normalizeExchangeRate(conversion.exchangeRate());
        BigDecimal amountInBase = conversion.convertedAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal holdings = toBigDecimal(item.get("holdings"));
        if (holdings != null) {
            holdings = holdings.setScale(4, RoundingMode.HALF_UP);
        }
        String symbol = normalizeString(stringValue(item.get("symbol")));
        String securityType = normalizeString(stringValue(item.getOrDefault("securityType", item.get("assetType"))));
        String assetName = symbol != null ? symbol : securityType;
        return new AssetEntryDraft(
                outcome.source(),
                outcome.status(),
                assetName,
                securityType,
                currency,
                currency,
                exchangeRate,
                amountInBase,
                null,
                marketValue,
                null,
                null,
                null,
                normalizeString(stringValue(item.get("premiumStatus"))),
                normalizeString(stringValue(item.get("riskLevel"))),
                symbol,
                holdings,
                null,
                fetchedAt,
                payloadRefId
        );
    }

    private AssetEntryDraft toInsuranceEntry(AssetAggregationCoordinator.SourceOutcome outcome, String payloadRefId, Instant fetchedAt, InsuranceItem item) {
        String currency = normalizeCurrency(item.currency());
        BigDecimal coverage = nullableAmount(item.coverage());
        BigDecimal amountForConversion = coverage != null ? coverage : ZERO_AMOUNT;
        CurrencyConversionService.ConversionResult conversion = currencyConversionService.convert(
                amountForConversion,
                currency,
                properties.baseCurrency()
        );
        BigDecimal exchangeRate = normalizeExchangeRate(conversion.exchangeRate());
        BigDecimal amountInBase = conversion.convertedAmount().setScale(2, RoundingMode.HALF_UP);
        String policyNumber = normalizeString(item.policyNumber());
        String policyType = normalizeString(item.policyType());
        return new AssetEntryDraft(
                outcome.source(),
                outcome.status(),
                policyNumber,
                policyType,
                currency,
                currency,
                exchangeRate,
                amountInBase,
                null,
                null,
                coverage,
                policyNumber,
                policyType,
                normalizeString(item.premiumStatus()),
                null,
                null,
                null,
                null,
                fetchedAt,
                payloadRefId
        );
    }

    private AssetEntryDraft toInsuranceEntry(AssetAggregationCoordinator.SourceOutcome outcome, String payloadRefId, Instant fetchedAt, Map<String, Object> item) {
        String currency = normalizeCurrency(stringValue(item.get("currency")));
        BigDecimal coverage = nullableAmount(toBigDecimal(item.get("coverage")));
        BigDecimal amountForConversion = coverage != null ? coverage : ZERO_AMOUNT;
        CurrencyConversionService.ConversionResult conversion = currencyConversionService.convert(
                amountForConversion,
                currency,
                properties.baseCurrency()
        );
        BigDecimal exchangeRate = normalizeExchangeRate(conversion.exchangeRate());
        BigDecimal amountInBase = conversion.convertedAmount().setScale(2, RoundingMode.HALF_UP);
        String policyNumber = normalizeString(stringValue(item.getOrDefault("policyNumber", item.get("assetName"))));
        String policyType = normalizeString(stringValue(item.getOrDefault("policyType", item.get("assetType"))));
        return new AssetEntryDraft(
                outcome.source(),
                outcome.status(),
                policyNumber,
                policyType,
                currency,
                currency,
                exchangeRate,
                amountInBase,
                null,
                null,
                coverage,
                policyNumber,
                policyType,
                normalizeString(stringValue(item.get("premiumStatus"))),
                null,
                null,
                null,
                null,
                fetchedAt,
                payloadRefId
        );
    }

    private BigDecimal nullableAmount(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal normalizeExchangeRate(BigDecimal exchangeRate) {
        return exchangeRate == null ? null : exchangeRate.setScale(6, RoundingMode.HALF_UP);
    }

    private String normalizeCurrency(String currency) {
        if (!StringUtils.hasText(currency)) {
            return properties.baseCurrency();
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeString(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private record AssetEntryDraft(
            AssetSourceType source,
            AssetComponentStatus status,
            String assetName,
            String assetType,
            String currency,
            String sourceCurrency,
            BigDecimal exchangeRate,
            BigDecimal amountInBase,
            BigDecimal balance,
            BigDecimal marketValue,
            BigDecimal coverage,
            String policyNumber,
            String policyType,
            String premiumStatus,
            String riskLevel,
            String symbol,
            BigDecimal holdings,
            String accountId,
            Instant fetchedAt,
            String payloadRefId
    ) {
        AssetStagingDocument.AssetEntry toAssetEntry(
                String customerId,
                String baseCurrency,
                BigDecimal totalAssetValue,
                String aggregationStatus,
                Instant aggregatedAt,
                String traceId
        ) {
            return new AssetStagingDocument.AssetEntry(
                    customerId,
                    source.name(),
                    status.name(),
                    assetName,
                    assetType,
                    currency,
                    sourceCurrency,
                    exchangeRate,
                    baseCurrency,
                    amountInBase,
                    balance,
                    marketValue,
                    coverage,
                    policyNumber,
                    policyType,
                    premiumStatus,
                    riskLevel,
                    symbol,
                    holdings,
                    accountId,
                    fetchedAt,
                    aggregatedAt,
                    traceId,
                    payloadRefId,
                    totalAssetValue,
                    aggregationStatus
            );
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BankPayload(List<BankItem> bankAssets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BankItem(String accountId, BigDecimal balance, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SecuritiesPayload(List<SecuritiesHolding> securitiesAssets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SecuritiesHolding(
            String securityType,
            String symbol,
            Integer holdings,
            BigDecimal marketValue,
            String currency,
            String riskLevel
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InsurancePayload(List<InsuranceItem> insuranceAssets) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record InsuranceItem(
            String policyNumber,
            String policyType,
            BigDecimal coverage,
            String premiumStatus,
            String currency
    ) {
    }

    private List<Map<String, Object>> extractAssetMaps(Map<String, Object> payload, String... keys) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }
        Map<String, Object> normalized = convertToMap(payload);
        for (String key : keys) {
            List<Map<String, Object>> matches = new ArrayList<>();
            collectMapsByKey(normalized, key, matches);
            if (!matches.isEmpty()) {
                return matches;
            }
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object raw) {
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(convertToMap(map));
                }
            }
            return result;
        }
        return List.of();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        if (value instanceof String s) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                return null;
            }
            return new BigDecimal(trimmed);
        }
        return null;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        return value.toString();
    }

    private Map<String, Object> convertToMap(Map<?, ?> source) {
        return source.entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        Map.Entry::getValue,
                        (left, right) -> right,
                        LinkedHashMap::new
                ));
    }

    private void collectMapsByKey(Map<String, Object> current, String targetKey, List<Map<String, Object>> acc) {
        Object direct = current.get(targetKey);
        acc.addAll(toMapList(direct));
        if (!acc.isEmpty()) {
            return;
        }
        for (Object value : current.values()) {
            if (value instanceof Map<?, ?> map) {
                collectMapsByKey(convertToMap(map), targetKey, acc);
                if (!acc.isEmpty()) {
                    return;
                }
            } else if (value instanceof List<?> list) {
                for (Object element : list) {
                    if (element instanceof Map<?, ?> nested) {
                        collectMapsByKey(convertToMap(nested), targetKey, acc);
                        if (!acc.isEmpty()) {
                            return;
                        }
                    }
                }
            }
        }
    }
}
