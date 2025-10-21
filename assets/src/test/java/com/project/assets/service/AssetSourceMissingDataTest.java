package com.project.assets.service;

import com.project.assets.config.MongoWriteRetryProperties;
import com.project.assets.integration.AssetSourceClient;
import com.project.assets.integration.AssetSourceClient.BankAssetResult;
import com.project.assets.integration.AssetSourceClient.InsuranceAssetResult;
import com.project.assets.integration.AssetSourceClient.SecuritiesAssetResult;
import com.project.assets.integration.AssetSourceMissingException;
import com.project.assets.logging.TraceContext;
import com.project.assets.model.AggregatedAssetResponse;
import com.project.assets.model.AssetComponentStatus;
import com.project.assets.model.AssetSourceType;
import com.project.assets.repository.AssetStagingRepository;
import com.project.assets.repository.BankAssetRawRepository;
import com.project.assets.repository.InsuranceAssetRawRepository;
import com.project.assets.repository.SecuritiesAssetRawRepository;
import com.project.assets.repository.document.AssetStagingDocument;
import com.project.assets.service.support.MongoWriteRetrier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class AssetSourceMissingDataTest {

    @Container
    static final MongoDBContainer mongoContainer = new MongoDBContainer("mongo:8.0");

    @DynamicPropertySource
    static void overrideMongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoContainer::getConnectionString);
        registry.add("spring.data.mongodb.database", () -> "testdb");
    }

    @Autowired
    private BankAssetRawRepository bankAssetRawRepository;

    @Autowired
    private SecuritiesAssetRawRepository securitiesAssetRawRepository;

    @Autowired
    private InsuranceAssetRawRepository insuranceAssetRawRepository;

    @Autowired
    private AssetStagingRepository assetStagingRepository;

    private MissingSourceClient assetSourceClient;
    private AssetAggregationService assetAggregationService;

    @BeforeEach
    void setUp() {
        bankAssetRawRepository.deleteAll();
        securitiesAssetRawRepository.deleteAll();
        insuranceAssetRawRepository.deleteAll();
        assetStagingRepository.deleteAll();

        assetSourceClient = new MissingSourceClient();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MongoWriteRetrier writeRetrier = new MongoWriteRetrier(MongoWriteRetryProperties.defaults());
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
                new LocalStaticConversionService(Map.of(
                        "USD:TWD", BigDecimal.valueOf(32.0)
                )),
                new AssetAggregationService.AggregationProperties("TWD", Duration.ofSeconds(2)),
                meterRegistry
        );
    }

    @Test
    @DisplayName("should mark missing source and return partial aggregation with zero amount")
    void aggregateAssets_missingSourceReturnsPartial() {
        TraceContext.ensureTraceId("missing-trace");

        assetSourceClient.bankResult = new BankAssetResult(
                "c-missing",
                Map.of("accounts", List.of(Map.of("accountId", "A-1", "balance", 400000, "currency", "TWD"))),
                BigDecimal.valueOf(400000),
                "TWD",
                List.of(new BankAssetWriter.BankAssetWriteRequest.CurrencyAmount("TWD", BigDecimal.valueOf(400000))),
                Instant.parse("2025-10-20T02:34:56Z"),
                "bank-trace"
        );

        assetSourceClient.insuranceResult = new InsuranceAssetResult(
                "c-missing",
                Map.of("policies", List.of(Map.of("policyNumber", "P-1", "coverage", 150000, "currency", "TWD"))),
                BigDecimal.valueOf(150000),
                "TWD",
                1,
                Instant.parse("2025-10-20T02:35:05Z"),
                "ins-trace"
        );

        AggregatedAssetResponse response = assetAggregationService.aggregateCustomerAssets("c-missing");

        assertThat(response.aggregationStatus()).isEqualTo(AggregatedAssetResponse.AggregationStatus.PARTIAL);
        assertThat(response.totalAssetValue()).isEqualByComparingTo(BigDecimal.valueOf(550000).setScale(2, RoundingMode.HALF_UP));

        assertThat(response.components())
                .extracting(AggregatedAssetResponse.Component::status)
                .containsExactlyInAnyOrder(AssetComponentStatus.SUCCESS, AssetComponentStatus.MISSING, AssetComponentStatus.SUCCESS);

        AggregatedAssetResponse.Component missingComponent = response.components().stream()
                .filter(component -> component.source() == AssetSourceType.SECURITIES)
                .findFirst()
                .orElseThrow();

        assertThat(missingComponent.amountInBase()).isEqualByComparingTo(BigDecimal.ZERO.setScale(2));
        assertThat(missingComponent.payloadRefId()).isNull();

        assertThat(response.currencyBreakdown()).containsExactly(
                new AggregatedAssetResponse.CurrencyAmount("TWD", BigDecimal.valueOf(550000).setScale(2, RoundingMode.HALF_UP))
        );

        assertThat(bankAssetRawRepository.count()).isEqualTo(1);
        assertThat(securitiesAssetRawRepository.count()).isZero();
        assertThat(insuranceAssetRawRepository.count()).isEqualTo(1);

        AssetStagingDocument staging = assetStagingRepository.findByCustomerId("c-missing").orElseThrow();
        assertThat(staging.aggregationStatus()).isEqualTo(AggregatedAssetResponse.AggregationStatus.PARTIAL.name());
        assertThat(staging.components())
                .extracting(AssetStagingDocument.Component::status)
                .containsExactlyInAnyOrder(AssetComponentStatus.SUCCESS.name(), AssetComponentStatus.MISSING.name(), AssetComponentStatus.SUCCESS.name());
    }

    private static class MissingSourceClient implements AssetSourceClient {

        private BankAssetResult bankResult;
        private InsuranceAssetResult insuranceResult;

        @Override
        public CompletableFuture<BankAssetResult> fetchBankAssets(String customerId, String traceId) {
            return CompletableFuture.completedFuture(bankResult);
        }

        @Override
        public CompletableFuture<SecuritiesAssetResult> fetchSecuritiesAssets(String customerId, String traceId) {
            return CompletableFuture.failedFuture(new AssetSourceMissingException("Securities data missing"));
        }

        @Override
        public CompletableFuture<InsuranceAssetResult> fetchInsuranceAssets(String customerId, String traceId) {
            return CompletableFuture.completedFuture(insuranceResult);
        }
    }

    private static class LocalStaticConversionService implements CurrencyConversionService {

        private final Map<String, BigDecimal> rates;

        private LocalStaticConversionService(Map<String, BigDecimal> rates) {
            this.rates = rates;
        }

        @Override
        public ConversionResult convert(BigDecimal amount, String fromCurrency, String toCurrency) {
            if (fromCurrency.equalsIgnoreCase(toCurrency)) {
                return new ConversionResult(amount, BigDecimal.ONE);
            }
            BigDecimal rate = rates.get(fromCurrency.toUpperCase() + ":" + toCurrency.toUpperCase());
            if (rate == null) {
                throw new IllegalArgumentException("Missing conversion rate");
            }
            return new ConversionResult(amount.multiply(rate), rate);
        }
    }
}
