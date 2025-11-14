package com.poc.svc.assets.service;

import com.poc.svc.assets.dto.AssetSourceType;
import com.poc.svc.assets.exception.AssetAggregationException;
import com.poc.svc.assets.service.AssetAggregationCoordinator.ExecutionSummary;
import com.poc.svc.assets.service.AssetAggregationCoordinator.SourceOutcome;
import com.poc.svc.assets.service.BankAssetWriter.BankAssetWriteRequest;
import com.poc.svc.assets.util.TraceContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetAggregationServiceTest {

    @Mock
    private AssetAggregationCoordinator coordinator;

    @Mock
    private AggregationExecutor aggregationExecutor;

    private AssetAggregationService service;

    @BeforeEach
    void setUp() {
        TraceContext.clear();
        service = new AssetAggregationService(
                coordinator,
                aggregationExecutor,
                new AssetAggregationService.AggregationProperties(Duration.ofSeconds(2), "assets_aggregation"),
                new SimpleMeterRegistry()
        );
    }

    @Test
    @DisplayName("should delegate to aggregation executor when coordination succeeds")
    void aggregateCustomerAssets_success() {
        ExecutionSummary summary = successSummary();
        when(coordinator.coordinate(anyString(), anyString(), any())).thenReturn(summary);
        List<Document> expected = List.of(new Document("traceId", "agg-trace"));
        when(aggregationExecutor.execute("assets_aggregation", "agg-trace")).thenReturn(expected);

        TraceContext.ensureTraceId("agg-trace");

        List<Document> actual = service.aggregateCustomerAssets("c-123");

        assertThat(actual).isEqualTo(expected);
        verify(aggregationExecutor).execute("assets_aggregation", "agg-trace");
    }

    @Test
    @DisplayName("should throw AssetAggregationException when any source fails")
    void aggregateCustomerAssets_failure() {
        ExecutionSummary summary = failureSummary();
        when(coordinator.coordinate(anyString(), anyString(), any())).thenReturn(summary);

        TraceContext.ensureTraceId("trace-failed");

        assertThatThrownBy(() -> service.aggregateCustomerAssets("c-456"))
                .isInstanceOf(AssetAggregationException.class)
                .hasMessageContaining("Failed to aggregate assets");

        verify(aggregationExecutor, never()).execute(anyString(), anyString());
    }

    @Test
    @DisplayName("should propagate executor errors")
    void aggregateCustomerAssets_executorError() {
        ExecutionSummary summary = successSummary();
        when(coordinator.coordinate(anyString(), anyString(), any())).thenReturn(summary);
        when(aggregationExecutor.execute("assets_aggregation", "trace-error"))
                .thenThrow(new IllegalStateException("pipeline missing"));

        TraceContext.ensureTraceId("trace-error");

        assertThatThrownBy(() -> service.aggregateCustomerAssets("c-789"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pipeline missing");
    }

    private ExecutionSummary successSummary() {
        Map<AssetSourceType, SourceOutcome> outcomes = new EnumMap<>(AssetSourceType.class);
        outcomes.put(AssetSourceType.BANK, SourceOutcome.success(
                AssetSourceType.BANK,
                BigDecimal.valueOf(100),
                "TWD",
                Instant.now(),
                "bank-trace",
                "payload-1",
                Map.of(),
                List.of(new BankAssetWriteRequest.CurrencyAmount("TWD", BigDecimal.valueOf(100))),
                List.of()
        ));
        outcomes.put(AssetSourceType.SECURITIES, SourceOutcome.missing(AssetSourceType.SECURITIES, "sec-trace"));
        outcomes.put(AssetSourceType.INSURANCE, SourceOutcome.success(
                AssetSourceType.INSURANCE,
                BigDecimal.valueOf(50),
                "TWD",
                Instant.now(),
                "ins-trace",
                "payload-2",
                Map.of(),
                List.of(),
                List.of()
        ));
        return new ExecutionSummary(outcomes);
    }

    private ExecutionSummary failureSummary() {
        Map<AssetSourceType, SourceOutcome> outcomes = new EnumMap<>(AssetSourceType.class);
        outcomes.put(AssetSourceType.BANK, SourceOutcome.failed(AssetSourceType.BANK, "trace", new RuntimeException("boom")));
        outcomes.put(AssetSourceType.SECURITIES, SourceOutcome.missing(AssetSourceType.SECURITIES, "trace"));
        outcomes.put(AssetSourceType.INSURANCE, SourceOutcome.success(
                AssetSourceType.INSURANCE,
                BigDecimal.ONE,
                "TWD",
                Instant.now(),
                "ins",
                "payload",
                Map.of(),
                List.of(),
                List.of()
        ));
        return new ExecutionSummary(outcomes);
    }
}
