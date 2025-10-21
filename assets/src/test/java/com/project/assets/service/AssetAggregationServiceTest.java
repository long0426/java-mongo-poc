package com.project.assets.service;

import com.project.assets.config.MongoWriteRetryProperties;
import com.project.assets.integration.AssetSourceClient;
import com.project.assets.integration.AssetSourceClient.BankAssetResult;
import com.project.assets.integration.AssetSourceClient.InsuranceAssetResult;
import com.project.assets.integration.AssetSourceClient.SecuritiesAssetResult;
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
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataMongoTest
@Testcontainers(disabledWithoutDocker = true)
class AssetAggregationServiceTest {

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

    private StubAssetSourceClient assetSourceClient;
    private StaticCurrencyConversionService currencyConversionService;
    private AssetAggregationService assetAggregationService;

    @BeforeEach
    void setUp() {
        bankAssetRawRepository.deleteAll();
        securitiesAssetRawRepository.deleteAll();
        insuranceAssetRawRepository.deleteAll();
        assetStagingRepository.deleteAll();

        assetSourceClient = new StubAssetSourceClient();
        currencyConversionService = new StaticCurrencyConversionService(Map.of(
                "USD:TWD", BigDecimal.valueOf(32.00)
        ));

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
                currencyConversionService,
                new AssetAggregationService.AggregationProperties("TWD", Duration.ofSeconds(3)),
                meterRegistry
        );
    }

    @Nested
    class SuccessfulAggregation {

        @Test
        @DisplayName("should aggregate assets from three sources and persist staging document")
        void aggregateAssets_successfulFlow() {
            TraceContext.ensureTraceId("agg-trace");

            assetSourceClient.bankResult = new BankAssetResult(
                    "c-123",
                    Map.of(
                            "accounts", List.of(
                                    Map.of("accountId", "A-001", "balance", 250000, "currency", "TWD"),
                                    Map.of("accountId", "A-002", "balance", 250000, "currency", "TWD")
                            )
                    ),
                    BigDecimal.valueOf(500000),
                    "TWD",
                    List.of(new BankAssetWriter.BankAssetWriteRequest.CurrencyAmount("TWD", BigDecimal.valueOf(500000))),
                    Instant.parse("2025-10-20T02:34:56Z"),
                    "bank-trace"
            );

            assetSourceClient.securitiesResult = new SecuritiesAssetResult(
                    "c-123",
                    Map.of(
                            "holdings", List.of(
                                    Map.of("symbol", "STK-1", "marketValue", 15000, "currency", "USD"),
                                    Map.of("symbol", "STK-2", "marketValue", 15000, "currency", "USD")
                            )
                    ),
                    BigDecimal.valueOf(30000),
                    "USD",
                    2,
                    Instant.parse("2025-10-20T02:35:01Z"),
                    "sec-trace"
            );

            assetSourceClient.insuranceResult = new InsuranceAssetResult(
                    "c-123",
                    Map.of(
                            "policies", List.of(
                                    Map.of("policyNumber", "P-001", "coverage", 450000, "currency", "TWD")
                            )
                    ),
                    BigDecimal.valueOf(450000),
                    "TWD",
                    1,
                    Instant.parse("2025-10-20T02:35:05Z"),
                    "ins-trace"
            );

            AggregatedAssetResponse response = assetAggregationService.aggregateCustomerAssets("c-123");

            assertThat(response.customerId()).isEqualTo("c-123");
            assertThat(response.baseCurrency()).isEqualTo("TWD");
            assertThat(response.aggregationStatus()).isEqualTo(AggregatedAssetResponse.AggregationStatus.COMPLETED);

            BigDecimal expectedTotal = BigDecimal.valueOf(500000)
                    .add(BigDecimal.valueOf(30000).multiply(BigDecimal.valueOf(32.00)))
                    .add(BigDecimal.valueOf(450000))
                    .setScale(2, RoundingMode.HALF_UP);
            assertThat(response.totalAssetValue()).isEqualByComparingTo(expectedTotal);

            assertThat(response.components()).hasSize(3);
            assertThat(response.components())
                    .extracting(AggregatedAssetResponse.Component::source)
                    .containsExactlyInAnyOrder(AssetSourceType.BANK, AssetSourceType.SECURITIES, AssetSourceType.INSURANCE);
            assertThat(response.components())
                    .allSatisfy(component -> assertThat(component.status()).isEqualTo(AssetComponentStatus.SUCCESS));

            assertThat(response.components())
                    .filteredOn(component -> component.source() == AssetSourceType.SECURITIES)
                    .singleElement()
                    .extracting(AggregatedAssetResponse.Component::amountInBase)
                    .asInstanceOf(InstanceOfAssertFactories.BIG_DECIMAL)
                    .isEqualByComparingTo(BigDecimal.valueOf(30000).multiply(BigDecimal.valueOf(32.00)));

            assertThat(response.currencyBreakdown()).containsExactlyInAnyOrder(
                    new AggregatedAssetResponse.CurrencyAmount("TWD", BigDecimal.valueOf(950000).setScale(2, RoundingMode.HALF_UP)),
                    new AggregatedAssetResponse.CurrencyAmount("USD", BigDecimal.valueOf(30000).setScale(2, RoundingMode.HALF_UP))
            );

            assertThat(bankAssetRawRepository.findAll()).hasSize(1);
            assertThat(securitiesAssetRawRepository.findAll()).hasSize(1);
            assertThat(insuranceAssetRawRepository.findAll()).hasSize(1);

            AssetStagingDocument staging = assetStagingRepository.findByCustomerId("c-123").orElseThrow();
            assertThat(staging.components()).hasSize(3);
            assertThat(staging.components())
                    .allSatisfy(component -> {
                        assertThat(component.status()).isEqualTo(AssetComponentStatus.SUCCESS.name());
                        assertThat(component.payloadRefId()).isNotBlank();
                    });
        }
    }

    @Nested
    class ErrorHandling {

        @Test
        @DisplayName("should throw AssetAggregationException when any source fails")
        void aggregateAssets_sourceFailure() {
            TraceContext.ensureTraceId("agg-trace");

            assetSourceClient.bankResult = new BankAssetResult(
                    "c-404",
                    Map.of(),
                    BigDecimal.ZERO,
                    "TWD",
                    List.of(),
                    Instant.parse("2025-10-20T00:00:00Z"),
                    "bank-trace"
            );
            assetSourceClient.securitiesFailure = new RuntimeException("securities unavailable");
            assetSourceClient.insuranceResult = new InsuranceAssetResult(
                    "c-404",
                    Map.of(),
                    BigDecimal.ZERO,
                    "TWD",
                    0,
                    Instant.parse("2025-10-20T00:00:00Z"),
                    "ins-trace"
            );

            assertThatThrownBy(() -> assetAggregationService.aggregateCustomerAssets("c-404"))
                    .isInstanceOf(AssetAggregationException.class)
                    .hasMessageContaining("securities")
                    .extracting("failedSources", InstanceOfAssertFactories.LIST)
                    .asList()
                    .containsExactly(AssetSourceType.SECURITIES);

            assertThat(assetStagingRepository.count()).isZero();
            assertThat(bankAssetRawRepository.count()).isEqualTo(1);
            assertThat(securitiesAssetRawRepository.count()).isZero();
            assertThat(insuranceAssetRawRepository.count()).isEqualTo(1);
        }
    }

    private static class StubAssetSourceClient implements AssetSourceClient {
        private BankAssetResult bankResult;
        private SecuritiesAssetResult securitiesResult;
        private InsuranceAssetResult insuranceResult;

        private RuntimeException bankFailure;
        private RuntimeException securitiesFailure;
        private RuntimeException insuranceFailure;

        @Override
        public CompletableFuture<BankAssetResult> fetchBankAssets(String customerId, String traceId) {
            if (bankFailure != null) {
                return CompletableFuture.failedFuture(bankFailure);
            }
            return CompletableFuture.completedFuture(bankResult);
        }

        @Override
        public CompletableFuture<SecuritiesAssetResult> fetchSecuritiesAssets(String customerId, String traceId) {
            if (securitiesFailure != null) {
                return CompletableFuture.failedFuture(securitiesFailure);
            }
            return CompletableFuture.completedFuture(securitiesResult);
        }

        @Override
        public CompletableFuture<InsuranceAssetResult> fetchInsuranceAssets(String customerId, String traceId) {
            if (insuranceFailure != null) {
                return CompletableFuture.failedFuture(insuranceFailure);
            }
            return CompletableFuture.completedFuture(insuranceResult);
        }
    }

    private static class StaticCurrencyConversionService implements CurrencyConversionService {

        private final Map<String, BigDecimal> rates;

        private StaticCurrencyConversionService(Map<String, BigDecimal> rates) {
            this.rates = rates;
        }

        @Override
        public ConversionResult convert(BigDecimal amount, String fromCurrency, String toCurrency) {
            if (fromCurrency.equalsIgnoreCase(toCurrency)) {
                return new ConversionResult(amount.setScale(2, RoundingMode.HALF_UP), BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP));
            }
            BigDecimal rate = rates.get(fromCurrency.toUpperCase() + ":" + toCurrency.toUpperCase());
            if (rate == null) {
                throw new IllegalArgumentException("Missing conversion rate for " + fromCurrency + " -> " + toCurrency);
            }
            BigDecimal converted = amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            return new ConversionResult(converted, rate.setScale(2, RoundingMode.HALF_UP));
        }
    }
}
