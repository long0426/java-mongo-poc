package com.poc.svc.assets.service;

import com.poc.svc.assets.config.MetricsConfig;
import com.poc.svc.assets.dto.AssetSourceType;
import com.poc.svc.assets.exception.AssetAggregationException;
import com.poc.svc.assets.util.TraceContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class AssetAggregationService {

    private static final Logger log = LoggerFactory.getLogger(AssetAggregationService.class);

    private final AssetAggregationCoordinator coordinator;
    private final AggregationExecutor aggregationExecutor;
    private final AggregationProperties properties;
    private final MeterRegistry meterRegistry;

    public record AggregationProperties(Duration timeout, String pipelineName) {
        public AggregationProperties {
            Objects.requireNonNull(timeout, "timeout must not be null");
            if (!StringUtils.hasText(pipelineName)) {
                throw new IllegalArgumentException("pipelineName must not be blank");
            }
            pipelineName = pipelineName.trim();
        }
    }

    public AssetAggregationService(
            AssetAggregationCoordinator coordinator,
            AggregationExecutor aggregationExecutor,
            AggregationProperties aggregationProperties,
            MeterRegistry meterRegistry
    ) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
        this.aggregationExecutor = Objects.requireNonNull(aggregationExecutor, "aggregationExecutor must not be null");
        this.properties = Objects.requireNonNull(aggregationProperties, "aggregationProperties must not be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    public List<Document> aggregateCustomerAssets(String customerId) {
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
            throw new AssetAggregationException(message, failedSources, rootCause);
        }

        Timer.Sample pipelineTimer = Timer.start(meterRegistry);
        try {
            List<Document> aggregationResult = aggregationExecutor.execute(properties.pipelineName(), traceId);
            pipelineTimer.stop(meterRegistry.timer(MetricsConfig.ASSET_AGGREGATION_STAGING_WRITE_LATENCY));
            meterRegistry.counter(MetricsConfig.ASSET_AGGREGATION_SUCCESS).increment();
            totalTimer.stop(meterRegistry.timer(MetricsConfig.ASSET_AGGREGATION_LATENCY));
            log.info("TraceId={} completed pipeline={} resultSize={}", traceId, properties.pipelineName(), aggregationResult.size());
            return aggregationResult;
        } catch (RuntimeException ex) {
            pipelineTimer.stop(meterRegistry.timer(MetricsConfig.ASSET_AGGREGATION_STAGING_WRITE_LATENCY));
            meterRegistry.counter(MetricsConfig.ASSET_AGGREGATION_FAILURE).increment();
            totalTimer.stop(meterRegistry.timer(MetricsConfig.ASSET_AGGREGATION_LATENCY));
            log.error("TraceId={} pipeline execution failed pipeline={}", traceId, properties.pipelineName(), ex);
            throw ex;
        }
    }
}
