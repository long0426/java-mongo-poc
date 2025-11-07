package com.poc.svc.assets.service;

import com.poc.svc.assets.config.MongoWriteRetryProperties;
import com.poc.svc.assets.config.MetricsConfig;
import com.poc.svc.assets.service.AssetSourceClient;
import com.poc.svc.assets.service.AssetSourceClient.BankAssetResult;
import com.poc.svc.assets.service.AssetSourceClient.InsuranceAssetResult;
import com.poc.svc.assets.service.AssetSourceClient.SecuritiesAssetResult;
import com.poc.svc.assets.util.TraceContext;
import com.poc.svc.assets.dto.AssetSourceType;
import com.poc.svc.assets.exception.AssetAggregationException;
import com.poc.svc.assets.repository.AssetStagingRepository;
import com.poc.svc.assets.repository.BankAssetRawRepository;
import com.poc.svc.assets.repository.InsuranceAssetRawRepository;
import com.poc.svc.assets.repository.SecuritiesAssetRawRepository;
import com.poc.svc.assets.entity.AssetStagingDocument;
import com.poc.svc.assets.entity.BankAssetRawDocument;
import com.poc.svc.assets.entity.InsuranceAssetRawDocument;
import com.poc.svc.assets.entity.SecuritiesAssetRawDocument;
import com.poc.svc.assets.service.impl.support.MongoWriteRetrier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssetSuccessRateMetricsTest {

    @Mock
    private BankAssetRawRepository bankAssetRawRepository;
    @Mock
    private SecuritiesAssetRawRepository securitiesAssetRawRepository;
    @Mock
    private InsuranceAssetRawRepository insuranceAssetRawRepository;
    @Mock
    private AssetStagingRepository assetStagingRepository;

    private SimpleMeterRegistry meterRegistry;
    private StubAssetSourceClient assetSourceClient;
    private AssetAggregationService assetAggregationService;
    private MongoWriteRetryProperties retryProperties;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        assetSourceClient = new StubAssetSourceClient();
        retryProperties = MongoWriteRetryProperties.defaults();
        retryProperties.setMaxAttempts(3);
        retryProperties.setBackoff(Duration.ZERO);
        MongoWriteRetrier writeRetrier = new MongoWriteRetrier(retryProperties);

        AssetAggregationCoordinator coordinator = new AssetAggregationCoordinator(
                assetSourceClient,
                new BankAssetWriter(bankAssetRawRepository, writeRetrier),
                new SecuritiesAssetWriter(securitiesAssetRawRepository, writeRetrier),
                new InsuranceAssetWriter(insuranceAssetRawRepository, writeRetrier),
                meterRegistry
        );

        assetAggregationService = new AssetAggregationService(
                coordinator,
                assetStagingRepository,
                new PassthroughConversionService(),
                new ObjectMapper(),
                new AssetAggregationService.AggregationProperties("TWD", Duration.ofSeconds(2)),
                meterRegistry
        );

        assetSourceClient.bankResult = new BankAssetResult(
                "cust-metrics",
                Map.of("bankAssets", List.of(Map.of("accountId", "B-1", "balance", 1200, "currency", "TWD"))),
                BigDecimal.valueOf(1200),
                "TWD",
                List.of(new BankAssetWriter.BankAssetWriteRequest.CurrencyAmount("TWD", BigDecimal.valueOf(1200))),
                Instant.parse("2025-10-20T04:00:00Z"),
                "bank-trace"
        );
        assetSourceClient.securitiesResult = new SecuritiesAssetResult(
                "cust-metrics",
                Map.of("securitiesAssets", List.of(Map.of("symbol", "ETF", "marketValue", 3000, "currency", "USD"))),
                BigDecimal.valueOf(3000),
                "USD",
                1,
                Instant.parse("2025-10-20T04:00:01Z"),
                "sec-trace"
        );
        assetSourceClient.insuranceResult = new InsuranceAssetResult(
                "cust-metrics",
                Map.of("insuranceAssets", List.of(Map.of("policyNumber", "POL-9", "coverage", 5000, "currency", "TWD"))),
                BigDecimal.valueOf(5000),
                "TWD",
                1,
                Instant.parse("2025-10-20T04:00:02Z"),
                "ins-trace"
        );

        when(securitiesAssetRawRepository.save(any(SecuritiesAssetRawDocument.class))).thenAnswer(invocation -> {
            SecuritiesAssetRawDocument doc = invocation.getArgument(0);
            return new SecuritiesAssetRawDocument("sec-success", doc.customerId(), doc.payload(), doc.totalMarketValue(), doc.holdingsCount(), doc.fetchedAt(), doc.traceId());
        });
        when(insuranceAssetRawRepository.save(any(InsuranceAssetRawDocument.class))).thenAnswer(invocation -> {
            InsuranceAssetRawDocument doc = invocation.getArgument(0);
            return new InsuranceAssetRawDocument("ins-success", doc.customerId(), doc.payload(), doc.totalCoverage(), doc.policiesCount(), doc.fetchedAt(), doc.traceId());
        });
        when(assetStagingRepository.save(any(AssetStagingDocument.class))).thenAnswer(invocation -> {
            AssetStagingDocument doc = invocation.getArgument(0);
            return new AssetStagingDocument("staging-id", doc.customerId(), doc.baseCurrency(), doc.components(), doc.assets(), doc.totalAssetValue(), doc.currencyBreakdown(), doc.aggregationStatus(), doc.aggregatedAt(), doc.traceId());
        });
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        meterRegistry.close();
    }

    @Test
    void countersReflectSuccessAndFailureFlows() {
        when(bankAssetRawRepository.save(any(BankAssetRawDocument.class))).thenAnswer(invocation -> {
            BankAssetRawDocument doc = invocation.getArgument(0);
            return new BankAssetRawDocument("bank-success", doc.customerId(), doc.payload(), doc.totalBalance(), doc.currencySummary(), doc.fetchedAt(), doc.traceId());
        }).thenThrow(new DataAccessException("write failed") {});

        TraceContext.ensureTraceId("trace-success");
        assetAggregationService.aggregateCustomerAssets("cust-metrics");

        assertThat(meterRegistry.counter(MetricsConfig.ASSET_AGGREGATION_SUCCESS).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(MetricsConfig.ASSET_AGGREGATION_FAILURE).count()).isEqualTo(0.0);
        assertThat(meterRegistry.counter("asset.aggregation.raw.write", "source", AssetSourceType.BANK.name(), "status", "SUCCESS").count())
                .isEqualTo(1.0);

        TraceContext.ensureTraceId("trace-failure");
        assertThatThrownBy(() -> assetAggregationService.aggregateCustomerAssets("cust-metrics"))
                .isInstanceOf(AssetAggregationException.class);

        assertThat(meterRegistry.counter(MetricsConfig.ASSET_AGGREGATION_SUCCESS).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(MetricsConfig.ASSET_AGGREGATION_FAILURE).count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("asset.aggregation.raw.write", "source", AssetSourceType.BANK.name(), "status", "FAILED").count())
                .isEqualTo(1.0);

        verify(bankAssetRawRepository, times(1 + retryProperties.getMaxAttempts())).save(any(BankAssetRawDocument.class));
    }

    private static final class StubAssetSourceClient implements AssetSourceClient {
        private BankAssetResult bankResult;
        private SecuritiesAssetResult securitiesResult;
        private InsuranceAssetResult insuranceResult;

        @Override
        public CompletableFuture<BankAssetResult> fetchBankAssets(String customerId, String traceId) {
            return CompletableFuture.completedFuture(bankResult);
        }

        @Override
        public CompletableFuture<SecuritiesAssetResult> fetchSecuritiesAssets(String customerId, String traceId) {
            return CompletableFuture.completedFuture(securitiesResult);
        }

        @Override
        public CompletableFuture<InsuranceAssetResult> fetchInsuranceAssets(String customerId, String traceId) {
            return CompletableFuture.completedFuture(insuranceResult);
        }
    }

    private static final class PassthroughConversionService implements CurrencyConversionService {
        @Override
        public ConversionResult convert(BigDecimal amount, String fromCurrency, String toCurrency) {
            return new ConversionResult(amount, BigDecimal.ONE);
        }
    }
}
